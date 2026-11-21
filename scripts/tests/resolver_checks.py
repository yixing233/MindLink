"""Run real resolver/ready-listener isolation checks without Android or native DexKit.

Usage: python -I scripts/tests/resolver_checks.py --jdk <JDK root>
Only the two host bridges and ReadOnlySql production sources are compiled. The
success case uses the real fingerprint/cache/descriptor path with fake metadata;
this suite does not exercise native loading or DexKit matcher/query behavior.
"""

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--case", choices=("isolation", "runtime-loader"), default="isolation")
    args = parser.parse_args()
    executable = ".exe" if sys.platform == "win32" else ""
    with tempfile.TemporaryDirectory(prefix="mindlink-resolver-") as temporary:
        sources = sorted((ROOT / "scripts/tests/resolver").rglob("*.java"))
        sources += [ROOT / relative for relative in [
            "scripts/tests/navigation-hooks/de/robv/android/xposed/XC_MethodHook.java",
            "scripts/tests/navigation-hooks/de/robv/android/xposed/callbacks/XC_LoadPackage.java",
            "scripts/tests/menu/android/os/Build.java",
            "scripts/tests/wex/ui/core/WexStore.java",
        ]]
        sources += [ROOT / "java_src/wex/ui/core/feature" / (name + ".java")
                    for name in ["HostTargetResolver", "HostDatabaseBridge", "ReadOnlySql"]]
        commands = [
            [str(args.jdk / ("bin/javac" + executable)), "-J-Duser.language=en", "-J-Duser.country=US",
             "-encoding", "UTF-8", "-d", temporary, *map(str, sources)],
            [str(args.jdk / ("bin/java" + executable)), "-cp", temporary,
             "wex.ui.core.feature.HostTargetResolverChecks", *([] if args.case == "isolation" else [args.case])],
        ]
        for command in commands:
            result = subprocess.run(command, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=60)
            print(result.stdout + result.stderr, end="")
            if result.returncode:
                return result.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
