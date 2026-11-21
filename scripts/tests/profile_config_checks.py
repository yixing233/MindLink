"""Compile and run real ConfigStorage profile/DB bridges and resolver reflection locally.

Usage: python -I scripts/tests/profile_config_checks.py --jdk <JDK 21 root>
Each case uses a fresh JVM. Production sources are passed directly to javac, never
copied or patched. Metadata doubles expose exact public signatures checked against
the bundled DexKit smali; no native DexKit, Android runtime, device or network is used.
"""

import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "scripts/tests/profile-config"
PRODUCTION = ROOT / "java_src/wex/ui/core/feature"
BRIDGE_CASES = (
    "unique-instance-target", "first-callback-during-hook", "failed-first-callback",
    "collision", "missing-target", "static-target", "abstract-target",
    "resolve-failure", "submit-failure", "hook-failure", "host-after-observation",
    "self-reading-recursion", "mandatory-reader-failure", "optional-name-failure",
    "listener-isolation", "getter-after-config-first", "identity-bound-direct-db",
    "closed-config-db", "reject-nested-erased-db", "ambiguous-config-db",
    "late-config-db", "privacy-logger-failure", "kernel-ready-direct-replay", "kernel-before-reader",
    "kernel-not-ready", "kernel-failed-after", "no-kernel-initialization", "constructor-partial-denied",
    "ready-runnable-after", "ready-runnable-failed-after", "kernel-ambiguous-core", "kernel-ambiguous-account",
    "kernel-static-core", "kernel-erased-core", "kernel-nested-core", "kernel-ambiguous-instance",
    "kernel-ambiguous-state", "same-instance-throttle",
)
METADATA_CASES = ("real-config-metadata", "real-metadata-collision", "typed-array-primitive")
SIGNATURES = {
    "query/matchers/MethodMatcher.smali": (
        "paramTypes([Ljava/lang/String;)Lorg/luckypray/dexkit/query/matchers/MethodMatcher;",
        "declaredClass(Lorg/luckypray/dexkit/query/matchers/ClassMatcher;)Lorg/luckypray/dexkit/query/matchers/MethodMatcher;",
        "returnType(Ljava/lang/String;)Lorg/luckypray/dexkit/query/matchers/MethodMatcher;",
        "paramCount(I)Lorg/luckypray/dexkit/query/matchers/MethodMatcher;",
        "modifiers(I)Lorg/luckypray/dexkit/query/matchers/MethodMatcher;",
    ),
    "query/matchers/ClassMatcher.smali": (
        "usingEqStrings([Ljava/lang/String;)Lorg/luckypray/dexkit/query/matchers/ClassMatcher;",
    ),
    "query/FindMethod.smali": (
        "matcher(Lorg/luckypray/dexkit/query/matchers/MethodMatcher;)Lorg/luckypray/dexkit/query/FindMethod;",
        "searchPackages([Ljava/lang/String;)Lorg/luckypray/dexkit/query/FindMethod;",
    ),
}


def execute(command):
    result = subprocess.run(command, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=90)
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
    return result.returncode


