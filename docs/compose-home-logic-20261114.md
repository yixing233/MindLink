# Desktop home / environment / system-bar correction — 2026-11-14

## Scope and exact candidate

This task responds to the user's page-logic feedback: duplicate actions, a homepage misnamed Settings,
defensive product text, fabricated unknown activation and light-themed UI with white status-bar glyphs.
Only the isolated desktop payload is replaced. No WeChat/side-panel source, host hook, Activity smali,
manifest, resource, user configuration or log is modified by this task. No commit/push or public upload.

- Candidate: `build/compose-logic-20261114/MindLink-home-logic.apk`
- SHA-256: `1de1e72fa291374b51e6cfc1a80a5e4960f17a135dccf6c1ed3893db42186f43`
- Size: 17,574,930 bytes (16.76 MiB), +4,096 bytes versus the installed baseline.
- Baseline: `build/compose-logic-20261114/installed-baseline.apk`, pulled from the current connected device.
- Baseline SHA-256: `0b3b5803bba2add313986f0bc9f7472ad7334f8c98cbb80109d4647a8f3465eb`
- Signing certificate SHA-256: `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`
- Installation: explicitly authorized by the user's “安装吧”, completed and hash-verified on 2026-11-14. Real visual acceptance remains pending.

## Page responsibilities

- Homepage title is **MindLink**; the internal Settings history key remains stable.
- First card describes available product capabilities, not activation disclaimers: interface enhancement,
  floating navigation, chat bubbles, chat toolbar and conversation groups. It does not claim every feature
  is currently enabled.
- Home has exactly three different destinations: Open WeChat (with `+ → Mdl 设置` path in its summary),
  Status and environment, About. No second launcher/guide route.
- The retired Guide enum and UI route are removed; old saved Guide history safely resets to the root.
- Only the environment page owns View logs / Copy redacted diagnostic. No duplicated tool buttons on
  home or inside the log viewer. Log text remains nonselectable, local, bounded and unshared.
- About owns project/repository/license/privacy/credits. Its project introduction is not a second
  repository link, and its repeated device/Android/host-version/compatibility card is removed.
- Prior MIUIX components, real document rendering, linear icons, logical page transitions, saved scroll,
  single overscroll and About material/backdrop path are retained.

## Environment data

`DesktopEnvironmentReader` queries actual PackageManager/Build facts and bounded allowlisted config
counts. It never invokes the old stateTitle/stateDetail/activation summary, WexStore or host bootstrap.

- Module version comes from the installed app PackageInfo, not the UI-build host's version.
- Host version, Android/API, device, log timestamp and saved enabled toggle count are distinct facts.
- Per-value states distinguish Available, Missing, Restricted and Failed. A read failure is not 0, normal
  or a blanket unknown. Metadata reads use Files.readAttributes to distinguish missing/permission errors.
- Log timestamps cannot prove Xposed activation. No static activation line or log-based overall status.
  This task does not invent a cross-process activation handshake or use private framework APIs.
- Config opens remain OPEN_READONLY | NO_LOCALIZED_COLLATORS with a no-op corruption callback. No file
  creation, corruption cleanup or migration; invalid values produce a format error without leaking them.
- Snapshots read on Dispatchers.IO, publish atomically, refresh on environment entry and foreground
  resume, and keep the last completed snapshot while refreshing. Diagnostic copying is disabled until
  the refreshed snapshot completes. Page/lifecycle changes cancel obsolete UI publication.
- Diagnostics contain only these environment facts/read results, not identities, paths, log body or
  unmeasured activation/compatibility conclusions.

## System bars

`DesktopSystemBars` binds once inside the resolved MIUIX theme, outside the AnimatedContent page layers.

- Resolved light surface sets APPEARANCE_LIGHT_STATUS_BARS and LIGHT_NAVIGATION_BARS (dark glyphs).
- Resolved dark surface clears only these bits (light glyphs).
- Masked platform WindowInsetsController writes preserve other appearance bits.
- Successful themed composition, resume and attachment apply the latest theme. Disposal removes the
  lifecycle observer and attachment listener. Destroyed/finishing/disposed bindings do not write.
