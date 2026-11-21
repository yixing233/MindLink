# Functional MIUIX desktop candidate — 2026-11-14

## User-reported defect

The installed UI rendered `IntegrationPage`: a static Column with an empty click callback.
It had no useful navigation or scrollable list. Component-loading verification was not a usable
settings/about UI and is no longer the runtime entry.

## Changes

- `DesktopPages`: actual MIUIX theme/scaffold/top bar/cards/arrow preferences and LazyColumn pages.
- Settings → functional entry guide, diagnostics, local logs, About, privacy and credits.
- About → own MindLink icon/version, device information, GPL license, repository and notices.
- Platform back callback plus view-owned Kotlin history; Bundle history and Compose saveable list states.
- Reuses platform-only ModuleDashboardState and LogPreview; no host WexStore or hook initialization.
- Real HyperChanger progressive Kyant Backdrop toolbar and AGSL material About background,
  adapted palette/lifecycle with attribution and Apache-2.0 text packaged.
- Per-page asynchronous local document/log loading; leaving the page cancels publication.
- Empty IntegrationPage retired; compiled candidate checked for its absence.

## Preserved installed baseline

APK SHA-256: `ed9559aebf8ff83632f3d7461b53dfdcef9767ccaa19a35f35c1ceab2c9a38bc`.
Saved at `build/compose-functional-20261114/installed-baseline.apk`.

The desktop candidate changes only `assets/compose-payload/*` and permitted local notices.
All other entries, including both root DEX, host fixes, manifest, resource table/files, native libraries
and profile, are byte-identical to this installed baseline. It does not merge unrelated live source work.

## Candidate

- Path: `build/compose-functional-20261114/MindLink-functional-ui.apk`.
- Size: 17,538,066 bytes (about 16.73 MiB), previously 33,483,718 bytes (31.93 MiB).
- SHA-256: `2eaec05f3cc4ab98b04711e1bed652d1ace9c305fa5fd6a61599d62acc145ccf`.
- Original signing certificate, v3, zipalign, minSdk 33, unique launcher, ZIP/DEX/payload integrity pass.
- Compression is ZIP deflate of built-in payload assets. AssetManager provides decompressed bytes;
  the existing SHA-256 verification and InMemoryDexClassLoader still operate on those bytes.
- No R8 shrinking yet; package size is reported rather than promised.

## Verification

- Actual MIUIX/Compose Kotlin compilation and AAR checks passed in an isolated build directory.
- Full existing regression passed; not an ART/visual claim.
- Actual compiled Kotlin DesktopNavigation executed with Bundle/Parcelable/Looper contract doubles:
  13 navigation/history/saved-state assertions passed. This is not actual Android rendering.
- Verification report: `build/compose-functional-20261114/verify-final/verification.txt`.
- Device click/scroll/back/About material appearance: **pending explicit installation approval**.

After approval, install exact candidate; operate only MindLink. Verify positive `functional-ui-created`
log for the current process, page navigation, actual scroll offsets, system/UI back, local document
rendering and reentry. Do not open chats, send messages, test payments, clear data/logs or publish captures.
