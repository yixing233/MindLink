"""Check shared Material3 ownership and compile/test platform-independent palette tokens."""
import argparse
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "ui-compose/sidepanel/src/main/java/dev/mindlink"


class SharedMaterial3Checks(unittest.TestCase):
    def test_all_entries_use_the_shared_theme(self):
        theme = (UI / "sidepanel/vendor/wekit/HomeSidePanelTheme.kt").read_text(encoding="utf-8")
        settings = (UI / "compose/SettingsScreen.kt").read_text(encoding="utf-8")
        desktop = (UI / "compose/DesktopUi.kt").read_text(encoding="utf-8")
        for source in (theme, settings, desktop):
            self.assertIn("MindLinkTheme", source)
            self.assertNotIn("lightColorScheme(", source)
            self.assertNotIn("darkColorScheme(", source)
            self.assertNotIn("Color(0x", source)

    def test_shared_components_are_real_material3(self):
        source = (UI / "ui/MindLinkComponents.kt").read_text(encoding="utf-8")
        for name in ("Card", "ListItem", "Switch", "Surface", "TooltipBox", "IconButton", "TopAppBar"):
            self.assertIn("androidx.compose.material3." + name, source)
        for file in ("compose/SettingsScreen.kt", "sidepanel/vendor/wekit/HomeSidePanelEditorPages.kt"):
            source = (UI / file).read_text(encoding="utf-8")
            self.assertIn("MindLinkSwitchRow(", source)
            self.assertIn("MindLinkCard(", source)

    def test_desktop_uses_one_payload_and_stays_outside_host_settings(self):
        feature = ROOT / "java_src/wex/ui/core/feature"
        bridge = (feature / "ComposePayloadBridge.java").read_text(encoding="utf-8")
        self.assertIn('"sidepanel-compose-payload","dev.mindlink.compose.DesktopUi"', bridge)
        self.assertIn("Application.getProcessName()", bridge)
        desktop = (UI / "compose/DesktopUi.kt").read_text(encoding="utf-8")
        for name in ("SettingsPanelModel", "WexStore", "SettingsChanges", "killProcess"):
            self.assertNotIn(name, desktop)
        self.assertIn("SettingsUi.createContent(", desktop)
        self.assertIn("SettingsUi.lifecycle(view, resumed)", desktop)
        self.assertIn("navigation.remove(view)", desktop)
        self.assertFalse((feature / "DesktopPageView.java").exists())

    def test_both_builds_compile_the_same_palette(self):
        self.assertIn("../../shared-ui/src/main/java", (ROOT / "ui-compose/sidepanel/build.gradle.kts").read_text())
        self.assertIn("shared-ui/src/main/java", (ROOT / "scripts/build.sh").read_text(encoding="utf-8"))
        self.assertIn("shared-ui\\src\\main\\java", (ROOT / "scripts/build.ps1").read_text(encoding="utf-8"))
        self.assertIn("new Palette(MindLinkColors.LIGHT)", (ROOT / "java_src/wex/ui/core/feature/SettingsTheme.java").read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, default=Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SharedMaterial3Checks))
    if not result.wasSuccessful():
        return 1
    if args.jdk is None:
        parser.error("--jdk or JAVA_HOME is required for palette behavior tests")
    with tempfile.TemporaryDirectory(prefix="mindlink-material3-") as directory:
        sources = sorted((ROOT / "shared-ui/src/main/java").rglob("*.java"))
        sources += sorted((ROOT / "scripts/tests/material3").rglob("*.java"))
        subprocess.run([str(args.jdk / "bin/javac.exe"), "-d", directory, *map(str, sources)], check=True)
        subprocess.run([str(args.jdk / "bin/java.exe"), "-cp", directory, "dev.mindlink.ui.MindLinkColorsChecks"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
