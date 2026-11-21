"""Build a host-effects-only candidate without changing installed UI or resources.

Select ten hash-verified effect families from the accepted local build, resolve
its ABI against the installed host, and replace only classes2.dex. No UI build,
installation, device restart or production-source rewrite is performed.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
PREFIX = "wex/ui/core/feature/"
FAMILIES = tuple(PREFIX + name for name in (
    "FloatingChatFooterFeature", "FloatingFooterLayout", "NavigationBarViews", "ViewBackdropSampler",
    "FeatureBootstrap", "HostDatabaseBridge", "SelfProfile", "NavigationBottomSpace", "HostProfileBridge", "HostStageLog"))
CURRENT_SHA256 = "1de1e72fa291374b51e6cfc1a80a5e4960f17a135dccf6c1ed3893db42186f43"
PRIMARY_SHA256 = "a2932d2d945b936be09a2623951fd6648a68d823514944325bfbd95350ca4345"
FEATURE_BASELINE_SHA256 = "f0fcb7381b70bbef2b0900dc386dcffa3bddcac2d15c3dfd7865abe29dc52145"
ACCEPTED_DEX_SHA256 = "d6337ea229e1ad73fed7958714db774f7dbb39d4fb2c28fe1a13d41155625d6d"
ACCEPTED_FREEZE_SHA256 = "e2bd517f2868f41ec4a46c9db72465c0bbf8e6e0079a02fbf7f0d144fd944ff2"
CLASSPATH_SHA256 = "013fb032063c01115e620a41a8e22e3cdc9639c4ddd47ebadbe6b4281fe14e66"
HELPER_SHA256 = "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e"
CERTIFICATE_SHA256 = "c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda"
TOOL_CLASSES = {
    "assembler/AssembleTool.class": "6a10b777ef3374a3e73f8cc414daa205753cccf24fbc98a05169c9e7e78dc16d",
    "disassembler/DisassembleTool.class": "16cfd8df850b877e88d28d51028f79989e552a963ffb09d89ff117394b114314",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def target(descriptor):
    return descriptor.startswith("L") and descriptor.endswith(";") and any(
        descriptor[1:-1] == family or descriptor[1:-1].startswith(family + "$") for family in FAMILIES)


def command(work, label, values, env=None):
    result = subprocess.run(list(map(str, values)), cwd=ROOT, env=env, capture_output=True,
                            encoding="utf-8", errors="replace")
    output = result.stdout + result.stderr
    if env and env.get("MINDLINK_EFFECTS_SIGN_PASSWORD"):
        output = output.replace(env["MINDLINK_EFFECTS_SIGN_PASSWORD"], "[REDACTED]")
    with (work / "logs" / (label + ".txt")).open("x", encoding="utf-8") as stream:
        stream.write(output)
    require(result.returncode == 0, f"{label} failed ({result.returncode}); see isolated logs")
    print("PASS: " + label, flush=True)
    return output


def certificate(output):
    return re.findall(r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer): certificate SHA-256 digest: ([0-9a-f]+)$", output, re.M)


def build(args):
    require(args.baseline_sha256 == CURRENT_SHA256, "Effects build requires the checked current installation identity")
    require(digest(args.baseline) == CURRENT_SHA256, "Current installed APK snapshot changed")
    require(digest(args.accepted / "feature/classes.dex") == ACCEPTED_DEX_SHA256, "Accepted feature DEX changed")
    require(digest(args.accepted / "source-freeze.json") == ACCEPTED_FREEZE_SHA256, "Accepted source freeze changed")
    require(digest(args.accepted / "inputs/classpath/classes.jar") == CLASSPATH_SHA256, "Accepted ABI classpath changed")
    helper_path = args.preparation / "scripts/hotfix.py"
    require(digest(helper_path) == HELPER_SHA256, "Verified DEX/ABI helper changed")
    require(not args.output.exists(), "Effects build output must be a new directory")
    for previous in (args.preparation, args.accepted, args.baseline.parent):
        require(args.output.resolve() != previous.resolve() and previous.resolve() not in args.output.resolve().parents,
                "Effects build must not overwrite or nest inside an input directory")

    spec = importlib.util.spec_from_file_location("mindlink_effects_verified_helper", helper_path)
    h = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(h)
    h.FAMILIES, h.family_desc = FAMILIES, target
    previous = json.loads((args.preparation / "input-hashes.json").read_text(encoding="utf-8"))
    for path, expected in previous["tool_sha256"].items():
        require(digest(path) == expected, "Verified DEX/signing tool changed: " + path)
    for relative, expected in TOOL_CLASSES.items():
        require(digest(args.preparation / "tool-classes" / relative) == expected, "Verified tool bytecode changed: " + relative)
    require(digest(args.android_jar) == previous["android_jar_sha256"], "Android ABI platform changed")
    for relative, expected in previous["stub_class_sha256"].items():
        require(digest(args.accepted / "inputs/classpath/stub" / relative) == expected, "Verified ABI stub changed: " + relative)

    primary = h.smali_tree(args.preparation / "primary-smali")
    expected_primary = json.loads((args.preparation / "primary-smali-hashes.json").read_text(encoding="utf-8"))
    require({desc: sha(data) for desc, (_, data) in primary.items()} == expected_primary, "Verified primary smali changed")
    with zipfile.ZipFile(args.baseline) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())) and archive.testzip() is None, "Baseline ZIP invalid")
        require({name for name in archive.namelist() if name.endswith(".dex")} == {"classes.dex", "classes2.dex"}, "Baseline root DEX differs")
        primary_bytes, old_bytes = archive.read("classes.dex"), archive.read("classes2.dex")
        require(sha(primary_bytes) == PRIMARY_SHA256 and sha(old_bytes) == FEATURE_BASELINE_SHA256, "Current host baseline differs")
        require(h.dex_classes(primary_bytes) == set(primary), "Primary smali does not identify the installed DEX")
        require(archive.getinfo("classes2.dex").compress_type == zipfile.ZIP_STORED, "Baseline feature DEX compression differs")
        require(not any(name.startswith("assets/sidepanel-compose-payload/") for name in archive.namelist()), "Current baseline is not the checked native-sidebar package")

    work = args.output.resolve()
    for relative in ("logs", "inputs/current", "inputs/accepted", "inputs/classpath", "inputs/frozen-src", "feature"):
        (work / relative).mkdir(parents=True, exist_ok=False)
    current_dex = work / "inputs/current/classes2.dex"
    accepted_dex = work / "inputs/accepted/classes2.dex"
    current_dex.write_bytes(old_bytes)
    accepted_dex.write_bytes((args.accepted / "feature/classes.dex").read_bytes())
    shutil.copy2(args.accepted / "inputs/classpath/classes.jar", work / "inputs/classpath/classes.jar")
    shutil.copytree(args.accepted / "inputs/classpath/stub", work / "inputs/classpath/stub")
    require(digest(work / "inputs/classpath/classes.jar") == CLASSPATH_SHA256, "Frozen ABI classpath differs")
    for relative, expected in previous["stub_class_sha256"].items():
        require(digest(work / "inputs/classpath/stub" / relative) == expected, "Frozen ABI stub differs: " + relative)
    accepted_freeze = json.loads((args.accepted / "source-freeze.json").read_text(encoding="utf-8"))
    sources = {}
    for family in FAMILIES:
        source = ROOT / "java_src" / (family + ".java")
        expected = accepted_freeze["sources"][str(source)]
        frozen = args.accepted / "inputs/frozen-src" / (family + ".java")
        require(digest(source) == expected == digest(frozen), "Effect source no longer matches accepted code: " + family)
        destination = work / "inputs/frozen-src" / (family + ".java")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(frozen, destination)
        require(digest(destination) == expected, "Frozen effect source differs: " + family)
        sources[str(source)] = expected
    write_json(work / "source-freeze.json", {"families": FAMILIES, "sources": sources,
               "baseline_sha256": CURRENT_SHA256, "accepted_dex_sha256": ACCEPTED_DEX_SHA256,
               "accepted_freeze_sha256": ACCEPTED_FREEZE_SHA256, "android_jar_sha256": digest(args.android_jar),
               "abi_classpath_sha256": CLASSPATH_SHA256, "new_compilation_performed": False})

    def guard():
        require(digest(args.baseline) == CURRENT_SHA256, "Current APK snapshot changed during packaging")
        require(digest(accepted_dex) == ACCEPTED_DEX_SHA256, "Frozen accepted DEX changed")
        for path, expected in sources.items():
            require(digest(path) == expected, "Effect source changed during packaging: " + path)

    java = args.jdk / "bin/java.exe"
    signer = [java, "-jar", args.build_tools / "lib/apksigner.jar"]
    old_signature = command(work, "baseline-signature", [*signer, "verify", "--verbose", "--print-certs", args.baseline])
    require(certificate(old_signature) == [CERTIFICATE_SHA256], "Current installation signing certificate differs")
    disassembler = ";".join(map(str, [args.preparation / "tool-classes/disassembler", *h.BAKJARS]))
    command(work, "current-feature-disassemble", [java, "-cp", disassembler, "DisassembleTool", current_dex, work / "baseline-smali"])
    command(work, "accepted-feature-disassemble", [java, "-cp", disassembler, "DisassembleTool", accepted_dex, work / "accepted-smali"])
    old = h.smali_tree(work / "baseline-smali")
    accepted = h.smali_tree(work / "accepted-smali")
    require(set(old) == h.dex_classes(old_bytes), "Current feature disassembly class membership differs")
    require(set(accepted) == h.dex_classes(accepted_dex.read_bytes()), "Accepted feature disassembly membership differs")
    patch = {desc: value for desc, value in accepted.items() if target(desc)}
    require(all("L" + family + ";" in patch for family in FAMILIES), "Effect family root missing")
    require(not any(target(desc) for desc in primary), "Effect family occurs in primary DEX")
    require(not set(primary) & set(patch), "Effect class duplicates primary DEX")
    shutil.copytree(work / "baseline-smali", work / "merged-smali")
    for desc, (path, _) in old.items():
        if target(desc):
            (work / "merged-smali" / path.relative_to(work / "baseline-smali")).unlink()
    for path, data in patch.values():
        destination = work / "merged-smali" / path.relative_to(work / "accepted-smali")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    command(work, "assemble-effects-only", [java, "-cp", str(args.preparation / "tool-classes/assembler") + ";" + str(h.APKTOOL),
            "AssembleTool", work / "merged-smali", work / "feature/classes.dex"])
    feature = (work / "feature/classes.dex").read_bytes()
    require(feature[:8] == b"dex\n039\0", "Effect writer must retain DEX039")
    command(work, "candidate-feature-disassemble", [java, "-cp", disassembler, "DisassembleTool", work / "feature/classes.dex", work / "candidate-smali"])
    candidate = h.smali_tree(work / "candidate-smali")
    expected_classes = {desc for desc in old if not target(desc)} | set(patch)
    require(set(candidate) == expected_classes == h.dex_classes(feature), "Pure effect candidate class membership differs")
    non_target_failures = sorted(desc for desc in old if not target(desc) and old[desc][1] != candidate[desc][1])
    normalize = lambda data: re.sub(rb"(?m)^(\s*)const-string/jumbo ", rb"\1const-string ", data)
    patch_failures = sorted(desc for desc in patch if normalize(patch[desc][1]) != normalize(candidate[desc][1]))
    write_json(work / "strict-class-diff.json", {"families": FAMILIES, "baseline_class_count": len(old), "candidate_class_count": len(candidate),
               "selected_effect_class_count": len(patch), "non_target_byte_exact_count": sum(not target(desc) for desc in old) - len(non_target_failures),
               "unexpected_differences": non_target_failures, "patch_disassembly_differences": patch_failures,
               "non_target_encoding_exceptions_allowed": False, "added_classes": sorted(set(candidate) - set(old)),
               "removed_classes": sorted(set(old) - set(candidate)), "candidate_smali_sha256": {desc: sha(data) for desc, (_, data) in candidate.items()}})
    require(not non_target_failures, "Non-target code changed; no encoding exceptions permitted")
    require(not patch_failures, "Effect code differs from accepted patch")
    require(not set(primary) & set(candidate), "Cross-DEX duplicate class")
    require(not any(desc.startswith(("Landroid/", "Landroidx/", "Lcom/tencent/mm/", "Lde/robv/android/xposed/")) for desc in candidate), "Runtime or compile stubs leaked into feature DEX")
    h.WORK, h.PREP, h.ANDROID = work, work, args.android_jar
    h.verify_abi(old, candidate, primary)
    guard()
    print("PASS: actual narrowed mixture ABI and byte-exact non-target code", flush=True)

    unsigned = work / "unsigned.apk"
    with zipfile.ZipFile(args.baseline) as src, zipfile.ZipFile(unsigned, "x") as dst:
        dst.comment = src.comment
        for info in src.infolist():
            if h.signature_entry(info.filename):
                continue
            if info.filename == "classes2.dex":
                replacement = copy.copy(info)
                dst.writestr(replacement, feature)
            else:
                h.copy_raw_entry(src, dst, info)
    command(work, "zipalign-build", [args.build_tools / "zipalign.exe", "-p", "4", unsigned, work / "aligned.apk"])
    build_text = (ROOT / "scripts/build.ps1").read_text(encoding="utf-8-sig")
    credential = re.search(r"^\s*\$KSPASS\s*=\s*(['\"])(.*?)\1", build_text, re.M)
    require(credential is not None and (ROOT / "build/mindlink.jks").is_file(), "Existing signing identity is unavailable")
    final = work / "MindLink-1.0-host-effects-candidate.apk"
    env = dict(os.environ)
    env["MINDLINK_EFFECTS_SIGN_PASSWORD"] = credential.group(2)
    try:
        command(work, "sign-original-key", [*signer, "sign", "--ks", ROOT / "build/mindlink.jks", "--ks-key-alias", "mindlink",
                "--ks-pass", "env:MINDLINK_EFFECTS_SIGN_PASSWORD", "--key-pass", "env:MINDLINK_EFFECTS_SIGN_PASSWORD",
                "--v3-signing-enabled", "true", "--v4-signing-enabled", "false", "--out", final, work / "aligned.apk"], env=env)
    finally:
        env.pop("MINDLINK_EFFECTS_SIGN_PASSWORD", None)
    new_signature = command(work, "candidate-signature", [*signer, "verify", "--verbose", "--print-certs", final])
    require(certificate(new_signature) == certificate(old_signature), "Original signing certificate changed")
    require("Verified using v3 scheme (APK Signature Scheme v3): true" in new_signature, "V3 signature not verified")
    command(work, "zipalign-final-check", [args.build_tools / "zipalign.exe", "-c", "-p", "4", final])
    changed, raw_changed, entries = [], [], {}
    with zipfile.ZipFile(args.baseline) as old_zip, zipfile.ZipFile(final) as new_zip:
        require(len(new_zip.namelist()) == len(set(new_zip.namelist())) and new_zip.testzip() is None, "Signed candidate ZIP invalid")
        old_names = {name for name in old_zip.namelist() if not h.signature_entry(name)}
        new_names = {name for name in new_zip.namelist() if not h.signature_entry(name)}
        require(old_names == new_names, "Non-signature APK entries changed")
        for name in sorted(old_names):
            before, after = old_zip.read(name), new_zip.read(name)
            raw_equal = h.raw_zip_member(old_zip, old_zip.getinfo(name))[1] == h.raw_zip_member(new_zip, new_zip.getinfo(name))[1]
            require(old_zip.getinfo(name).compress_type == new_zip.getinfo(name).compress_type, "APK compression strategy changed: " + name)
            entries[name] = {"baseline_sha256": sha(before), "candidate_sha256": sha(after), "byte_exact": before == after,
                             "compressed_stream_byte_exact": raw_equal, "zip_method": new_zip.getinfo(name).compress_type,
                             "compressed_bytes": new_zip.getinfo(name).compress_size}
            if before != after:
                changed.append(name)
            if not raw_equal:
                raw_changed.append(name)
        require(changed == ["classes2.dex"] == raw_changed, "Non-effect APK content or compressed stream changed")
        require(new_zip.read("classes2.dex") == feature, "Signed feature DEX differs")
    write_json(work / "strict-zip-entry-diff.json", {"changed_non_signature_entries": changed,
               "changed_compressed_streams": raw_changed, "non_signature_entry_count": len(entries), "entries": entries})
    guard()
    result = {"apk": str(final), "apk_sha256": digest(final), "apk_bytes": final.stat().st_size,
              "baseline_apk": str(args.baseline), "baseline_sha256": CURRENT_SHA256, "baseline_bytes": args.baseline.stat().st_size,
              "size_delta_bytes": final.stat().st_size - args.baseline.stat().st_size, "families": FAMILIES,
              "feature_dex_sha256": sha(feature), "primary_dex_sha256": PRIMARY_SHA256, "certificate_sha256": CERTIFICATE_SHA256,
              "changed_non_signature_entries": changed, "all_other_entries_and_compressed_streams_byte_exact": True,
              "selected_effect_class_count": len(patch), "non_target_class_count": sum(not target(desc) for desc in old),
              "source_freeze_sha256": digest(work / "source-freeze.json"), "v3_verified": True, "zipalign_verified": True,
              "desktop_sidebar_header_bubble_code_preserved": True, "production_sources_modified": False,
              "ui_build_performed": False, "device_operations_performed": False, "device_acceptance_complete": False,
              "status": "signed-local-candidate-awaiting-regression-and-installation-authorization"}
    write_json(work / "build-result.json", result)
    print("PASS: only classes2.dex changed; installed UI, resources, notices and compressed streams preserved", flush=True)
    print("APK: " + str(final), flush=True)
    print(f"Bytes: {result['apk_bytes']}; delta: {result['size_delta_bytes']}; SHA-256: {result['apk_sha256']}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline", "accepted", "preparation", "output", "jdk", "build-tools", "android-jar"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--baseline-sha256", required=True)
    args = parser.parse_args()
    try:
        build(args)
        return 0
    except (ValueError, AssertionError, OSError, KeyError, zipfile.BadZipFile) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
