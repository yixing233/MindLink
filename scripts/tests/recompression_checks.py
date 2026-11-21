"""Exercise recompression failures and byte-exact desktop compression preservation."""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def empty_dex():
    data = bytearray(0x70)
    data[:8] = b"dex\n039\0"
    struct.pack_into("<III", data, 0x20, len(data), 0x70, 0x12345678)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)


def fixture(path, packager, bad_hash=False):
    dex = empty_dex()
    digest = hashlib.sha256(dex).hexdigest()
    with zipfile.ZipFile(path, "x") as archive:
        archive.comment = b"fixture comment"
        for name in ("classes.dex", "classes2.dex"):
            archive.writestr(name, dex, compress_type=zipfile.ZIP_STORED)
        archive.writestr("AndroidManifest.xml", b"fixture manifest", compress_type=zipfile.ZIP_DEFLATED)
        archive.writestr("assets/mindlink/THIRD-PARTY-NOTICES.md", b"fixture attribution" * 80,
                         compress_type=zipfile.ZIP_DEFLATED, compresslevel=1)
        archive.writestr("META-INF/FIXTURE.SF", b"old signature")
        for prefix in (packager.DESKTOP, packager.SIDEPANEL):
            archive.writestr(prefix + "classes.bin", dex,
                             compress_type=zipfile.ZIP_DEFLATED if prefix == packager.DESKTOP else zipfile.ZIP_STORED)
            archive.writestr(prefix + "index.txt", "classes.bin " + ("0" * 64 if bad_hash and prefix == packager.SIDEPANEL else digest) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--helper", type=Path)
    args = parser.parse_args()
    packager = load("mindlink_recompression_fixture", "scripts/recompress_checked_candidate.py")
    complete = load("mindlink_desktop_stream_fixture", "scripts/tests/complete_candidate_checks.py")
    apk = load("mindlink_asset_compression_fixture", "scripts/tests/apk_checks.py")
    checks = 0

    def check(condition):
        nonlocal checks
        assert condition
        checks += 1

    def rejects(action, message):
        nonlocal checks
        try:
            action()
        except (ValueError, AssertionError) as error:
            assert message in str(error), str(error)
            checks += 1
        else:
            raise AssertionError("Expected failure: " + message)

    for compression in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
        info = zipfile.ZipInfo("asset.bin")
        info.compress_type = compression
        apk.asset_compression(info)
        check(True)
    info.compress_type = zipfile.ZIP_BZIP2
    rejects(lambda: apk.asset_compression(info), "not supported")

    if args.helper is None:
        args.helper = ROOT / "build/full-ui-lifecycle-fix-20261114-111241-832dc7ff/scripts/hotfix.py"
    helper = packager.load_helper(args.helper)
    with tempfile.TemporaryDirectory(prefix="mindlink-recompression-") as directory:
        work = Path(directory)
        candidate = work / "complete.apk"
        fixture(candidate, packager)
        digest = packager.CHECK.digest_file(candidate)
        output, report = work / "compressed.apk", work / "report.json"
        options = SimpleNamespace(candidate=candidate, candidate_sha256=digest, helper=args.helper, output=output, report=report)
        packager.recompress(options)
        check(packager.CHECK.digest_file(candidate) == digest)
        check(output.is_file() and report.is_file())
        with packager.CHECK.checked_zip(candidate) as old, packager.CHECK.checked_zip(output) as new:
            entries, changed = packager.verify_recompression(old, new, packager.payload_entries(old), helper)
            check(changed == [packager.SIDEPANEL + "classes.bin"])
            check(all(entry["content_byte_exact"] for entry in entries.values()))
            check(all(entry["compressed_stream_byte_exact"] for name, entry in entries.items()
                      if not name.startswith(packager.SIDEPANEL + "classes")))
            complete.verify_preserved_desktop(old, new)
            check(True)
            check("META-INF/FIXTURE.SF" not in new.namelist())
        rejects(lambda: packager.recompress(options), "outputs must be new")
        check(packager.CHECK.digest_file(candidate) == digest)

        def options_for(name, **changes):
            values = vars(options).copy()
            values.update(output=work / (name + ".apk"), report=work / (name + ".json"))
            values.update(changes)
            return SimpleNamespace(**values)

        bad_identity = options_for("identity", candidate_sha256="0" * 64)
        rejects(lambda: packager.recompress(bad_identity), "identity differs")
        check(not bad_identity.output.exists() and not bad_identity.report.exists())
        bad_helper = work / "unverified-helper.py"
        bad_helper.write_text("raise AssertionError('must never execute')\n", encoding="utf-8")
        rejects(lambda: packager.recompress(options_for("helper", helper=bad_helper)), "helper identity differs")
        rejects(lambda: packager.recompress(options_for("input", output=candidate)), "overwrite an input")
        rejects(lambda: packager.recompress(options_for("aliased", report=work / "aliased.apk")), "another output")
        existing_report = options_for("report-existing")
        report_partial = existing_report.report.with_name(existing_report.report.name + ".partial")
        report_partial.write_bytes(b"preserve report sentinel")
        rejects(lambda: packager.recompress(existing_report), "outputs must be new")
        check(not existing_report.output.exists() and report_partial.read_bytes() == b"preserve report sentinel")
        existing = options_for("existing")
        partial = existing.output.with_name(existing.output.name + ".partial")
        partial.write_bytes(b"preserve sentinel")
        rejects(lambda: packager.recompress(existing), "outputs must be new")
        check(partial.read_bytes() == b"preserve sentinel")

        invalid = work / "invalid.apk"
        fixture(invalid, packager, bad_hash=True)
        invalid_options = options_for("invalid-output", candidate=invalid, candidate_sha256=packager.CHECK.digest_file(invalid))
        rejects(lambda: packager.recompress(invalid_options), "index/hash differs")
        check(not invalid_options.output.exists() and not invalid_options.report.exists())

        modified = work / "modified.apk"
        with zipfile.ZipFile(output) as src, zipfile.ZipFile(modified, "x") as dst:
            dst.comment = src.comment
            for item in src.infolist():
                if item.filename == "assets/mindlink/THIRD-PARTY-NOTICES.md":
                    dst.writestr(item.filename, b"different notice", compress_type=item.compress_type)
                else:
                    helper.copy_raw_entry(src, dst, item)
        with packager.CHECK.checked_zip(candidate) as old, packager.CHECK.checked_zip(modified) as new:
            rejects(lambda: packager.verify_recompression(old, new, packager.payload_entries(old), helper), "decompressed content")

        expanded = work / "expanded-desktop.apk"
        with zipfile.ZipFile(output) as src, zipfile.ZipFile(expanded, "x") as dst:
            dst.comment = src.comment
            for item in src.infolist():
                if item.filename == packager.DESKTOP + "classes.bin":
                    dst.writestr(item.filename, src.read(item), compress_type=zipfile.ZIP_STORED)
                else:
                    helper.copy_raw_entry(src, dst, item)
        with packager.CHECK.checked_zip(candidate) as old, packager.CHECK.checked_zip(expanded) as new:
            rejects(lambda: complete.verify_preserved_desktop(old, new), "compression strategy")
            rejects(lambda: packager.verify_recompression(old, new, packager.payload_entries(old), helper), "Non-target compression stream")

        reencoded = work / "reencoded-desktop.apk"
        with zipfile.ZipFile(output) as src, zipfile.ZipFile(reencoded, "x") as dst:
            dst.comment = src.comment
            for item in src.infolist():
                if item.filename == packager.DESKTOP + "classes.bin":
                    dst.writestr(item.filename, src.read(item), compress_type=zipfile.ZIP_DEFLATED, compresslevel=0)
                else:
                    helper.copy_raw_entry(src, dst, item)
        with packager.CHECK.checked_zip(candidate) as old, packager.CHECK.checked_zip(reencoded) as new:
            rejects(lambda: complete.verify_preserved_desktop(old, new), "compression strategy")
            rejects(lambda: packager.verify_recompression(old, new, packager.payload_entries(old), helper), "Non-target compression stream")

        raw_changed = work / "same-length-desktop.apk"
        raw_changed.write_bytes(output.read_bytes())
        with packager.CHECK.checked_zip(raw_changed) as archive:
            desktop = archive.getinfo(packager.DESKTOP + "classes.bin")
            stream = complete.compressed_stream(archive, desktop)
            original = archive.read(desktop)
            archive.fp.seek(desktop.header_offset + 26)
            name_size, extra_size = struct.unpack("<HH", archive.fp.read(4))
            stream_offset = desktop.header_offset + 30 + name_size + extra_size
        altered = None
        for bit in (0x80, 0x40, 0x20, 0x10, 8, 4, 2, 1):
            trial = stream[:-1] + bytes((stream[-1] ^ bit,))
            try:
                if zlib.decompress(trial, -15) == original:
                    altered = trial
                    break
            except zlib.error:
                continue
        check(altered is not None and len(altered) == len(stream) and altered != stream)
        with raw_changed.open("r+b") as file:
            file.seek(stream_offset)
            file.write(altered)
        with packager.CHECK.checked_zip(candidate) as old, packager.CHECK.checked_zip(raw_changed) as new:
            check(old.read(packager.DESKTOP + "classes.bin") == new.read(packager.DESKTOP + "classes.bin"))
            check(old.getinfo(packager.DESKTOP + "classes.bin").compress_size == new.getinfo(packager.DESKTOP + "classes.bin").compress_size)
            rejects(lambda: complete.verify_preserved_desktop(old, new), "original compressed stream changed")
            rejects(lambda: packager.verify_recompression(old, new, packager.payload_entries(old), helper), "Non-target compression stream")

        inventory_apk = work / "inventory.apk"
        resources = b"fixture resource table"
        dex_hash = hashlib.sha256(empty_dex()).hexdigest()
        identity = {"source": "installed-desktop-preserved", "installed_apk_sha256": digest, "payload_sha256": {"classes.bin": dex_hash}}
        inventory = {"sidepanel_apk_sha256": "a" * 64, "sidepanel_payload_sha256": {"classes.bin": dex_hash},
                     "desktop_identity": identity, "host_feature_sha256": dex_hash,
                     "final_resources_sha256": hashlib.sha256(resources).hexdigest()}

        def inventory_fixture(path, values):
            with zipfile.ZipFile(output) as src, zipfile.ZipFile(path, "x") as dst:
                dst.comment = src.comment
                for info in src.infolist():
                    helper.copy_raw_entry(src, dst, info)
                dst.writestr("resources.arsc", resources)
                dst.writestr("assets/compose-runtime-inventory.json", json.dumps(values))

        inventory_fixture(inventory_apk, inventory)
        with packager.CHECK.checked_zip(inventory_apk) as archive:
            complete.verify_runtime_inventory(archive, "a" * 64, digest, True)
            check(True)
            rejects(lambda: complete.verify_runtime_inventory(archive, "b" * 64, digest, True), "sidepanel compilation identity")
            rejects(lambda: complete.verify_runtime_inventory(archive, "a" * 64, "c" * 64, True), "installed desktop identity")
            rejects(lambda: complete.verify_runtime_inventory(archive, "a" * 64, digest, False), "installed desktop identity")
        for index, (key, bad, message) in enumerate([
            ("sidepanel_payload_sha256", {"classes.bin": "0" * 64}, "sidepanel DEX identities"),
            ("host_feature_sha256", "0" * 64, "host feature identity"),
            ("final_resources_sha256", "0" * 64, "resource table identity"),
            ("desktop_identity", dict(identity, payload_sha256={"classes.bin": "0" * 64}), "installed desktop identity"),
        ]):
            invalid_inventory = work / ("bad-inventory-" + str(index) + ".apk")
            inventory_fixture(invalid_inventory, dict(inventory, **{key: bad}))
            with packager.CHECK.checked_zip(invalid_inventory) as archive:
                rejects(lambda: complete.verify_runtime_inventory(archive, "a" * 64, digest, True), message)

    print(f"PASS: recompression identity, failure safety, loader compression and original desktop streams ({checks} assertions)")


if __name__ == "__main__":
    main()
