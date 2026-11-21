# Real Material3/Compose desktop UI

The Java imitation pages and static IntegrationPage are no longer the desktop entry.
DesktopUi renders an actual Material3 MindLink homepage, About, environment, local log and declaration pages.
Home owns three distinct entries; environment owns diagnostic tools, while About owns project/legal pages.
Resolved Material3 theme controls system-bar glyph appearance. Environment reads refresh in the background
on entry/resume, with per-item missing/restricted/failure results instead of fabricated activation status.
Device visual/interaction acceptance is still separate from compilation; see
`../docs/compose-home-logic-20261114.md` for the current page/system-bar candidate, and
`../docs/compose-polish-ui-20261114.md` for earlier navigation/document polish.
The earlier functional desktop baseline is recorded in `../docs/compose-functional-ui-20261114.md`.

- Forward/backward page transitions use actual Compose AnimatedContent, with logical history,
  input locking through completion, RTL mirroring and saved per-page list state.
- Documents render as Material3 Text/Compose lazy blocks (a bounded Markdown subset, not CommonMark).
  GPL/Apache text is literal; logs remain nonselectable and unshared.
- Credits has a concise landing list with complete local declaration/license subpages.
- Ordinary page backdrop records an opaque themed background, while About retains its AGSL material.
  Lists use the standard Compose LazyColumn overscroll path.
- Compilation/JVM tests cannot establish actual Android animation, blur or rebound acceptance.

## Build and dependency ownership

- UI-only Gradle module: `ui-compose/app`; original hooks remain Java/smali/apktool.
- Toolchain: Gradle 9.7.1, AGP 9.3.2, Kotlin/Compose compiler plugin 2.3.21, JDK 21.
  AGP warns that compileSdk 37 is newer than its tested SDK 36.1; this warning is not suppressed.
- Material3 baseline: JetBrains Compose Material3 1.9.0, resolving AndroidX
  Material3 1.4.0 and Compose 1.11.1. Backdrop 2.0.0 and Shapes 1.2.0 remain.
- The four desktop page glyphs are small local ImageVectors; no icon catalog is bundled.
- `app/gradle.lockfile` locks the resolved graph. `runtime-checksums.json` records
  the 72 resolved runtime artifacts; the main build checks these exact coordinates/bytes.
- A coherent isolated UI runtime is stored in APK assets as hash-indexed `.bin` DEX.
  `InMemoryDexClassLoader` has an Android platform parent, not the legacy app classloader.
  The desktop bridge exposes only Android platform types.
- Both payloads enable R8 code shrinking and stay in separate classloaders. The
  string-loaded UI entries and Compose runtime marker classes are explicitly kept.
- Final payload `.bin` assets are DEFLATED; integrity is checked after decompression.
- No code is fetched or loaded from external storage. Payload is built and signed with the APK.
  The bridge explicitly rejects the WeChat process.

## Resources and old runtime preservation

- All 371 existing `public.xml` IDs verified unchanged after final resource linking.
- Four duplicate enum bodies are removed only in a newly generated resource staging tree;
  original apktool resources are not rewritten.
- One final linked `resources.arsc` is selected; independent tables are never concatenated.
- Original smali runtime remains; standard Compose classes are not mixed with its obfuscated ABI.
- Selected graphics-path JNI is byte-identical to the old library for all four ABIs.
- Original xposed entry, DexKit library, manifest permissions and baseline profile remain.
  UI baseline profile is not overlaid onto the old runtime.
- Desktop Activity is platform `android.app.Activity`; isolated Compose owns its own standard
  Lifecycle/SavedState/ViewModelStore. No old obfuscated owner object crosses the boundary.

## Earlier integration gate result (superseded by functional desktop candidate)

- Real Material3 desktop screens compile and package.
- Resource IDs, dependency hashes, original signing certificate, unique launcher, DEX checksums,
  isolated payload class definitions and within-payload uniqueness pass local checks.
- Loader safety boundary passes JVM tests; the test double explicitly cannot execute DEX.
- Existing local regression passed. This does not validate Compose rendering or Android lifecycle.
- No installation approval was received for **this exact historical gate** in this session.
  A later installed host candidate demonstrated the same component payload could render;
  the functional pages then replaced the empty screen. This section does not describe current UI.

## Historical integration gate candidate (not the shared build path's current hash)

- `build/MindLink-1.0.apk`: 33,455,046 bytes (about 31.91 MiB).
- SHA-256: `3a0a79474c66ceb028fd4b874407dfd21ffba15bcb99b4d4ae248d5e06529989`.
- Certificate SHA-256: `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`.
- Validation: `build/verify-compose-gate-20261114/verification.txt`.

## Device gate after explicit approval

Install the exact verified candidate without clearing data; launch MindLink only.
Check actual Material3 controls, classloader/owner resolution, cold start, pause/resume,
back/reentry and no resource/verification/class-cast exceptions. Capture only module
UI if authorized. Do not restart WeChat, send messages, test payments, clear logs or
publish screenshots. Installation success alone is not an integration pass.
