"""Verify a signed host-effects candidate against its actual installed baseline.

Notices, UI payloads and all other assets intentionally follow the installed APK,
not the deferred full-UI source tree. Re-disassemble the signed feature DEX and
check the exact ten-family mixture, platform ABI, certificate and compression.
"""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(__file__).resolve().parents[2]
FAMILIES = tuple("wex/ui/core/feature/" + name for name in (
    "FloatingChatFooterFeature", "FloatingFooterLayout", "NavigationBarViews", "ViewBackdropSampler",
    "FeatureBootstrap", "HostDatabaseBridge", "SelfProfile", "NavigationBottomSpace", "HostProfileBridge", "HostStageLog"))
BASELINE_SHA256 = "1de1e72fa291374b51e6cfc1a80a5e4960f17a135dccf6c1ed3893db42186f43"
ACCEPTED_SHA256 = "d6337ea229e1ad73fed7958714db774f7dbb39d4fb2c28fe1a13d41155625d6d"
HELPER_SHA256 = "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e"
CERTIFICATE_SHA256 = "c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def target(descriptor):
    return any(descriptor == "L" + family + ";" or descriptor.startswith("L" + family + "$") for family in FAMILIES)


def compressed_bytes(archive, name):
    info = archive.getinfo(name)
    archive.fp.seek(info.header_offset)
    header = archive.fp.read(30)
    assert len(header) == 30 and header[:4] == b"PK\x03\x04", "invalid ZIP local header"
    name_size, extra_size = struct.unpack_from("<HH", header, 26)
    archive.fp.seek(name_size + extra_size, 1)
    data = archive.fp.read(info.compress_size)
    assert len(data) == info.compress_size, "truncated compressed member"
    return data


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("apk", "baseline", "generation", "accepted", "preparation", "jdk", "build-tools", "android-jar", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), "verification output must be new"
    args.output.mkdir(parents=True)
    apk_checks = load(ROOT / "scripts/tests/apk_checks.py", "mindlink_effects_apk_checks")
    helper_path = args.preparation / "scripts/hotfix.py"
    assert sha(helper_path.read_bytes()) == HELPER_SHA256, "verified ABI helper changed"
    h = load(helper_path, "mindlink_effects_check_abi")
    h.FAMILIES, h.family_desc = FAMILIES, target
    assert sha(args.baseline.read_bytes()) == BASELINE_SHA256, "current baseline differs"
    assert sha((args.accepted / "feature/classes.dex").read_bytes()) == ACCEPTED_SHA256, "accepted effect source DEX differs"
    new_classes, dex_info, manifest, entry_count = apk_checks.inspect(args.apk)
    old_classes, _, old_manifest, _ = apk_checks.inspect(args.baseline)
    assert manifest == old_manifest, "manifest/launcher permissions changed"
    assert new_classes["classes.dex"] == old_classes["classes.dex"], "primary definitions changed"
    assert not any("/tests/" in name or name.endswith("Checks;") for name in new_classes["classes2.dex"]), "test code packaged"
    assert not any(name.startswith(("Landroid/", "Landroidx/", "Lde/robv/android/xposed/", "Lcom/tencent/mm/")) for name in new_classes["classes2.dex"]), "compile/runtime stub packaged"
    changed, raw_changed = [], []
    with zipfile.ZipFile(args.baseline) as before, zipfile.ZipFile(args.apk) as after:
        old_names = {name for name in before.namelist() if not h.signature_entry(name)}
        new_names = {name for name in after.namelist() if not h.signature_entry(name)}
        assert old_names == new_names, "non-signature entries changed"
        for name in sorted(old_names):
            old_info, new_info = before.getinfo(name), after.getinfo(name)
            assert old_info.compress_type == new_info.compress_type, "compression changed: " + name
            if before.read(name) != after.read(name):
                changed.append(name)
            if compressed_bytes(before, name) != compressed_bytes(after, name):
                raw_changed.append(name)
        assert changed == ["classes2.dex"] == raw_changed, "non-effect APK content or compressed payload changed"
        assert after.read("classes2.dex") == (args.generation / "feature/classes.dex").read_bytes(), "signed feature differs from checked mixture"
        assert after.read("assets/xposed_init").strip() == b"wex.ui.core.xposed.HookEntry", "Xposed entry changed"
        assert not any(name.startswith("assets/sidepanel-compose-payload/") for name in after.namelist()), "deferred sidebar runtime was added"
        for source, archive in (("current", before), ("candidate", after)):
            dest = args.output / "inputs" / source
            dest.mkdir(parents=True)
            (dest / "classes2.dex").write_bytes(archive.read("classes2.dex"))
        payload_classes, indexed = set(), []
        for position, line in enumerate(after.read("assets/compose-payload/index.txt").decode("ascii").splitlines(), 1):
            name, expected = line.split(" ")
            assert name == "classes" + (str(position) if position > 1 else "") + ".bin", "desktop DEX order changed"
            path = "assets/compose-payload/" + name
            data = after.read(path)
            assert sha(data) == expected, "desktop hash mismatch"
            definitions = apk_checks.descriptors(data)
            assert not payload_classes & definitions, "duplicate desktop payload class"
            payload_classes.update(definitions)
            indexed.append(path)
        assert set(indexed) | {"assets/compose-payload/index.txt"} == {name for name in after.namelist() if name.startswith("assets/compose-payload/")}, "desktop payload entry set changed"
        for expected in ("Ldev/mindlink/compose/DesktopUi;", "Landroidx/compose/runtime/Composer;", "Ltop/yukonga/miuix/kmp/theme/MiuixThemeKt;"):
            assert expected in payload_classes, "real desktop dependency missing: " + expected
        assert "Lwex/ui/core/WexStore;" not in payload_classes, "host store leaked into isolated payload"
    print("PASS: only feature DEX changed; installed desktop/resources/notices/JNI compressed streams exact", flush=True)

    java = args.jdk / "bin/java.exe"
    cp = ";".join(map(str, [args.preparation / "tool-classes/disassembler", *h.BAKJARS]))
    for source in ("current", "candidate"):
        apk_checks.tool([java, "-cp", cp, "DisassembleTool", args.output / "inputs" / source / "classes2.dex", args.output / (source + "-smali")], args.output / (source + "-disassemble.txt"))
    old = h.smali_tree(args.output / "current-smali")
    candidate = h.smali_tree(args.output / "candidate-smali")
    accepted = h.smali_tree(args.accepted / "candidate-smali")
    accepted_manifest = json.loads((args.accepted / "strict-class-diff.json").read_text(encoding="utf-8"))["candidate_smali_sha256"]
    assert {name: sha(data) for name, (_, data) in accepted.items()} == accepted_manifest, "accepted smali changed"
    patch = {name: item for name, item in accepted.items() if target(name)}
    assert set(candidate) == {name for name in old if not target(name)} | set(patch), "candidate contains another family change"
    assert set(candidate) == new_classes["classes2.dex"] and set(old) == old_classes["classes2.dex"], "disassembly membership mismatch"
    assert all("L" + family + ";" in candidate for family in FAMILIES), "required effect root missing"
    exact = sum(not target(name) for name in old)
    assert all(old[name][1] == candidate[name][1] for name in old if not target(name)), "non-target smali differs"
    normalize = lambda data: re.sub(rb"(?m)^(\s*)const-string/jumbo ", rb"\1const-string ", data)
    assert all(normalize(data) == normalize(candidate[name][1]) for name, (_, data) in patch.items()), "effect class differs from accepted implementation"
    primary = h.smali_tree(args.preparation / "primary-smali")
    expected_primary = json.loads((args.preparation / "primary-smali-hashes.json").read_text(encoding="utf-8"))
    assert {name: sha(data) for name, (_, data) in primary.items()} == expected_primary, "primary smali snapshot changed"
    assert set(primary) == new_classes["classes.dex"], "primary snapshot membership mismatch"
    h.WORK, h.PREP, h.ANDROID = args.output, args.generation, args.android_jar
    h.verify_abi(old, candidate, primary)
    abi = json.loads((args.output / "external-family-ABI-check.json").read_text(encoding="utf-8"))
    print(f"PASS: signed candidate ten-family mixture; {exact} non-target classes exact; ABI missing/incompatible zero", flush=True)

    signer = [java, "-jar", args.build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs"]
    signatures = [apk_checks.tool([*signer, apk], args.output / (label + "-signature.txt")) for label, apk in (("baseline", args.baseline), ("candidate", args.apk))]
    cert = lambda text: re.findall(r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer): certificate SHA-256 digest: ([0-9a-f]+)$", text, re.M)
    assert cert(signatures[0]) == cert(signatures[1]) == [CERTIFICATE_SHA256], "original certificate changed"
    assert "Verified using v3 scheme (APK Signature Scheme v3): true" in signatures[1], "V3 signature missing"
    apk_checks.tool([args.build_tools / "zipalign.exe", "-c", "-p", "4", args.apk], args.output / "zipalign.txt")
    badging = apk_checks.tool([args.build_tools / "aapt2.exe", "dump", "badging", args.apk], args.output / "badging.txt")
    assert "package: name='dev.mindlink'" in badging and "application-label:'MindLink'" in badging, "package identity changed"
    assert badging.count("launchable-activity:") == 1 and "launchable-activity: name='wex.ui.core.MainActivity'" in badging, "launcher changed"
    freeze = json.loads((args.generation / "source-freeze.json").read_text(encoding="utf-8"))
    for path, expected in freeze["sources"].items():
        assert sha(Path(path).read_bytes()) == expected, "effect source changed after freeze"
    report = {"apk": str(args.apk), "sha256": sha(args.apk.read_bytes()), "bytes": args.apk.stat().st_size,
              "size_delta_bytes": args.apk.stat().st_size - args.baseline.stat().st_size, "changed_non_signature_entries": changed,
              "all_other_entries_and_compressed_streams_byte_exact": True, "non_target_class_count": exact,
              "selected_effect_class_count": len(patch), "dex": dex_info, "zip_entry_count": entry_count,
              "abi_incoming": abi["incoming_reference_count"], "abi_outgoing": abi["outgoing_reference_count"],
              "abi_missing": 0, "abi_incompatible": 0, "original_certificate_sha256": CERTIFICATE_SHA256,
              "v3_verified": True, "zipalign_verified": True, "device_acceptance_complete": False,
              "not_verified_on_device": ["actual_hook_loading", "persistent_background_blur", "bottom_bar_geometry", "natural_composer_IME_height", "real_profile_status_capture"]}
    with (args.output / "verification.json").open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("PASS: original certificate/V3/zipalign/launcher and independently inspected signed APK", flush=True)
    print(f"APK: {report['bytes']} bytes; SHA-256 {report['sha256']}", flush=True)
    print("Device loading, real GPU blur, IME layout and profile/status reads remain unverified.", flush=True)


if __name__ == "__main__":
    main()
