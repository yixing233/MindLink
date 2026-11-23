"""Run HostProfileBridge, HostDatabaseBridge and SelfProfile together against synthetic boundaries.

Usage: python -I -B scripts/tests/profile_integration_checks.py --jdk <JDK21 root>
Production Java sources are compiled directly, not copied/patched. Android loop,
Xposed callback delivery, resolver results and database rows are fixture boundaries.
No UI compilation, device, native DexKit, actual data or network is involved.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "scripts/tests"
PRODUCTION = ROOT / "java_src/wex/ui/core/feature"
CASES = (
    "actual-db-field-evidence", "last-query-close-rejected",
    "class-identity-metadata", "source-version-queued-reject",
    "hot-getter-bounded", "callback-monitor-independent", "rapid-hooks-coalesced",
    "self-read-no-capture-queue", "late-db-replacement-closed", "worker-epoch-reject", "deferred-before-capture",
    "not-ready-to-ready", "failed-ready-after", "kernel-before-reader", "self-read-coalesced",
    "forced-tail-latest-name", "optional-name-failure-retains", "mandatory-db-failure-config-only",
    "preference-switch-rejects-old", "callbacks-outside-bridge-locks", "safe-late-config-getter",
    "ready-without-holder-evidence", "callback-evidence-budget",
    "ready-cache-invalidation", "empty-instance-demand-blocked", "not-ready-queued-result-rejected",
    "pinned-selected-loader",
)


def execute(command):
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
    if result.stdout:
        print(result.stdout, end="", flush=True)
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr, flush=True)
    return result.returncode


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--pinned-apk", type=Path)
    parser.add_argument("--case", action="append", choices=CASES + ("pinned-early-construct-ready", "unpinned-early-skipped"))
    args = parser.parse_args()
    if args.case and "pinned-selected-loader" in args.case and not args.pinned_apk:
        parser.error("pinned-selected-loader requires --pinned-apk")
    suffix = ".exe" if (args.jdk / "bin/javac.exe").is_file() else ""
    javac, java = args.jdk / ("bin/javac" + suffix), args.jdk / ("bin/java" + suffix)
    if not javac.is_file() or not java.is_file():
        parser.error("--jdk must contain bin/javac and bin/java")
    sources = sorted(path for path in (TESTS / "profile-config/common").rglob("*.java")
                     if path.name not in ("HostStageLog.java", "Looper.java"))  # Use integration's text-status Looper below.
    sources += [TESTS / "profile-config/bridge/wex/ui/core/feature/HostTargetResolver.java"]
    sources += sorted((TESTS / "profile-integration").rglob("*.java"))
    sources += [TESTS / path for path in (
        "database-capture/common/android/database/Cursor.java",
        "database-capture/common/android/content/pm/ApplicationInfo.java",
        "database-capture/common/dev/mindlink/xposed/XC_MethodHook.java",
        "database-capture/common/dev/mindlink/xposed/callbacks/XC_LoadPackage.java",
        "text-status/profile/wex/ui/core/feature/TextStatusBridge.java",
        "text-status/profile/wex/ui/core/feature/ConversationStorageBridge.java",
        "wex/ui/core/WexStore.java",
    )]
    sources += sorted((TESTS / "text-status/common/android").rglob("*.java"))
    sources += sorted((TESTS / "text-status/profile/android").rglob("*.java"))
    names = ("HostProfileBridge", "HostDatabaseBridge", "SelfProfile", "ReadOnlySql", "HostStageLog")
    sources += [PRODUCTION / (name + ".java") for name in names]
    before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    failures = []
    with tempfile.TemporaryDirectory(prefix="mindlink-profile-three-bridge-") as temporary:
        classes = Path(temporary) / "classes"
        classes.mkdir()
        print("Compile real HostProfileBridge + HostDatabaseBridge + SelfProfile in one isolated source set", flush=True)
        code = execute([str(javac), "-J-Duser.language=en", "-J-Duser.country=US", "-encoding", "UTF-8", "--release", "21",
                        "-sourcepath", str(TESTS / "profile-integration"), "-d", str(classes), *map(str, sources)])
        if code:
            return code
        runs = [(case, [str(args.pinned_apk)] if case == "pinned-selected-loader" else [])
                for case in CASES if case != "pinned-selected-loader" or args.pinned_apk]
        if args.pinned_apk:
            runs += [("pinned-early-construct-ready", [str(args.pinned_apk)]),
                     ("unpinned-early-skipped", [str(PRODUCTION / "ReadOnlySql.java")])]
        if args.case:
            runs = [(case, extra) for case, extra in runs if case in args.case]
        for case, extra in runs:
            code = execute([str(java), "-cp", str(classes), "wex.ui.core.feature.ProfileIntegrationChecks", case, *extra])
            if code:
                failures.append(f"{case} (exit {code})")
    changed = [path for path, digest in before.items() if hashlib.sha256(Path(path).read_bytes()).hexdigest() != digest]
    if changed:
        failures.append("source changed during run: " + ", ".join(changed))
    for name in names:
        path = PRODUCTION / (name + ".java")
        print(f"SHA256 {path}: {before[str(path)]}", flush=True)
    if failures:
        print("FAILED: " + "; ".join(failures), file=sys.stderr)
        return 1
    print(f"All {len(runs)} real three-bridge integration cases passed.")
    print("Not exercised: ART/Xposed interception, native DexKit, actual host initialization/device calls/data, avatar network/decoder or UI.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
