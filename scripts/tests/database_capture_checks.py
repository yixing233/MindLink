"""Compile real database capture/bootstrap sources against isolated Java doubles.

Usage: python -I scripts/tests/database_capture_checks.py --jdk <JDK 21 root>
No Android runtime, native DexKit, shared resolver stubs, or full build is used.
Each case runs in a fresh JVM; sources are neither copied nor patched.
"""

import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "scripts/tests/database-capture"
DATABASE_CASES = (
    "hook-failure", "resolver-failure", "late-capture", "named-string",
    "named-object", "verified-wrapper", "ambiguous-unknown", "ambiguous-overloads",
    "interface-metadata", "reject-unrelated-nested", "closed-database",
    "replacement-listeners", "privacy", "legacy-locate", "missing-account-metadata", "ambiguous-account-metadata",
)
BOOTSTRAP_CASES = (
    "success", "attached-loader", "resolver-throwable", "resolver-error", "host-profile-throwable", "host-profile-error", "profile-throwable",
    "profile-error", "both-fail", "each-feature-fails", "logger-fails",
)


def execute(command):
    result = subprocess.run(command, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=60)
    if result.stdout:
        print(result.stdout, end="", flush=True)
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr, flush=True)
    return result.returncode


def check_loader_smali():
    path = ROOT / "smali/z4/c.smali"
    before = hashlib.sha256(path.read_bytes()).hexdigest()
    actual = path.read_text(encoding="utf-8")
    original = subprocess.run(["git", "-C", str(ROOT), "show", "HEAD:smali/z4/c.smali"],
                              capture_output=True, text=True, encoding="utf-8", check=True).stdout
    attached = """    const/4 v2, 0x0

    invoke-virtual {p1}, Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;->hasThrowable()Z

    move-result v3

    if-nez v3, :attached_context_done

    iget-object v3, p1, Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;->thisObject:Ljava/lang/Object;

    instance-of v0, v3, Landroid/content/Context;

    if-eqz v0, :attached_context_done

    check-cast v3, Landroid/content/Context;

    move-object v2, v3

    :attached_context_done
"""
    bootstrap = """    if-eqz v2, :legacy_init

    :bootstrap_try_start
    invoke-static {p1, v2}, Lwex/ui/core/feature/FeatureBootstrap;->init(Lde/robv/android/xposed/callbacks/XC_LoadPackage$LoadPackageParam;Landroid/content/Context;)V
    :bootstrap_try_end
    .catch Ljava/lang/Throwable; {:bootstrap_try_start .. :bootstrap_try_end} :bootstrap_failed

    goto :legacy_init

    :bootstrap_failed
    move-exception v3

    :legacy_init
"""
    assert actual.count(attached) == actual.count(bootstrap) == 1, "Only exact attach/bootstrap additions are allowed"
    assert actual.count("    .locals 4\n") == 1, "Register expansion must be exact"
    reconstructed = actual.replace(attached, "").replace(bootstrap, "").replace("    .locals 4\n", "    .locals 2\n")
    assert reconstructed == original, "Constructor/fields/process gate/verify/legacy init/non-target bytes remain exact"
    assert actual.index("access$verifyModule") < actual.index("if-eqz v0, :cond_1") < actual.index("FeatureBootstrap;->init")
    assert actual.index("hasThrowable()Z") < actual.index("->thisObject:") < actual.index("iget-object p1, p0")
    assert actual.index(":bootstrap_failed\n    move-exception v3") < actual.index("access$doInit")
    assert "->classLoader:" not in actual, "Shared LoadPackageParam loader must never be mutated"
    assert before == hashlib.sha256(path.read_bytes()).hexdigest(), "Read-only source gate changed smali"
    print("PASS loader-smali exact non-target/verify/attach-throwable/legacy-fallback source gate")
    print(f"SHA256 {path}: {before}")
    print("Not exercised: smali assembly, ART verification, Xposed/device interception.")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path)
    parser.add_argument("--loader-smali", action="store_true")
    parser.add_argument("--suite", choices=("all", "database", "bootstrap"), default="all")
    parser.add_argument("--case", action="append", choices=DATABASE_CASES + BOOTSTRAP_CASES)
    args = parser.parse_args()
    if args.loader_smali:
        check_loader_smali()
        return 0
    if args.jdk is None:
        parser.error("--jdk is required for Java checks")
    suffix = ".exe" if (args.jdk / "bin/javac.exe").is_file() else ""
    javac = args.jdk / ("bin/javac" + suffix)
    java = args.jdk / ("bin/java" + suffix)
    if not javac.is_file() or not java.is_file():
        parser.error("--jdk must contain bin/javac and bin/java")
    failures = []
    with tempfile.TemporaryDirectory(prefix="mindlink-database-capture-") as temporary:
        for suite, production, entrypoint, cases in (
            ("database", ("HostDatabaseBridge", "ReadOnlySql"), "DatabaseCaptureChecks", DATABASE_CASES),
            ("bootstrap", ("FeatureBootstrap",), "FeatureBootstrapChecks", BOOTSTRAP_CASES),
        ):
            if args.suite not in ("all", suite):
                continue
            classes = Path(temporary) / suite
            classes.mkdir()
            sources = sorted((TESTS / "common").rglob("*.java"))
            sources += sorted((TESTS / suite).rglob("*.java"))
            sources += [ROOT / "java_src/wex/ui/core/feature" / (name + ".java")
                        for name in production]
            print(f"Compile real {', '.join(production)} ({suite} isolated source set)", flush=True)
            code = execute([str(javac), "-J-Duser.language=en", "-J-Duser.country=US",
                            "-encoding", "UTF-8", "--release", "21", "-sourcepath",
                            str(TESTS / suite), "-d", str(classes), *map(str, sources)])
            if code:
                failures.append(f"{suite}: compilation (exit {code})")
                continue
            for case in cases:
                if args.case and case not in args.case:
                    continue
                code = execute([str(java), "-cp", str(classes),
                                "wex.ui.core.feature." + entrypoint, case])
                if code:
                    failures.append(f"{suite}/{case} (exit {code})")
    if failures:
        print("FAILED: " + "; ".join(failures), file=sys.stderr)
        return 1
    print("All requested real-source database capture/bootstrap checks passed.")
    print("Not exercised: real ART/Xposed interception, native DexKit, Android storage or device startup.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
