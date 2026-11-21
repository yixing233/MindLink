"""Run the existing desktop loader safety assertions after the shared-loader extraction.

This does not execute DEX or validate Android rendering; the existing test loader refuses that.
"""
import argparse
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    repo = args.repo.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    classes = args.output / "classes"
    classes.mkdir(exist_ok=True)
    sources = [repo / "java_src/wex/ui/core/feature" / (name + ".java") for name in
               ["PlusMenuExtra", "SettingsTheme", "AboutPageBuilder", "ComposePayloadBridge", "ComposePayloadLoader"]]
    sources += sorted((repo / "shared-ui/src/main/java").rglob("*.java"))
    sources += sorted((repo / "scripts/tests/menu").rglob("*.java"))
    sources += [p for p in sorted((repo / "scripts/tests/compose-boundary").rglob("*.java")) if p.name != "ApplicationInfo.java"]
    sources += [repo / "scripts/tests/dashboard/wex/ui/core/WexStore.java",
                repo / "scripts/tests/dashboard/wex/ui/core/feature/FeatureDiagnostics.java",
                repo / "scripts/tests/dashboard/wex/ui/core/feature/ImagePicker.java"]
    result = subprocess.run([str(args.jdk / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    (args.output / "compile.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr)
    if result.returncode:
        raise SystemExit(result.returncode)
    result = subprocess.run([str(args.jdk / "bin/java.exe"), "-cp", str(classes),
                             "wex.ui.core.feature.ComposeBoundaryChecks", str(args.output)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    (args.output / "behavior.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
