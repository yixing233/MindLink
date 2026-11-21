# Desktop Material3 Migration (2026-11-20)

## Scope

The desktop Compose payload moved from MIUIX to Material3 without changing the
host Java/smali, desktop navigation, settings actions, document parser, isolated
classloader, or side-panel payload. The four page glyphs are local ImageVectors,
so no full icon catalog was added.

Desktop and side panel now use the same dependency generation:

- Android Gradle Plugin 9.3.2 / Gradle 9.7.1
- AndroidX Material3 1.5.0-alpha28
- Compose Foundation/UI/Animation 1.13.0-alpha01
- Lifecycle 2.10.0, SavedState 1.4.0
- Kotlin/Compose compiler 2.3.21

Release payloads enable R8 code shrinking. The reflective `DesktopUi` and
`SidePanelUi` entry points, `androidx.compose.runtime.Composer`, and `kotlin.Unit`
are explicitly kept by name. Resource shrinking stays disabled because both
payloads share one linked resource table.

AGP 9 runs with the existing `android.builtInKotlin=false`, `android.newDsl=false`
and generated-SourceSet compatibility settings. The desktop and side panel remain
separate boot-parent InMemoryDexClassLoader roots.

## Packaging

Both hash-indexed payload `.bin` assets are now DEFLATED. `AssetManager.open`
decompresses them before the loader verifies SHA-256 and constructs the classloader.
This changes only ZIP compression; payload decompressed bytes and hashes remain
unchanged. Root `.dex` entries stay STORED.

The full merge gate now recognizes the six WeChat tips-bar view IDs that are
intentional literals in module `findViewById` paths. The whitelist is exact;
unknown missing `0x7f...` constants still fail.

## Validation

- Desktop actual Release Kotlin compile, R8, and APK assembly passed.
- Desktop document parser: 74 checks.
- Desktop source/logic + compiled behavior: 69 assertions.
- Navigation and saved-state state machine: 34 assertions.
- Compose payload/process boundary: 13 assertions.
- Side panel host/schema/data checks: all existing cases passed.
- Full regression exit code 0:
  `build/release-20261120-md3-r8-003/regression.log`.
- Independent APK verification passed ZIP/CRC, primary/secondary DEX class
  membership, payload SHA-256, isolated entry points/runtime presence and side-panel
  presence, original repository certificate/v3, zipalign and launcher.
- APK checks for a full apktool rebuild use the explicit
  `--allow-primary-rebuild` mode; primary class membership is still required to
  match exactly.

## Artifacts

Repository-certified R8 Release:

- `build/release-20261120-md3-r8-003/MindLink-1.0.apk`
- SHA-256 `a8519ef7736543c146509957489b7c568941d5580c98d35c268ebbb0eb39a97b`
- 13,818,027 bytes; 13.18 MiB
- Certificate `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`

Device-key installation variant with identical unsigned content:

- `build/release-20261120-md3-r8-003/MindLink-1.0-phone-key.apk`
- SHA-256 `4c81a2b24aed6229dd73250971c05090c5fb9b175748e0ba67819b81c7c5505a`
- 13,818,019 bytes
- Certificate `77be300524d8817c0d4734458f6cb0d9a6a9987521bcdaf2e121f7bb0ec1b3fc`

Both APKs contain two compressed R8 Material3 payloads of roughly 1 MB each.
No installation or device visual acceptance was performed for this release.
