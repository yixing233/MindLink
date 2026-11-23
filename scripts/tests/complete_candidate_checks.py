"""Verify the completed two-payload candidate against its installed baseline and source freeze."""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("mindlink_complete_resource_check", ROOT / "scripts/check_compose_resources.py")
CHECK = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = CHECK
spec.loader.exec_module(CHECK)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def same_value(first, second):
    if first == second:
        return True
    # RGB8 and opaque ARGB8 encode the same Android color, without changing its value.
    if first[0] == second[0] == "value":
        old_type, old_data = first[1]
        new_type, new_data = second[1]
        return {old_type, new_type} == {0x1c, 0x1d} and old_data == new_data and old_data >> 24 == 0xff
    return False


def signature(name):
    return name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF", ".MF"))


def compressed_stream(archive, info):
    position = archive.fp.tell()
    try:
        archive.fp.seek(info.header_offset)
        header = archive.fp.read(30)
        require(len(header) == 30 and header[:4] == b"PK\x03\x04", "Invalid local ZIP header")
        flags, method = struct.unpack_from("<HH", header, 6)
        require(not flags & 1 and method == info.compress_type, "Local ZIP compression differs")
        name_size, extra_size = struct.unpack_from("<HH", header, 26)
        archive.fp.seek(name_size + extra_size, 1)
        data = archive.fp.read(info.compress_size)
        require(len(data) == info.compress_size, "Truncated ZIP compression stream")
        return data
    finally:
        archive.fp.seek(position)


def verify_preserved_desktop(old, new):
    prefix = "assets/compose-payload/"
    old_names = {name for name in old.namelist() if name.startswith(prefix)}
    new_names = {name for name in new.namelist() if name.startswith(prefix)}
    require(old_names and old_names == new_names, "Installed desktop payload membership changed")
    for name in sorted(old_names):
        before, after = old.getinfo(name), new.getinfo(name)
        require(old.read(name) == new.read(name), "Installed desktop payload bytes changed: " + name)
        require(before.compress_type == after.compress_type and before.compress_size == after.compress_size,
                "Installed desktop compression strategy changed: " + name)
        require(compressed_stream(old, before) == compressed_stream(new, after),
                "Installed desktop original compressed stream changed: " + name)


