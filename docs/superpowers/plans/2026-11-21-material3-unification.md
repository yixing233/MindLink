# Material3 UI Unification Implementation Plan

**Goal:** Use real Material3 components and one theme for the drawer, injected settings and launcher UI, without changing settings storage or WeChat hooks.

**Architecture:** Compile the same platform-independent palette source into the Java feature DEX and the existing isolated Compose payload. Share Material3 surfaces, rows and typography. The launcher loads `DesktopUi` from the same payload as `SettingsUi` and `SidePanelUi`; each entry retains a separate lifecycle and process boundary.

**Tech Stack:** Java 11, Kotlin, AndroidX Compose Material3, existing Gradle/apktool packaging.

## Tasks

- [x] Add shared palette and Material3 theme/components; remove page-local palettes.
- [x] Use shared cards and switch rows in side-panel editing and settings; retain callbacks, card IDs and saved state.
- [x] Replace launcher Java UI with `DesktopUi` in the shared payload; preserve process guard, back navigation and lifecycle.
- [x] Connect shared sources to Java and Compose builds and tests; retain provenance for modified vendored components.
- [x] Run palette/component checks, desktop boundary tests, settings system-bar tests, Kotlin compilation, lint and APK packaging where tools are available.

## Verification

```powershell
python -I scripts/tests/material3_checks.py
python -I scripts/tests/desktop_checks.py --jdk "$env:JAVA_HOME"
python -I scripts/tests/settings_systembar_checks.py --jdk "$env:JAVA_HOME" --out build/material3-systembars
```

The three entries must share `MindLinkTheme`; the launcher must not read WeChat configuration or load a second Compose runtime. Existing 371 public resource IDs remain stable. Signing credentials and local device artifacts stay outside source control.

Verified 2026-11-21: all three checks pass; sidepanel `assembleRelease` + `lintVitalRelease` succeed; unsigned APK at `build/material3-final-3/MindLink-1.0-unsigned.apk` (19.09 MiB, 16,642 payload classes, one shared `sidepanel-compose-payload`).

Final device verification on 2026-11-21: `build/viewport-fix-4/MindLink-1.0.apk` was signed, alignment-checked and installed over the existing module. Conversation and contact lists extend through the gesture navigation area. Chat messages remain above the input card in default, IME, emoji and More states and return to the original position when the panel closes. The footer regression suites pass 701 assertions, including history-position preservation; the host-loaded tail-decoration bridge check also passes.
