# Desktop navigation/document polish — 2026-11-14

## Scope and candidate

Only the isolated desktop UI is changed. No WeChat feature, source migration, configuration write,
user picture/log deletion, public upload or commit/push is performed by this task.
The exact candidate was subsequently installed after separate per-candidate approval, as recorded below.

- Candidate: `build/compose-polish-20261114/MindLink-polished-ui.apk`
- SHA-256: `0b3b5803bba2add313986f0bc9f7472ad7334f8c98cbb80109d4647a8f3465eb`
- Size: 17,570,834 bytes (16.76 MiB), +32,768 bytes versus the functional baseline.
- Baseline: `build/compose-functional-20261114/MindLink-functional-ui.apk`
- Baseline SHA-256: `2eaec05f3cc4ab98b04711e1bed652d1ace9c305fa5fd6a61599d62acc145ccf`
- Baseline was previously installed and pulled/hash-verified in this session. At candidate preparation,
  `adb devices -l` had no online device. This is the last verified installed baseline, not a fresh claim
  about the phone's present version. Reconnect and compare the current installed APK before installation;
  if it changed, make a new desktop-only candidate preserving that version, rather than overwrite host fixes.
- Signature certificate SHA-256: `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`.

## Implemented

- Logical forward/backward navigation and atomic page/direction/sequence presentation. Real Compose
  AnimatedContent performs short horizontal/fade/scale transitions, reversed on return and mirrored in RTL.
  System-disabled animation changes immediately. No predictive-back gesture-progress claim is made.
- Outgoing and incoming page trees use their displayed page, independent backdrop and stable saveable key.
  Inputs, preferences and link actions are gated until the transition really settles; additional system
  back is consumed through a root pop. Preference colors do not flash disabled during the transition.
- MIUIX top-app-bar state and lazy-list state remain inside each SaveableStateProvider. Document loading
  does not mount a temporary one-row lazy list that would clamp a saved long-document scroll offset.
- Ordinary recorder has an opaque themed background **inside** layerBackdrop. Transparent TopAppBar and
  progressive real Backdrop blur remain, with About's existing AGSL material path preserved.
- Lists use actual MIUIX overScrollVertical outside the top-bar nested-scroll connection. Default
  LazyColumn overscrollEffect is null: there is one rebound path, no handwritten extra spring.
- Privacy/About/license/credit entries use actual MIUIX Regular Lock/Info/File vectors. Privacy section
  matching normalizes its heading instead of relying on an emoji; rendered heading decoration is removed.
- Bounded native Markdown subset: headings, paragraphs, nested list entries, quotes, code, emphasis,
  inline code, HTTPS links and stacked narrow-screen table cells. This is not a full CommonMark renderer.
  Unknown syntax remains literal. Code/paragraphs are real MIUIX Text with Compose layouts/AnnotatedString,
  not one Text with Markdown markers stripped. No WebView, rendering network call or new dependency.
- HTTPS-only native link callbacks require a real tap and active page; credentials, invalid hosts,
  control/whitespace/backslash URLs and non-HTTPS schemes do not launch. Relative license references
  remain noninteractive; full license routes are separate local entries.
- GPL and Apache full legal content stays literal, with original numbering and symbols. Logs remain
  local/nonselectable, with original 64 KiB/200-line reader limits and only redacted diagnostic copying.
- Credits landing is concise (component, license, one-line purpose). Full declarations and GPL/Apache
  licenses are secondary pages. Existing author/source mappings are retained; verified HyperChanger
  historical NOTICE attribution is added. No unverified MIT license body was invented.

## Isolation and package verification

Build output and notice staging are independent under `build/compose-polish-20261114/`. The candidate
uses a frozen notice snapshot of the verified baseline plus desktop attribution additions. Parallel
unshipped side-panel declarations are not silently shipped as claims about code absent from this APK.
Current repository notices retain parallel work; they are not rolled back to the old snapshot.
`mdlNoticeStage` is an optional build input; normal builds still read `assets/mindlink`.

Changed APK entries, and only these:

1. `assets/compose-payload/classes2.bin`
2. `assets/compose-payload/index.txt`
3. `assets/mindlink/THIRD-PARTY-NOTICES.md`

