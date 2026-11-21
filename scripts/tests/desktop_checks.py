"""Execute desktop page behavior against strict local Android test doubles."""

import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

sys.stdout.reconfigure(encoding="utf-8",errors="replace")

ROOT = Path(__file__).resolve().parents[2]


def checks(jdk, output):
    output.mkdir(parents=True, exist_ok=True)
    classes = output / "classes"
    classes.mkdir(exist_ok=True)
    names = ["PlusMenuExtra", "SettingsTheme", "AboutPageBuilder", "ComposePayloadBridge", "ComposePayloadLoader"]
    sources = [ROOT / "java_src/wex/ui/core/feature" / (name + ".java") for name in names]
    sources += sorted((ROOT / "scripts/tests/menu").rglob("*.java"))
    sources += [source for source in sorted((ROOT / "scripts/tests/compose-boundary").rglob("*.java"))
                if source.name != "ApplicationInfo.java"]
    sources += [ROOT / "scripts/tests/dashboard/wex/ui/core/WexStore.java",
                ROOT / "scripts/tests/dashboard/wex/ui/core/feature/FeatureDiagnostics.java",
                ROOT / "scripts/tests/dashboard/wex/ui/core/feature/ImagePicker.java"]
    compile_result = subprocess.run([str(jdk / "bin/javac.exe"), "-J-Duser.language=en", "-J-Duser.country=US", "-encoding", "UTF-8", "-d", str(classes),
                                     *map(str, sources)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    (output / "compile.txt").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
    if compile_result.returncode:
        print(compile_result.stdout + compile_result.stderr)
        return compile_result.returncode
    result = subprocess.run([str(jdk / "bin/java.exe"), "-cp", str(classes),
                             "wex.ui.core.feature.ComposeBoundaryChecks", str(output)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    (output / "behavior.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output is not None:
        return checks(args.jdk, args.output)
    with tempfile.TemporaryDirectory(prefix="mindlink-desktop-") as temporary:
        return checks(args.jdk, Path(temporary))


if __name__ == "__main__":
    raise SystemExit(main())
