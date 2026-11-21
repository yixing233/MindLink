"""Execute native bottom-strip ownership tests with isolated host view doubles."""

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
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="mindlink-frosted-") as temporary:
        sources = sorted((ROOT / "scripts/tests/menu").rglob("*.java"))
        sources += sorted((ROOT / "scripts/tests/navigation").rglob("*.java"))
        sources += [ROOT / "java_src/wex/ui/core/feature/NavigationFrostedController.java",
                    ROOT / "java_src/wex/ui/core/feature/PlusMenuExtra.java",
                    ROOT / "java_src/wex/ui/core/feature/SettingsTheme.java"]
        commands = [[str(args.jdk / "bin/javac.exe"), "-J-Duser.language=en", "-J-Duser.country=US",
                     "-encoding", "UTF-8", "-d", temporary, *map(str, sources)],
                    [str(args.jdk / "bin/java.exe"), "-cp", temporary, "wex.ui.core.feature.NavigationFrostedChecks"]]
        for command in commands:
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
            print(result.stdout + result.stderr)
            if result.returncode:
                return result.returncode
        hook_overrides = sorted((ROOT / "scripts/tests/navigation-hooks").rglob("*.java"))
        overridden = {source.relative_to(ROOT / "scripts/tests/navigation-hooks") for source in hook_overrides}
        hook_sources = [source for source in (ROOT / "scripts/tests/menu").rglob("*.java")
                        if source.relative_to(ROOT / "scripts/tests/menu") not in overridden]
        hook_sources += hook_overrides
        hook_sources += [ROOT / "scripts/tests/navigation/com/tencent/mm/pluginsdk/ui/chat/ChattingUILayout.java"]
        hook_sources += [ROOT / "java_src/wex/ui/core/feature" / (name + ".java") for name in
                         ["NavigationFrostedController", "NavigationBarFeature", "NavigationBarViews", "NativeTabBridge",
                          "NavigationBottomSpace", "NavigationContactViewport", "NavigationTabModel", "NavigationScrollState", "LiquidGlassMotion", "ChatToolbarLifecycle", "PlusMenuExtra", "SettingsTheme"]]
        hook_classes = Path(temporary) / "hooks"
        hook_classes.mkdir()
        commands = [[str(args.jdk / "bin/javac.exe"), "-J-Duser.language=en", "-J-Duser.country=US",
                     "-encoding", "UTF-8", "-d", str(hook_classes), *map(str, hook_sources)],
                    [str(args.jdk / "bin/java.exe"), "-cp", str(hook_classes), "wex.ui.core.feature.NavigationHookChecks"]]
        for command in commands:
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
            print(result.stdout + result.stderr)
            if result.returncode:
                return result.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
