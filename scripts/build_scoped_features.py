"""Build only the approved feature families over a hash-identified installed APK.

The previous verified preparation supplies read-only classpath and deterministic
DEX tools. No installation, source-tree rebuild, data reset or signing is done here.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
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
    "FloatingChatFooterFeature", "FloatingFooterLayout", "ChatFooterOverlay", "ChatTailSpacer",
    "ChatGlassSurface", "FloatingHeaderStyle", "NavigationBarViews", "ViewBackdropSampler",
    "FeatureBootstrap", "HostDatabaseBridge", "SelfProfile", "HomeSidePanelFeature", "HomeSidePanelData",
    "HomeSidePanelWallet", "ProfileView", "ComposePayloadBridge", "NavigationBottomSpace", "HostProfileBridge",
    "HostStageLog", "HomeSidePanelSnapshot", "SidePanelComposeBridge", "ComposePayloadLoader"))
REDUNDANT = {"Lcom/android/tools/r8/annotations/LambdaMethod;", *("L" + PREFIX + name + "-IA;" for name in (
    "ChatToolbarFeature", "FloatingChatFooterFeature", "FloatingChatHeaderFeature", "HomeSidePanelFeature"))}
PAYLOAD_PREFIXES = ("assets/compose-payload/", "assets/sidepanel-compose-payload/")
# D8 emits this empty constructor-access marker for the new private icon-cache
# class. It is referenced by that scoped family, not a discarded global artifact.
OWNED_SYNTHETICS = {"Lwex/ui/core/feature/HomeSidePanelSnapshot-IA;"}
HELPER_SHA256 = "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path, values):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(values, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def target(descriptor):
    return descriptor in OWNED_SYNTHETICS or (descriptor.startswith("L") and descriptor.endswith(";") and any(
        descriptor[1:-1] == name or descriptor[1:-1].startswith(name + "$") for name in FAMILIES))


def command(work, label, values):
    result = subprocess.run(list(map(str, values)), cwd=ROOT, capture_output=True,
                            encoding="utf-8", errors="replace")
    (work / "logs" / (label + ".txt")).write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        print(result.stdout + result.stderr, flush=True)
        raise ValueError(f"{label} failed ({result.returncode}); see isolated logs")
    print("PASS: " + label, flush=True)


def frozen_copy(source, dest):
    expected = digest(source)
    dest.parent.mkdir(parents=True, exist_ok=True)
    require(not dest.exists(), "Frozen input already exists: " + str(dest))
    shutil.copy2(source, dest)
    require(digest(source) == expected == digest(dest), "Input changed while being frozen: " + str(source))
    return expected


def load_helper(preparation):
    path = preparation / "scripts/hotfix.py"
    require(digest(path) == HELPER_SHA256, "Verified ABI/DEX helper source changed")
    spec = importlib.util.spec_from_file_location("mindlink_verified_scoped_helper", path)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    helper.FAMILIES = FAMILIES
    helper.family_desc = target
    return helper


def build(args):
    require(re.fullmatch(r"[0-9a-f]{64}", args.baseline_sha256) is not None, "Expected baseline SHA-256 is required")
    require(digest(args.baseline) == args.baseline_sha256, "Installed baseline APK SHA-256 differs")
    require(not args.output.exists(), "Scoped build output must be a new directory")
    preparation = args.preparation.resolve()
    require(args.output.resolve() != preparation and preparation not in args.output.resolve().parents,
            "New build must not overwrite or nest inside previous preparation")
    h = load_helper(preparation)
    previous = json.loads((preparation / "input-hashes.json").read_text(encoding="utf-8"))
    generation = preparation / "generation-001"
    baseline_tree = generation / "candidate-smali"
    primary_tree = preparation / "primary-smali"
    old = h.smali_tree(baseline_tree)
    primary = h.smali_tree(primary_tree)
    expected_old = json.loads((generation / "strict-class-diff.json").read_text(encoding="utf-8"))["candidate_smali_sha256"]
    expected_primary = json.loads((preparation / "primary-smali-hashes.json").read_text(encoding="utf-8"))
    require({desc: sha(data) for desc, (_, data) in old.items()} == expected_old, "Installed classes2 smali snapshot changed")
    require({desc: sha(data) for desc, (_, data) in primary.items()} == expected_primary, "Primary smali snapshot changed")
    with zipfile.ZipFile(args.baseline) as baseline:
        require(len(baseline.namelist()) == len(baseline.NameToInfo), "Duplicate baseline ZIP entries")
        primary_bytes, feature_bytes = baseline.read("classes.dex"), baseline.read("classes2.dex")
        require(h.dex_classes(primary_bytes) == set(primary), "Primary smali does not identify the installed DEX")
        require(h.dex_classes(feature_bytes) == set(old), "Feature smali does not identify the installed DEX")
    require(not any(target(desc) for desc in primary), "Target family occurs in primary DEX")
    classpath = preparation / "inputs/classpath"
    require(digest(classpath / "classes.jar") == previous["classpath_sha256"], "Verified compile classpath changed")
    for relative, expected in previous["stub_class_sha256"].items():
        require(digest(classpath / "stub" / relative) == expected, "Verified compile stub changed: " + relative)
    for path, expected in previous["tool_sha256"].items():
        require(digest(Path(path)) == expected, "Verified DEX tool changed: " + path)
    require(digest(args.android_jar) == previous["android_jar_sha256"], "Android compilation/ABI platform differs from verified preparation")

    updated_class_hashes = json.loads((generation / "compiled-class-hashes.json").read_text(encoding="utf-8"))
    for relative, expected in updated_class_hashes.items():
        require(digest(generation / "classes" / relative) == expected, "Installed candidate compile dependency changed: " + relative)

    work = args.output.resolve()
    for relative in ["logs", "inputs/frozen-src", "inputs/classpath", "classes", "patch-dex", "feature", "empty-sourcepath"]:
        (work / relative).mkdir(parents=True, exist_ok=False)
    # The original jar predates the installed lifecycle fixes. Refresh only this
    # compilation classpath with their verified bytecode, never the shipped DEX.
    current_classpath_jar = work / "inputs/classpath/classes.jar"
    with zipfile.ZipFile(classpath / "classes.jar") as old_jar, zipfile.ZipFile(current_classpath_jar, "x", compression=zipfile.ZIP_STORED) as current_jar:
        for info in old_jar.infolist():
            if info.filename not in updated_class_hashes:
                current_jar.writestr(info, old_jar.read(info.filename))
        for relative in sorted(updated_class_hashes):
            current_jar.write(generation / "classes" / relative, relative)
    shutil.copytree(classpath / "stub", work / "inputs/classpath/stub")
    write_json(work / "compile-classpath.json", {"base_jar_sha256": previous["classpath_sha256"],
               "installed_candidate_class_overlays": updated_class_hashes, "current_jar_sha256": digest(current_classpath_jar)})
    source_hashes, sources = {}, []
    for family in FAMILIES:
        source = ROOT / "java_src" / (family + ".java")
        destination = work / "inputs/frozen-src" / (family + ".java")
        source_hashes[str(source)] = frozen_copy(source, destination)
        sources.append(destination)
    write_json(work / "source-freeze.json", {"families": FAMILIES, "sources": source_hashes,
               "baseline_sha256": args.baseline_sha256, "android_jar_sha256": digest(args.android_jar)})

    def guard():
        require(digest(args.baseline) == args.baseline_sha256, "Installed baseline changed during build")
        for path, expected in source_hashes.items():
            require(digest(Path(path)) == expected, "Production source changed after freeze: " + path)

    java, javac = args.jdk / "bin/java.exe", args.jdk / "bin/javac.exe"
    cp = ";".join(map(str, [args.android_jar, current_classpath_jar, classpath / "stub"]))
    argsfile = work / "inputs/javac-sources.txt"
    argsfile.write_text("\n".join('"' + str(source).replace("\\", "/") + '"' for source in sources) + "\n", encoding="utf-8")
    command(work, "compile-frozen-target-sources", [javac, "-J-Duser.language=en", "-J-Duser.country=US", "-nowarn", "-source", "11", "-target", "11", "-proc:none", "-implicit:none",
            "-sourcepath", work / "empty-sourcepath", "-classpath", cp, "-encoding", "UTF-8", "-d", work / "classes", "@" + str(argsfile)])
    guard()
    classes = sorted((work / "classes").rglob("*.class"))
    require(classes and all(target("L" + path.relative_to(work / "classes").as_posix()[:-6] + ";") for path in classes),
            "Unexpected compiled class outside scoped source families")
    with zipfile.ZipFile(work / "target-families.jar", "x", compression=zipfile.ZIP_STORED) as archive:
        for path in classes:
            archive.write(path, path.relative_to(work / "classes").as_posix())
    command(work, "d8-target-families", [java, "-cp", args.build_tools / "lib/d8.jar", "com.android.tools.r8.D8",
            "--lib", args.android_jar, "--classpath", current_classpath_jar, "--classpath", classpath / "stub",
            "--min-api", "33", "--output", work / "patch-dex", work / "target-families.jar"])
    guard()
    disassembler = ";".join(map(str, [preparation / "tool-classes/disassembler", *h.BAKJARS]))
    command(work, "patch-disassemble", [java, "-cp", disassembler, "DisassembleTool", work / "patch-dex/classes.dex", work / "patch-smali"])
    raw = h.smali_tree(work / "patch-smali")
    require(set(raw) == h.dex_classes((work / "patch-dex/classes.dex").read_bytes()), "Raw D8 class set differs")
    for descriptor in OWNED_SYNTHETICS & raw.keys():
        marker = raw[descriptor][1].decode("utf-8")
        require([line.strip() for line in marker.splitlines() if line.strip()] == [
                    ".class public final synthetic " + descriptor, ".super Ljava/lang/Object;", ".source \"D8$$SyntheticClass\""],
                "Owned D8 constructor marker has unexpected executable content")
        require(any(descriptor.encode() in data for desc, (_, data) in raw.items()
                    if desc != descriptor and target(desc)), "Owned D8 marker is not referenced by scoped classes")
    redundant = {desc for desc in raw if not target(desc)}
    require(redundant <= REDUNDANT, "Unexpected global D8 output: " + repr(sorted(redundant - REDUNDANT)))
    require(all(desc in old and old[desc][1] == raw[desc][1] for desc in redundant), "Redundant D8 class differs from installed baseline")
    patch = {desc: value for desc, value in raw.items() if target(desc)}
    require(all("L" + family + ";" in patch for family in FAMILIES), "Target root family missing from compiled patch")
    require(not set(patch) & set(primary), "Patch duplicates primary DEX classes")
    shutil.copytree(baseline_tree, work / "merged-smali")
    for desc, (path, _) in old.items():
        if target(desc):
            (work / "merged-smali" / path.relative_to(baseline_tree)).unlink()
    for _, (path, _) in patch.items():
        dest = work / "merged-smali" / path.relative_to(work / "patch-smali")
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, dest)
    command(work, "assemble-complete-scoped-feature-dex", [java, "-cp",
            str(preparation / "tool-classes/assembler") + ";" + str(h.APKTOOL), "AssembleTool", work / "merged-smali", work / "feature/classes.dex"])
    require((work / "feature/classes.dex").read_bytes()[:8] == b"dex\n039\0", "Feature writer must preserve baseline DEX039")
    command(work, "candidate-disassemble", [java, "-cp", disassembler, "DisassembleTool", work / "feature/classes.dex", work / "candidate-smali"])
    candidate = h.smali_tree(work / "candidate-smali")
    expected = {desc for desc in old if not target(desc)} | set(patch)
    require(set(candidate) == expected == h.dex_classes((work / "feature/classes.dex").read_bytes()), "Candidate class membership differs")
    non_target_failures = [desc for desc in old if not target(desc) and old[desc][1] != candidate[desc][1]]
    normalize = lambda data: re.sub(rb"(?m)^(\s*)const-string/jumbo ", rb"\1const-string ", data)
    patch_failures = [desc for desc in patch if normalize(patch[desc][1]) != normalize(candidate[desc][1])]
    require(not set(candidate) & set(primary), "Cross-DEX duplicate class")
    require(not any(desc.startswith(("Landroid/", "Landroidx/", "Lcom/tencent/mm/", "Lde/robv/android/xposed/")) for desc in candidate),
            "Compile stub or AndroidX runtime leaked into feature DEX")
    write_json(work / "strict-class-diff.json", {"baseline_class_count": len(old), "candidate_class_count": len(candidate),
            "non_target_byte_exact_count": sum(not target(desc) for desc in old) - len(non_target_failures),
            "non_target_encoding_exceptions_allowed": False, "unexpected_differences": non_target_failures,
            "patch_disassembly_differences": patch_failures, "redundant_d8_preserved": sorted(redundant),
            "added_classes": sorted(set(candidate) - set(old)), "removed_classes": sorted(set(old) - set(candidate)),
            "candidate_smali_sha256": {desc: sha(value[1]) for desc, value in candidate.items()},
            "baseline_primary_sha256": sha(primary_bytes), "feature_dex_sha256": digest(work / "feature/classes.dex")})
    require(not non_target_failures, "Non-target smali changed; no normalization exceptions permitted")
    require(not patch_failures, "Assembled target differs from D8 output")
    h.WORK, h.ANDROID, h.PREP = work, args.android_jar, work
    h.verify_abi(old, candidate, primary)
    guard()
    print("PASS: byte-exact non-target classes and complete incoming/outgoing/inherited platform ABI", flush=True)

    # Remove only superseded build products and explicitly updated notices. All other
    # assets, original resources, manifest and primary DEX retain their exact content.
    allowed_notices = {"LICENSE.txt": ROOT / "LICENSE", "README.md": ROOT / "README.md",
                       "THIRD-PARTY-NOTICES.md": ROOT / "THIRD-PARTY-NOTICES.md"}
    replaced_notices = {}
    stage = work / "stage.apk"
    with zipfile.ZipFile(args.baseline) as src, zipfile.ZipFile(stage, "x") as dst:
        for info in src.infolist():
            name = info.filename
            if h.signature_entry(name) or name == "classes2.dex" or name.startswith(PAYLOAD_PREFIXES) or name == "assets/compose-runtime-inventory.json":
                continue
            if name.startswith("assets/mindlink/"):
                relative = name[len("assets/mindlink/"):]
                if relative in allowed_notices:
                    content = allowed_notices[relative].read_bytes()
                    if content != src.read(name):
                        replaced_notices[name] = {"previous_sha256": sha(src.read(name)), "new_sha256": sha(content)}
                        dst.writestr(name, content, compress_type=zipfile.ZIP_DEFLATED)
                        continue
            h.copy_raw_entry(src, dst, info)
    with zipfile.ZipFile(stage) as check:
        require(check.read("classes.dex") == primary_bytes, "Primary DEX bytes changed while staging")
        require({name for name in check.namelist() if re.fullmatch(r"classes\d*\.dex", name)} == {"classes.dex"}, "Stage is not primary-only")
    write_json(work / "scoped-build-result.json", {"baseline_apk": str(args.baseline), "baseline_sha256": args.baseline_sha256,
               "families": FAMILIES, "non_target_class_count": sum(not target(desc) for desc in old),
               "primary_class_count": len(primary), "primary_dex_sha256": sha(primary_bytes),
               "feature_dex_sha256": digest(work / "feature/classes.dex"), "stage_sha256": digest(stage),
               "updated_notices": replaced_notices, "source_freeze_sha256": digest(work / "source-freeze.json"),
               "device_operations_performed": False, "production_sources_modified": False})
    guard()
    print("PASS: scoped feature DEX and primary-only installed-baseline stage prepared", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["baseline", "preparation", "output", "jdk", "build-tools", "android-jar"]:
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