All other baseline APK entry bytes are unchanged, including primary/host DEX, manifest, resource table,
all resource entries, native libraries, profile and original configuration/assets. Original root DEX
remains ZIP_STORED. In-memory UI loader sees decompressed hash-verified payload bytes; runtime not mixed
with the legacy ABI. No R8/minification or new runtime dependency was added.

## Validation results

- Actual release Kotlin/Compose/Java compilation succeeded, including renderer, gestures and transitions.
- Actual compiled Kotlin navigation: 28 assertions passed (direction, ancestor return, rapid input lock,
  stale settle, root pop back consumption, history restoration, no-animation mode).
- Actual compiled pure Kotlin document model: 74 assertions passed (syntax, semantics, tables, unsafe
  links, unknown fallbacks, literal legal content and heading normalization).
- Production platform desktop bridge: 13 process/integrity/fail-closed boundary assertions passed.
  JVM loader double deliberately cannot execute Android DEX.
- All 371 original public resource IDs and 80 pinned runtime artifacts passed linked/hash checks.
- Final APK: same signing certificate, v3 signature, launcher, minimum API 33, alignment, unique DEX
  definitions and payload SHA-256 verified. New document/credit/navigation classes present; empty
  IntegrationPage and WexStore absent from isolated payload.
- Verification: `build/compose-polish-20261114/verify/verification.txt`.

**Full working-tree regression did not pass.** The run reached the concurrently changing side-panel
suite and failed compiling its real `HomeSidePanelSnapshot.java`: missing test dependencies `Canvas`,
`BitmapDrawable`, `NativeStatusIcon`, `ImageResult` (6 errors). Desktop boundary, earlier runtime suites
and package checks passed; this is not reported as an all-green repository. No parallel side-panel
source/test dependency was altered to hide this result. Full output: `build/compose-polish-20261114/regression.txt`.

AGP 8.13.2 still warns compileSdk 37 is newer than its tested SDK 36.1; graphics-path native stripping
is unavailable, so original library bytes are packaged. Neither warning is concealed.

## Device installation verified; visual acceptance pending

After the user provided `192.168.1.148:35589`, wireless ADB connected and the current installed APK was
pulled: SHA-256 still matched `2eaec05f3cc4ab98b04711e1bed652d1ace9c305fa5fd6a61599d62acc145ccf`.
The user then selected the explicit install-and-check option for this exact candidate.

- `adb install -r`: Success; no uninstall, data clearing or log clearing.
- Cold start `am start -W`: Status ok, 824 ms.
- APK pulled after install: exact approved SHA-256
  `0b3b5803bba2add313986f0bc9f7472ad7334f8c98cbb80109d4647a8f3465eb`.
- Positive UI evidence: `2026-11-14 14:02:33.030`, PID 11067,
  `functional-ui-created runtime=isolated page=Settings`.
- Page-only logs show About, Credits, Privacy, Logs, Diagnostics and Settings transitions and settle callbacks.
  No AndroidRuntime error appeared in this filtered read. This is not a complete stability verdict.
- Read-only activity check showed MindLink foreground and the same PID. No automatic touches, screenshots,
  host feature changes, external-link taps or WeChat operations were performed; user inspection was not interrupted.
- Evidence: `build/compose-polish-20261114/device-current-35589/installation-verification.txt`.

**Actual animation, document rendering, blur and rebound visual acceptance remains pending.** Installed
hash and positive loading/page logs establish the new UI is running, not that its effects meet requirements.

Remaining device checks:

- Launch/observe MindLink only; verify candidate installed hash and isolated UI positive load evidence.
- Push/pop nested pages, fast repeated taps/back, disabled-animation setting, RTL and resume/recreation.
- Check ordinary long-document text/card edges visibly blur as they pass beneath the top bar; confirm
  About blur/material has not regressed. A still empty background is not blur evidence.
- Check short/long pages at top/bottom: drag, release, fling/reverse, return to zero; top bar doesn't rebound.
- Confirm privacy is retained and properly rendered, vectors have no emoji, concise credits and full legal
  routes work, long-document/list/top-bar positions restore after navigation.
- Do not open/restart WeChat, change host features, send messages, use payments or capture WeChat UI.
  Local screenshots require scope approval and MindLink foreground guard. Do not clear logs/configuration.

Public Release replacement and installation remain separately authorized actions.
