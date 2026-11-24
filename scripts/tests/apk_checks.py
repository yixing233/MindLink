"""Independently validate candidate ZIP, DEX definitions, launcher and original signing certificate."""

import argparse
import hashlib
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile
import zlib

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def descriptors(data):
    assert data[:4] == b"dex\x0a" and data[7] == 0, "DEX magic"
    assert len(data) == u32(data, 0x20) and u32(data, 0x24) == 0x70, "DEX size/header"
    assert u32(data, 0x28) == 0x12345678, "DEX endianness"
    assert hashlib.sha1(data[32:]).digest() == data[12:32], "DEX SHA-1"
    assert zlib.adler32(data[12:]) & 0xffffffff == u32(data, 8), "DEX Adler32"
    strings_count, strings_offset = u32(data, 0x38), u32(data, 0x3c)
    type_count, types_offset = u32(data, 0x40), u32(data, 0x44)
    class_count, classes_offset = u32(data, 0x60), u32(data, 0x64)
    assert strings_offset + strings_count * 4 <= len(data)
    assert types_offset + type_count * 4 <= len(data)
    assert classes_offset + class_count * 32 <= len(data)
    result = []
    for index in range(class_count):
        type_index = u32(data, classes_offset + index * 32)
        assert type_index < type_count
        string_index = u32(data, types_offset + type_index * 4)
        assert string_index < strings_count
        position = u32(data, strings_offset + string_index * 4)
        for _ in range(5):
            byte = data[position]
            position += 1
            if byte < 0x80:
                break
        else:
            raise AssertionError("bad string length")
        end = data.index(b"\0", position)
        result.append(data[position:end].decode("utf-8"))
    assert len(result) == len(set(result)), "duplicate definitions within DEX"
    return set(result)


def tool(command, output):
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True, encoding="utf-8", errors="replace")
    output.write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise AssertionError(f"tool failed ({result.returncode}); see {output}")
    return result.stdout + result.stderr


def asset_compression(info):
    assert info.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), "asset compression is not supported by the loader"