def check_metadata_signatures():
    for relative, signatures in SIGNATURES.items():
        source = ROOT / "smali/org/luckypray/dexkit" / relative
        methods = [line.strip() for line in source.read_text(encoding="utf-8").splitlines()
                   if line.startswith(".method public ")]
        for signature in signatures:
            if not any(line.endswith(" " + signature) for line in methods):
                raise RuntimeError(f"Bundled DexKit public API missing: {source}: {signature}")
    print("PASS bundled DexKit public signatures (8 exact String[]/ClassMatcher/primitive/finder APIs)", flush=True)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--suite", choices=("all", "bridge", "metadata"), default="all")
    parser.add_argument("--case", action="append", dest="cases", help="Run only named cases (repeatable)")
    parser.add_argument("--classes-dir", type=Path, help="Keep compiled fixture classes for focused rechecks")
    parser.add_argument("--compile-only", action="store_true", help="Compile requested suites without running cases")
    args = parser.parse_args()
    if args.cases:
        selected = BRIDGE_CASES + METADATA_CASES if args.suite == "all" else BRIDGE_CASES if args.suite == "bridge" else METADATA_CASES
        if any(case not in selected for case in args.cases):
            parser.error("--case must name a case in the requested suite")
    suffix = ".exe" if (args.jdk / "bin/javac.exe").is_file() else ""
    javac, java = args.jdk / ("bin/javac" + suffix), args.jdk / ("bin/java" + suffix)
    if not javac.is_file() or not java.is_file():
        parser.error("--jdk must contain bin/javac and bin/java")
    failures = []
    if args.suite in ("all", "metadata"):
        try:
            check_metadata_signatures()
        except (OSError, RuntimeError) as error:
            print(f"FAILED: {error}", file=sys.stderr)
            return 1
    common = sorted((TESTS / "common").rglob("*.java")) + [ROOT / relative for relative in (
        "scripts/tests/database-capture/common/android/database/Cursor.java",
        "scripts/tests/database-capture/common/de/robv/android/xposed/XC_MethodHook.java",
        "scripts/tests/database-capture/common/de/robv/android/xposed/callbacks/XC_LoadPackage.java",
    )]
    from contextlib import nullcontext
    workspace = nullcontext(args.classes_dir) if args.classes_dir else tempfile.TemporaryDirectory(prefix="mindlink-profile-config-")
    with workspace as temporary:
        for suite, entrypoint, cases in (
            ("bridge", "ProfileConfigChecks", BRIDGE_CASES),
            ("metadata", "ProfileMetadataChecks", METADATA_CASES),
        ):
            if args.suite not in ("all", suite):
                continue
            classes = Path(temporary) / suite
            classes.mkdir(parents=True, exist_ok=True)
            sources = common + sorted((TESTS / suite).rglob("*.java"))
            production = ["HostProfileBridge", "HostDatabaseBridge", "ReadOnlySql"]
            if suite == "bridge":
                sources += [ROOT / "scripts/tests/database-capture/common/android/content/Context.java",
                            ROOT / "scripts/tests/database-capture/common/android/content/pm/ApplicationInfo.java"]
            else:
                production += ["HostTargetResolver"]
                sources += [path for path in sorted((ROOT / "scripts/tests/resolver/android").rglob("*.java"))
                            if path.name not in ("Cursor.java", "Looper.java")]
                sources += [ROOT / "scripts/tests/menu/android/os/Build.java",
                            ROOT / "scripts/tests/wex/ui/core/WexStore.java"]
            sources += [PRODUCTION / (name + ".java") for name in production]
            print(f"Compile real {', '.join(production)} ({suite} isolated source set)", flush=True)
            code = execute([str(javac), "-J-Duser.language=en", "-J-Duser.country=US", "-encoding", "UTF-8",
                            "--release", "21", "-sourcepath", str(TESTS / suite), "-d", str(classes), *map(str, sources)])
            if code:
                failures.append(f"{suite}: compilation (exit {code})")
                continue
            if args.compile_only:
                continue
            for case in cases:
                if args.cases and case not in args.cases:
                    continue
                print(f"CASE {suite}/{case}", flush=True)
                code = execute([str(java), "-cp", str(classes), "wex.ui.core.feature." + entrypoint, case])
                if code:
                    failures.append(f"{suite}/{case} (exit {code})")
    if failures:
        print("FAILED: " + "; ".join(failures), file=sys.stderr)
        return 1
    print("All requested real-source ConfigStorage checks passed" + (" (compilation only)." if args.compile_only else "."))
    print("Production SHA256 below is an observed snapshot only, not a frozen-source or production-regression conclusion.")
    for name in ("HostProfileBridge", "HostDatabaseBridge", "HostTargetResolver"):
        source = PRODUCTION / (name + ".java")
        print(f"SHA256 {source}: {hashlib.sha256(source.read_bytes()).hexdigest()}")
    print("Not exercised: ART/Xposed interception, native DexKit matching, host database contents, device startup or network.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