- Existing transparent backgrounds, edge-to-edge/insets/contrast policy are untouched. No legacy
  AndroidX API or root DEX/smali modification is needed; no new runtime dependency is introduced.

## Validation

- Actual release Kotlin/Compose/Java compilation succeeded.
- Initial build failed because inserted Kotlin string delimiters were typographic quotes; corrected to
  code quotes and rebuilt successfully. No old package was installed after that failed build.
- Actual compiled Kotlin logic: 69 assertions passed (menu ownership, refresh policy, environment reader,
  masked system-bar binding/lifecycle). Android/database contract doubles do not prove rendered visuals.
- Actual compiled Kotlin navigation/history/legacy migration: 34 assertions passed.
- Actual document parser: 74 assertions passed.
- Production desktop loader/process/integrity boundary: 13 assertions passed; JVM does not execute DEX.
- All 371 original resource IDs and 80 pinned runtime artifact hashes passed.
- Full current working-tree regression completed with exit code 0. Earlier side-panel fixture failure
  from the prior candidate is historical; this run is green, without editing parallel side-panel code.
- Final candidate signature/v3, launcher, min API 33, alignment, DEX class uniqueness and asset index
  hashes passed. New environment/menu/system-bar classes are present.

Only changed APK entries:

1. `assets/compose-payload/classes2.bin`
2. `assets/compose-payload/index.txt`

All other installed-baseline entries are byte-identical, including root DEX, host/side-panel payloads,
manifest, res/resources.arsc, JNI, baseline profiles, original configuration assets and all notice/legal
assets. Root DEX remains ZIP_STORED; payload is transparently decompressed and hash-verified by the loader.
Resources/notice staging and candidate output are independent; shared APK outputs are not overwritten.

Evidence: `build/compose-logic-20261114/{compile-final,logic,router,document,boundary,resources,regression}.txt`
and `build/compose-logic-20261114/verify/verification.txt`.

## Device installation verified; visual acceptance pending

After the user's explicit “安装吧”, the current APK was pulled again and its SHA-256 still matched
`0b3b5803bba2add313986f0bc9f7472ad7334f8c98cbb80109d4647a8f3465eb`; no newer parallel build was replaced.

- `adb install -r`: Success; data, pictures and logs retained.
- Cold start `am start -W`: Status ok, 2060 ms.
- Post-install APK pulled and hash-verified as
  `1de1e72fa291374b51e6cfc1a80a5e4960f17a135dccf6c1ed3893db42186f43`.
- Positive load: `2026-11-14 16:54:13.451`, PID 31114,
  `functional-ui-created runtime=isolated page=Settings` (stable internal root key).
- Same-PID logs record environment/About entry and return; no AndroidRuntime error appeared in this
  filtered read. Foreground check shows MindLink. This is not a complete stability/visual verdict.
- No automatic touches, screenshots, system-theme changes, link taps or WeChat operations performed.
- Evidence: `build/compose-logic-20261114/postinstall-2y07kk0y/installation-verification.txt`.

**Actual page layout, environment values, system-bar glyph colors and earlier visual effects remain
pending user/device acceptance.** Installed hash and positive loading establish the new UI is running,
not that all visual requirements are satisfied.

Remaining MindLink-only checks:
- Installed APK hash and positive isolated UI loading.
- Homepage title/capability card/three unique entries, retired guide absence, About/environment separation.
- Actual installed version/device values and precise storage read results; no fabricated activation row.
- Light theme black status glyphs; dark theme white glyphs, including resume/recreation/navigation.
- Existing animation, real ordinary/About blur, scroll restoration and rebound are not regressed.

Do not automatically change system dark-mode preferences, open/restart WeChat, send messages, use payment
or capture host UI. Local screenshot scope must be allowed and foreground-guarded. Do not clear
configuration/images/logs or publish artifacts without separate authorization.