def inspect(apk):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), "duplicate ZIP entries"
        assert archive.testzip() is None, "bad ZIP CRC"
        dex_names = [name for name in names if name.endswith(".dex")]
        assert set(dex_names) == {"classes.dex", "classes2.dex"}, "exactly two root DEX entries"
        dex_classes = {}
        dex_info = {}
        for name in dex_names:
            info = archive.getinfo(name)
            assert info.compress_type == zipfile.ZIP_STORED, "DEX must be STORED"
            data = archive.read(name)
            dex_classes[name] = descriptors(data)
            dex_info[name] = {"size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "classes": len(dex_classes[name])}
        assert not dex_classes["classes.dex"] & dex_classes["classes2.dex"], "duplicate definitions across DEX"
        manifest = archive.read("AndroidManifest.xml")
        assert "assets/xposed_init" not in names, "legacy Xposed entry must not be packaged"
        assert archive.read("META-INF/xposed/java_init.list").decode("utf-8").split() == ["dev.mindlink.xposed.ModuleEntry"], "libxposed entry missing"
        assert archive.read("META-INF/xposed/scope.list").decode("utf-8").split() == ["com.tencent.mm"], "static scope missing"
        assert "staticScope=true" in archive.read("META-INF/xposed/module.prop").decode("utf-8").split(), "staticScope disabled"
        return dex_classes, dex_info, manifest, len(names)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--build-tools", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--app-label", default="WeLiquid", help="Expected application display name")
    parser.add_argument("--allow-primary-rebuild", action="store_true",
                        help="Allow primary DEX byte changes only when its class membership is unchanged")
    parser.add_argument("--allow-desktop-removal", action="store_true",
                        help="The standalone desktop activity was removed; verify the module manifest instead of byte identity")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    new_classes, info, manifest, entries = inspect(args.apk)
    old_classes, _, old_manifest, _ = inspect(args.baseline)
    if not args.allow_desktop_removal:
        assert manifest == old_manifest, "launcher, metadata and permission manifest changed unexpectedly"
    if not args.allow_primary_rebuild:
        with zipfile.ZipFile(args.apk) as new, zipfile.ZipFile(args.baseline) as old:
            assert new.read("classes.dex") == old.read("classes.dex"), "primary DEX bytes changed"
    assert new_classes["classes.dex"] == old_classes["classes.dex"], "primary DEX class membership changed"
    union = new_classes["classes.dex"] | new_classes["classes2.dex"]
    assert "Lwex/ui/core/MainActivity;" in new_classes["classes.dex"], "smali MainActivity absent"
    for name in ["AboutPageBuilder", "ModuleDashboardPage", "ModuleDashboardState", "NavigationFrostedController",
                 "ModuleAboutPage", "ModulePreferenceViews", "ModulePageState", "ModuleAboutBackdrop"]:
        assert f"Lwex/ui/core/feature/{name};" in new_classes["classes2.dex"], name + " absent"
    assert not any(name.startswith(("Landroid/", "Landroidx/", "Lde/robv/android/xposed/", "Lio/github/libxposed/", "Lcom/tencent/mm/"))
                   for name in new_classes["classes2.dex"]), "compile or host stubs packaged"
    assert not any("/tests/" in name or name.endswith("Checks;") for name in new_classes["classes2.dex"]), "test code packaged"
    with zipfile.ZipFile(args.apk) as archive:
        root = Path(__file__).resolve().parents[2]
        for source, asset in [("LICENSE", "LICENSE.txt"), ("README.md", "README.md"), ("THIRD-PARTY-NOTICES.md", "THIRD-PARTY-NOTICES.md")]:
            assert archive.read("assets/mindlink/" + asset) == (root / source).read_bytes(), "local notice differs from packaged source: " + source
    with zipfile.ZipFile(args.apk) as archive:
        if "assets/compose-payload/index.txt" in archive.namelist():
            payload_classes = set()
            payload_info = []
            for line in archive.read("assets/compose-payload/index.txt").decode().splitlines():
                name, digest = line.split(" ")
                assert re.fullmatch(r"classes[0-9]*\.bin", name)
                data = archive.read("assets/compose-payload/" + name)
                asset_compression(archive.getinfo("assets/compose-payload/" + name))
                assert hashlib.sha256(data).hexdigest() == digest, "payload SHA-256"
                classes = descriptors(data)
                assert not classes & payload_classes, "duplicate class within isolated payload"
                payload_classes.update(classes)
                payload_info.append((name, len(data), digest))
            for expected in ["Ldev/mindlink/compose/DesktopUi;", "Landroidx/compose/runtime/Composer;"]:
                assert expected in payload_classes, "actual UI dependency absent: " + expected
            assert "Lwex/ui/core/WexStore;" not in payload_classes, "host store leaked into UI runtime"
            assert "Lwex/ui/core/feature/ComposePayloadBridge;" in new_classes["classes2.dex"]
            print("PASS: isolated actual Material3/Compose payload", payload_info)
        if "assets/sidepanel-compose-payload/index.txt" in archive.namelist():
            sidebar_classes = set()
            sidebar_info = []
            indexed = set()
            for position, line in enumerate(archive.read("assets/sidepanel-compose-payload/index.txt").decode().splitlines(), 1):
                name, digest = line.split(" ")
                assert name == "classes" + (str(position) if position > 1 else "") + ".bin", "sidepanel DEX index order"
                path = "assets/sidepanel-compose-payload/" + name
                data = archive.read(path)
                assert path not in indexed and hashlib.sha256(data).hexdigest() == digest, "sidepanel payload index/hash"
                asset_compression(archive.getinfo(path))
                classes = descriptors(data)
                assert not sidebar_classes & classes, "sidepanel duplicate class"
                sidebar_classes.update(classes)
                indexed.add(path)
                sidebar_info.append((name, len(data), digest))
            assert {name for name in archive.namelist() if name.startswith("assets/sidepanel-compose-payload/")} == indexed | {"assets/sidepanel-compose-payload/index.txt"}
            for expected in ["Ldev/mindlink/compose/SidePanelUi;", "Ldev/mindlink/compose/SettingsUi;",
                             "Landroidx/compose/runtime/Composer;"]:
                assert expected in sidebar_classes, "original WeKit component/runtime absent: " + expected
            assert "Lwex/ui/core/WexStore;" not in sidebar_classes, "host store leaked into sidepanel"
            assert "Lwex/ui/core/feature/SidePanelComposeBridge;" in new_classes["classes2.dex"]
            assert "assets/mindlink/licenses/LUCIDE-ISC.txt" in archive.namelist(), "Lucide icon license absent"
            print("PASS: isolated original WeKit/Material3 sidepanel payload", sidebar_info)
    signer = [args.jdk / "bin/java.exe", "-jar", args.build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs"]
    old_signature = tool([*signer, args.baseline], args.output / "baseline-signature.txt")
    new_signature = tool([*signer, args.apk], args.output / "candidate-signature.txt")
    # apksigner prints "Signer #1 certificate SHA-256 digest:" in newer build-tools and
    # "V3.0 Signer: certificate SHA-256 digest:" in older ones; both name the same digest.
    certificate = lambda value: re.findall(
        r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-f]+)$",
        value, re.MULTILINE)
    assert certificate(old_signature) and certificate(old_signature) == certificate(new_signature), "original signing certificate changed"
    assert "Verified using v3 scheme (APK Signature Scheme v3): true" in new_signature, "v3 signature absent"
    tool([args.build_tools / "zipalign.exe", "-c", "-p", "4", args.apk], args.output / "zipalign.txt")
    badging = tool([args.build_tools / "aapt2.exe", "dump", "badging", args.apk], args.output / "badging.txt")
    assert "package: name='dev.mindlink'" in badging and f"application-label:'{args.app_label}'" in badging, "unexpected application identity"
    xml = tool([args.build_tools / "aapt2.exe", "dump", "xmltree", "--file", "AndroidManifest.xml", args.apk], args.output / "manifest-tree.txt")
    if args.allow_desktop_removal:
        assert "launchable-activity:" not in badging, "standalone desktop launcher still packaged"
        assert '"android.intent.category.LAUNCHER"' not in xml, "launcher filter still packaged"
        for removed in ["wex.ui.core.MainActivity", "wex.ui.core.FeatureSettingsActivity"]:
            assert removed not in xml, "desktop activity still declared: " + removed
        assert "com.tencent.mm" in xml, "xposed scope lost"
        for kept in ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                     "android.permission.INTERNET"]:
            assert kept in xml, "module permission lost: " + kept
        print("PASS: desktop activity removed, module manifest and permissions intact")
    else:
        assert badging.count("launchable-activity:") == 1 and "launchable-activity: name='wex.ui.core.MainActivity'" in badging, "wrong launcher"
        assert xml.count('"android.intent.category.LAUNCHER" (Raw: "android.intent.category.LAUNCHER")') == 1, "launcher filter not unique"
    assert '"android.permission.MANAGE_EXTERNAL_STORAGE"' not in xml, "broad storage permission added"
    data = args.apk.read_bytes()
    lines = ["PASS: candidate ZIP/DEX/checksums/class uniqueness/original certificate/v3/zipalign/launcher",
             f"APK size: {len(data)} bytes", f"APK SHA-256: {hashlib.sha256(data).hexdigest()}",
             f"ZIP entries: {entries}; defined classes: {len(union)}",
             f"Certificate SHA-256: {certificate(new_signature)[0]}"]
    for name, values in info.items():
        lines.append(f"{name}: {values['size']} bytes; {values['classes']} classes; SHA-256 {values['sha256']}")
    lines.append("Device cold start and native Frosted rendering have not been validated.")
    report = "\n".join(lines) + "\n"
    (args.output / "verification.txt").write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
