"""Build selected native sources over a verified APK, preserving Compose and resources."""
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
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PREP = ROOT / "build/full-ui-lifecycle-fix-20261114-111241-832dc7ff"
COMPILE = ROOT / "build/remaining-repairs-20261116/candidate-002"
FAMILIES = tuple("wex/ui/core/feature/" + name for name in (
    "FloatingChatFooterFeature", "FloatingFooterLayout", "FloatingHeaderStyle",
    "ChatGlassSurface", "ViewBackdropSampler", "ChatFooterOverlay", "ChatTailSpacer"))


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


def owned(descriptor):
    return any(descriptor == "L" + name + ";" or descriptor.startswith("L" + name + "$")
               or descriptor == "L" + name + "-IA;" for name in FAMILIES)


def main():
    global FAMILIES
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--baseline-sha256", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--family", action="append",
                        help="Compile only these wex.ui.core.feature class families; default: integrated chat families")
    args = parser.parse_args()
    if args.family:
        require(all(re.fullmatch(r"[A-Z][A-Za-z0-9_]*", name) for name in args.family),
                "Family must be a simple feature class name")
        require(len(args.family) == len(set(args.family)), "Duplicate family")
        FAMILIES = tuple("wex/ui/core/feature/" + name for name in args.family)
    baseline = args.baseline.resolve()
    expected = args.baseline_sha256.lower()
    require(digest(baseline) == expected, "Baseline identity changed")
    helper = PREP / "scripts/hotfix.py"
    require(digest(helper) == "573aff0e87d9d9cb8f01f65fe553c50bb58fbf3d836d9d46503ece9e9994fa0e",
            "DEX helper identity changed")
    require(digest(COMPILE / "classes.jar") == "c8bbb91a14d57fad0336be802adcae5a6d0255e213b3b38963c099bdc09477df",
            "Compile classpath identity changed")
    work = args.output.resolve()
    require(work.is_relative_to(ROOT / "build") and not work.exists(), "Use a new build directory")
    spec = importlib.util.spec_from_file_location("chat_candidate_tools", helper)
    h = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(h)
    for relative in ("logs", "inputs/classpath", "inputs/frozen-src", "empty-sourcepath",
                     "classes", "patch-dex", "feature"):
        (work / relative).mkdir(parents=True, exist_ok=False)
    shutil.copy2(COMPILE / "classes.jar", work / "inputs/classpath/classes.jar")
    shutil.copytree(COMPILE / "stub", work / "inputs/classpath/stub")
    h.PREP = h.WORK = work
    h.FAMILIES, h.family_desc = FAMILIES, owned
    sources, frozen = {}, []
    for family in FAMILIES:
        source = ROOT / "java_src" / (family + ".java")
        target = work / "inputs/frozen-src" / (family + ".java")
        target.parent.mkdir(parents=True, exist_ok=True)
        sources[str(source)] = digest(source)
        shutil.copy2(source, target)
        require(digest(target) == sources[str(source)], "Source changed during freeze")
        frozen.append(target)
    h.dump("source-freeze.json", {"sources": sources, "families": FAMILIES, "baseline_sha256": expected})

    def guard():
        require(digest(baseline) == expected, "Baseline changed during build")
        require(all(digest(path) == value for path, value in sources.items()), "Source changed during build")

    def run(label, command, env=None):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, env=env, capture_output=True,
                                encoding="utf-8", errors="replace")
        (work / "logs" / (label + ".txt")).write_text(result.stdout + result.stderr, encoding="utf-8")
        require(result.returncode == 0, label + " failed; inspect build log")
        guard()
        print("PASS: " + label, flush=True)
        return result.stdout + result.stderr

    java = h.JDK / "bin/java.exe"
    disassembler = ";".join(map(str, [PREP / "tool-classes/disassembler", *h.BAKJARS]))
    with zipfile.ZipFile(baseline) as archive:
        require(archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist())),
                "Invalid baseline ZIP")
        for name in ("classes.dex", "classes2.dex"):
            (work / "inputs" / name).write_bytes(archive.read(name))
    for name, destination in (("classes.dex", "primary-smali"), ("classes2.dex", "baseline-smali")):
        run(destination, [java, "-cp", disassembler, "DisassembleTool", work / "inputs" / name, work / destination])
    old = h.smali_tree(work / "baseline-smali")
    primary = h.smali_tree(work / "primary-smali")
    require(not any(owned(desc) for desc in primary), "Target family in primary DEX")
    classpath = ";".join(map(str, [h.ANDROID, work / "inputs/classpath/classes.jar", work / "inputs/classpath/stub"]))
    run("compile", [h.JDK / "bin/javac.exe", "-encoding", "UTF-8", "-source", "11", "-target", "11",
                    "-proc:none", "-implicit:none", "-sourcepath", work / "empty-sourcepath",
                    "-classpath", classpath, "-d", work / "classes", *frozen])
    with zipfile.ZipFile(work / "patch.jar", "x", compression=zipfile.ZIP_STORED) as archive:
        for path in sorted((work / "classes").rglob("*.class")):
            relative = path.relative_to(work / "classes").as_posix()
            require(owned("L" + relative[:-6] + ";"), "Unexpected compiler output")
            archive.write(path, relative)
    run("d8", [java, "-cp", h.BT / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", h.ANDROID,
               "--classpath", work / "inputs/classpath/classes.jar", "--classpath", work / "inputs/classpath/stub",
               "--min-api", "33", "--output", work / "patch-dex", work / "patch.jar"])
    run("patch-disassemble", [java, "-cp", disassembler, "DisassembleTool",
                             work / "patch-dex/classes.dex", work / "patch-smali"])
    raw = h.smali_tree(work / "patch-smali")
    patch = {desc: value for desc, value in raw.items() if owned(desc)}
    extra = set(raw) - set(patch)
    require(all(desc in old and old[desc][1] == raw[desc][1] for desc in extra), "Unexpected unowned D8 class")
    require(all("L" + name + ";" in patch for name in FAMILIES), "Missing target family")
    shutil.copytree(work / "baseline-smali", work / "merged-smali")
    for desc, (path, _) in old.items():
        if owned(desc):
            (work / "merged-smali" / path.relative_to(work / "baseline-smali")).unlink()
    for desc, (path, _) in patch.items():
        target = work / "merged-smali" / path.relative_to(work / "patch-smali")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    run("assemble", [java, "-cp", str(PREP / "tool-classes/assembler") + ";" + str(h.APKTOOL),
                     "AssembleTool", work / "merged-smali", work / "feature/classes.dex"])
    run("verify-disassembly", [java, "-cp", disassembler, "DisassembleTool",
                              work / "feature/classes.dex", work / "candidate-smali"])
    candidate = h.smali_tree(work / "candidate-smali")
    require(set(candidate) == {desc for desc in old if not owned(desc)} | set(patch), "Unexpected class membership")
    require(all(candidate[desc][1] == value[1] for desc, value in old.items() if not owned(desc)),
            "Unrelated class changed")
    normalize = lambda value: re.sub(rb"(?m)^(\s*)const-string/jumbo ", rb"\1const-string ", value)
    require(all(normalize(candidate[desc][1]) == normalize(value[1]) for desc, value in patch.items()),
            "Patch differs after assembly")
    require(not set(candidate) & set(primary), "Cross-DEX duplicate class")
    require(set(candidate) == h.dex_classes((work / "feature/classes.dex").read_bytes()), "DEX definitions differ")
    h.verify_abi(old, candidate, primary)
    h.dump("strict-class-diff.json", {
        "families": FAMILIES, "preserved_non_target_classes": sum(not owned(desc) for desc in old),
        "added_classes": sorted(set(candidate) - set(old)), "removed_classes": sorted(set(old) - set(candidate)),
        "unexpected_differences": []})
    with zipfile.ZipFile(baseline) as src, zipfile.ZipFile(work / "unsigned.apk", "x") as dst:
        for info in src.infolist():
            if h.signature_entry(info.filename):
                continue
            if info.filename == "classes2.dex":
                dst.writestr(copy.copy(info), (work / "feature/classes.dex").read_bytes())
            else:
                h.copy_raw_entry(src, dst, info)
    run("align", [h.BT / "zipalign.exe", "-p", "4", work / "unsigned.apk", work / "aligned.apk"])
    signer = [java, "-jar", h.BT / "lib/apksigner.jar"]
    env = os.environ.copy()
    env["MINDLINK_CANDIDATE_PASSWORD"] = "mindlink"
    final = work / "MindLink-1.0.apk"
    run("sign", [*signer, "sign", "--ks", ROOT / "build/mindlink.jks", "--ks-key-alias", "mindlink",
                 "--ks-pass", "env:MINDLINK_CANDIDATE_PASSWORD", "--key-pass", "env:MINDLINK_CANDIDATE_PASSWORD",
                 "--v4-signing-enabled", "false", "--out", final, work / "aligned.apk"], env)
    certificates = []
    for label, apk in (("baseline-signature", baseline), ("candidate-signature", final)):
        text = run(label, [*signer, "verify", "--verbose", "--print-certs", apk])
        certificates.append(re.findall(
            r"^(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-f]+)$", text, re.M))
    require(len(certificates[0]) == 1 and certificates[0] == certificates[1], "Signing certificate differs")
    run("signed-alignment", [h.BT / "zipalign.exe", "-c", "-p", "4", final])
    with zipfile.ZipFile(baseline) as src, zipfile.ZipFile(final) as dst:
        require(dst.testzip() is None, "Candidate CRC failed")
        names = lambda archive: {name for name in archive.namelist() if not h.signature_entry(name)}
        require(names(src) == names(dst), "APK entry membership changed")
        changed = sorted(name for name in names(src) if src.read(name) != dst.read(name))
        require(changed == ["classes2.dex"], "Unexpected APK entry changed")
        for name in names(src) - {"classes2.dex"}:
            require(h.raw_zip_member(src, src.getinfo(name))[1] == h.raw_zip_member(dst, dst.getinfo(name))[1],
                    "Unrelated compressed stream changed: " + name)
    h.dump("build-result.json", {"apk": str(final), "sha256": digest(final), "bytes": final.stat().st_size,
        "baseline": str(baseline), "baseline_sha256": expected, "changed_entries": changed,
        "certificate_sha256": certificates[1][0], "device_installed": False})
    print("CANDIDATE: " + str(final), flush=True)


if __name__ == "__main__":
    main()