def verify_runtime_inventory(archive, sidepanel_sha256, baseline_sha256, preserved_desktop):
    inventory = json.loads(archive.read("assets/compose-runtime-inventory.json").decode("utf-8"))
    require(inventory["sidepanel_apk_sha256"] == sidepanel_sha256, "Runtime inventory sidepanel compilation identity differs")
    if "sidepanel_payload_sha256" in inventory:
        actual = {name[len("assets/sidepanel-compose-payload/"):]: hashlib.sha256(archive.read(name)).hexdigest()
                  for name in archive.namelist() if name.startswith("assets/sidepanel-compose-payload/") and name.endswith(".bin")}
        require(inventory["sidepanel_payload_sha256"] == actual, "Runtime inventory sidepanel DEX identities differ")
    if "desktop_identity" in inventory:
        identity = inventory["desktop_identity"]
        actual = {name[len("assets/compose-payload/"):]: hashlib.sha256(archive.read(name)).hexdigest()
                  for name in archive.namelist() if name.startswith("assets/compose-payload/") and name.endswith(".bin")}
        require(preserved_desktop and identity["installed_apk_sha256"] == baseline_sha256
                and identity["payload_sha256"] == actual, "Runtime inventory installed desktop identity differs")
    if "host_feature_sha256" in inventory:
        require(inventory["host_feature_sha256"] == hashlib.sha256(archive.read("classes2.dex")).hexdigest(), "Runtime inventory host feature identity differs")
    require(inventory["final_resources_sha256"] == hashlib.sha256(archive.read("resources.arsc")).hexdigest(), "Runtime inventory resource table identity differs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["apk", "baseline", "scoped-build", "desktop-apk", "sidepanel-apk", "regression-log", "output"]:
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--baseline-sha256", required=True)
    parser.add_argument("--preserved-desktop", action="store_true")
    args = parser.parse_args()
    require(CHECK.digest_file(args.baseline) == args.baseline_sha256, "Installed baseline SHA-256 differs")
    require(not args.output.exists(), "Complete verification report must be new")
    freeze = json.loads((args.scoped_build / "source-freeze.json").read_text(encoding="utf-8"))
    for path, expected in freeze["sources"].items():
        require(CHECK.digest_file(Path(path)) == expected, "Source changed after build: " + path)
    classes = json.loads((args.scoped_build / "strict-class-diff.json").read_text(encoding="utf-8"))
    abi = json.loads((args.scoped_build / "external-family-ABI-check.json").read_text(encoding="utf-8"))
    require(classes["unexpected_differences"] == [] and classes["patch_disassembly_differences"] == [], "Class preservation gate failed")
    require(not classes["non_target_encoding_exceptions_allowed"], "Non-target encoding exceptions were enabled")
    require(abi["missing_referenced_types_or_members"] == [] and abi["incompatible_access_or_static_changes"] == [], "ABI gate failed")
    regression = args.regression_log.read_text(encoding="utf-8", errors="replace")
    require("PASS: native arguments including empty, spaces, Chinese, quotes and trailing backslashes" in regression,
            "Complete regression did not reach its final gate")
    require("Traceback (most recent call last)" not in regression, "Complete regression has an uncaught failure")

    with CHECK.checked_zip(args.baseline) as old, CHECK.checked_zip(args.apk) as new:
        require(old.read("classes.dex") == new.read("classes.dex"), "Primary DEX bytes changed")
        require(old.read("AndroidManifest.xml") == new.read("AndroidManifest.xml"), "Manifest/launcher/permissions changed")
        require(new.read("classes2.dex") == (args.scoped_build / "feature/classes.dex").read_bytes(), "Verified scoped feature DEX differs in signed APK")
        changed, preserved = [], []
        for name in set(old.namelist()) | set(new.namelist()):
            if signature(name):
                continue
            if name in old.namelist() and name in new.namelist() and old.read(name) == new.read(name):
                preserved.append(name)
                continue
            # The Material Symbols wrapper license left with its icons (Lucide since 2026-11-22).
            require(name in new.namelist() or name == "assets/mindlink/licenses/COMPOSEICONS-MIT.txt",
                    "Unexpected removed baseline entry: " + name)
            allowed = (name == "classes2.dex" or name == "resources.arsc" or name.startswith("res/")
                       or name.startswith(("assets/compose-payload/", "assets/sidepanel-compose-payload/"))
                       or name in {"assets/compose-runtime-inventory.json", "assets/mindlink/THIRD-PARTY-NOTICES.md",
                                   "assets/mindlink/README.md", "assets/mindlink/APACHE-2.0.txt",
                                   "assets/mindlink/licenses/COMPOSEICONS-MIT.txt", "assets/mindlink/licenses/LUCIDE-ISC.txt"})
            require(allowed, "Unexpected changed entry outside the approved build scope: " + name)
            changed.append(name)
        original = CHECK.read_resource_table(old.read("resources.arsc"))
        final = CHECK.read_resource_table(new.read("resources.arsc"))
        equivalent_encodings = []
        for symbol, entry in original.entries.items():
            require(final.symbols.get(symbol) == entry.resource_id, "Installed resource ID changed: " + symbol)
            for config, value in entry.configs.items():
                actual = final.entries[symbol].configs.get(config)
                require(actual is not None and same_value(value, actual), "Installed resource value changed: " + symbol)
                if value != actual:
                    equivalent_encodings.append({"symbol": symbol, "configuration": config.hex(), "old": value, "new": actual})
        require(final.files <= set(new.namelist()), "Linked resource file absent from signed APK")
        require({name for name in new.namelist() if re.fullmatch(r"classes\d*\.dex", name)} == {"classes.dex", "classes2.dex"}, "Root DEX layout changed")
        for prefix, ui_path in [("compose-payload", args.desktop_apk), ("sidepanel-compose-payload", args.sidepanel_apk)]:
            with CHECK.checked_zip(ui_path) as ui:
                lines = new.read("assets/" + prefix + "/index.txt").decode("ascii").splitlines()
                for position, line in enumerate(lines, 1):
                    name, expected = line.split(" ")
                    dex_name = "classes" + (str(position) if position > 1 else "") + ".dex"
                    require(name == dex_name[:-4] + ".bin", "Payload index ordering differs")
                    data = new.read("assets/" + prefix + "/" + name)
                    require(data == ui.read(dex_name) and hashlib.sha256(data).hexdigest() == expected,
                            "Signed payload differs from verified actual UI compilation")
        if args.preserved_desktop:
            verify_preserved_desktop(old, new)
        verify_runtime_inventory(new, CHECK.digest_file(args.sidepanel_apk), args.baseline_sha256, args.preserved_desktop)
        result = {"apk": str(args.apk), "apk_sha256": CHECK.digest_file(args.apk), "bytes": args.apk.stat().st_size,
                  "baseline_sha256": args.baseline_sha256, "primary_bytes_unchanged": True,
                  "non_target_feature_classes_byte_exact": classes["non_target_byte_exact_count"],
                  "all_installed_resource_ids_and_values_preserved": True,
                  "original_resource_symbols": len(original.entries), "final_resource_symbols": len(final.entries),
                  "equivalent_color_encodings": equivalent_encodings, "changed_entries": sorted(changed),
                  "preserved_entries": sorted(preserved), "source_freeze_sha256": CHECK.digest_file(args.scoped_build / "source-freeze.json"),
                  "regression_log_sha256": CHECK.digest_file(args.regression_log),
                  "desktop_payload_byte_exact": args.preserved_desktop,
                  "desktop_original_compressed_streams_preserved": args.preserved_desktop,
                  "desktop_lint_vital": "not rerun: installed desktop payload preserved" if args.preserved_desktop else "passed",
                  "sidepanel_lint_vital": "not executed: explicit offline candidate",
                  "device_installed": False, "device_acceptance": "pending"}
    CHECK.write_report(args.output, result)
    print("PASS: complete signed candidate matches scoped DEX and both actual UI payloads; primary, non-target classes and installed resource semantics preserved")
    print(f"APK: {result['bytes']} bytes; SHA-256 {result['apk_sha256']}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        raise SystemExit(1)
