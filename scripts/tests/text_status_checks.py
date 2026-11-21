"""Compile real TextStatusBridge/SelfProfile against isolated, queued host doubles.

No production source is copied or patched. Each bridge case runs in a fresh JVM,
so real class-initializer poisoning and process-wide disablement remain observable.
"""

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / "scripts/tests/text-status"
BRIDGE_CASES = (
    "not-ready", "account-mismatch", "queued-read-revoked", "queued-open-revoked",
    "ready-read", "ordinary-throwable", "ordinary-error", "singleton-init-failure",
    "singleton-error", "open-throwable", "render-throwable", "listeners",
    "stale-token", "off-main-revoked", "background-rejected", "main-rejected", "main-throws",
)
PROFILE_CASES = ("not-confirmed", "query-confirmed", "account-revoked", "query-failure",
                 "io-rejected", "main-rejected", "queued-account-change", "stale-database-account",
                 "missing-optional", "nickname-error", "avatar-error", "optional-existing-account",
                 "optional-new-account", "optional-stale-result", "mandatory-failure-revokes",
                 "config-only-identity", "config-subscribed-refresh", "config-capture-confirmed",
                 "config-db-identity-mismatch", "config-database-stale", "config-preference-mismatch", "config-old-token",
                 "config-mandatory-failure", "config-read-failure", "config-empty-name-fallback",
                 "config-optional-error", "config-lost-database", "profile-listeners",
                 "profile-summary-status", "force-while-loading", "force-followup-bounded",
                 "force-failure-recovery", "force-generation-isolation", "force-old-failure-isolation",
                 "force-main-rejection-isolation", "force-main-throw-isolation", "self-read-ready-suppressed",
                 "listener-force-bounded", "config-empty-name-error-retained", "config-name-unknown-empty",
                 "profile-stage-booleans")


def execute(command):
    result = subprocess.run(command, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=90)
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
    return result.returncode


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--suite", choices=("all", "bridge", "profile"), default="all")
    parser.add_argument("--case", action="append", default=[], help="Run only selected cases (repeatable)")
    args = parser.parse_args()
    available = set(BRIDGE_CASES if args.suite == "bridge" else PROFILE_CASES if args.suite == "profile"
                    else BRIDGE_CASES + PROFILE_CASES)
    if set(args.case) - available:
        parser.error("--case contains an unknown case for the selected suite")
    suffix = ".exe" if (args.jdk / "bin/javac.exe").is_file() else ""
    javac = args.jdk / ("bin/javac" + suffix)
    java = args.jdk / ("bin/java" + suffix)
    if not javac.is_file() or not java.is_file():
        parser.error("--jdk must point to a JDK containing bin/javac and bin/java")
    common = sorted((TESTS / "common").rglob("*.java"))
    failures = []
    with tempfile.TemporaryDirectory(prefix="mindlink-text-status-") as temporary:
        for suite, production, entrypoint, cases in (
            ("bridge", "TextStatusBridge", "TextStatusChecks", BRIDGE_CASES),
            ("profile", "SelfProfile", "SelfProfileChecks", PROFILE_CASES),
        ):
            if args.suite not in ("all", suite):
                continue
            selected = [case for case in cases if not args.case or case in args.case]
            if not selected:
                continue
            classes = Path(temporary) / suite
            classes.mkdir()
            sources = common + sorted((TESTS / suite).rglob("*.java"))
            sources += [ROOT / "java_src/wex/ui/core/feature" / (production + ".java")]
            print(f"Compile real {production}.java ({suite} isolated source set)", flush=True)
            code = execute([str(javac), "-J-Duser.language=en", "-J-Duser.country=US",
                            "-encoding", "UTF-8", "-sourcepath", str(TESTS / suite),
                            "-d", str(classes), *map(str, sources)])
            if code:
                failures.append(f"{suite}: compilation (exit {code})")
                continue
            for case in selected:
                code = execute([str(java), "-cp", str(classes),
                                "wex.ui.core.feature." + entrypoint, case])
                if code:
                    failures.append(f"{suite}/{case} (exit {code})")
    if failures:
        print("FAILED: " + "; ".join(failures), file=sys.stderr)
        return 1
    print("All requested real-source text-status checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
