# Sidebar Editor Freeze (2026-11-19)

## Cause

`HomeSidePanelFeature.DrawerSession.showEditor()` keeps the original Compose view
visible and publishes an editing snapshot. However, `syncLifecycle()` previously
required `editorDraft == null` before resuming that same view. Starting a draft
therefore sent `SidePanelComposeBridge.lifecycle(view, false)`.

`SidePanelUi.SidePanelOwner` translates that into a CREATED lifecycle and an
inactive `HomeSidePanelState`. Its `call()` method discards inactive callbacks;
save, discard, add and settings actions consequently stop responding. The
lifecycle-aware recomposer can also stop its frame clock. This is a deterministic
editor lifecycle defect, not evidence of a thread deadlock.

The only production change removes the draft condition. A visible drawer remains
resumed during editing. Activity pause, drawer closing, zero progress and detach
still suspend or dispose the same owner.

## Regression Coverage

- The Java bridge double now dispatches through per-view lifecycle gates instead
  of calling Runnables unconditionally.
- Existing editor checks use the gated save/discard/picker paths (49 assertions).
- New editor-lifecycle checks cover all three editor entry routes, repeated
  settings/add/delete actions, pause/resume preserving the draft, save/discard,
  close/reopen, root/scroll-owner reuse and disposal (67 assertions).
- A negative replay restores the old predicate in an isolated temporary source
  tree and fails at `visible editor root stays resumed`, confirming the defect.
- Real pinned Kotlin/Compose/Lifecycle boundary checks pass 56 assertions,
  including editing updates, save/add/settings/discard callbacks, real pause,
  resume and disposal. Android platform boundaries are fixtures; this does not
  render AndroidComposeView or establish device/GPU acceptance.
- The boundary runner includes the current editor payload carriers in compilation
  so older cached UI classes do not hide their required symbols.
- Full regression passed with exit code 0:
  `build/sidebar-edit-freeze-20261119/regression.log`.
- Focused output: `build/sidebar-edit-freeze-20261119/side-panel-tests.log`.
- Kotlin output: `build/sidebar-edit-freeze-20261119/kotlin-boundary-verified/`.

## Candidate Identity

Baseline: `build/phone-integration-20261119/candidate-005/MindLink-1.0.apk`,
SHA-256 `2bc17ed7508fcfe70cdced8402459b044ef9347e66237d94a25eb50eb60d4229`.
The previously integrated chat/phone fixes are preserved.

Candidate: `build/sidebar-edit-freeze-20261119/candidate-001/MindLink-1.0.apk`.
SHA-256 `dc2c79d1dbe5bf9e22db22128642fdf608fcbe394e62e08fed6d31fe54f142c5`.
Size: 44,247,383 bytes. Repository certificate:
`c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`.

Only the HomeSidePanelFeature family is compiled/replaced. All 631 other
secondary-DEX classes remain byte-exact. No classes are added or removed.
Only classes2.dex changes; the primary DEX, resources, desktop/side-panel payloads
and all other original compressed streams are preserved.

Real Android compilation, D8, assembly, incoming/outgoing member ABI, signing,
zipalign and independent APK ZIP/CRC/DEX/class uniqueness/launcher/payload checks
passed. Current source matches the candidate's frozen hash.

```powershell
python -I scripts/build_chat_candidate.py `
  --baseline build/phone-integration-20261119/candidate-005/MindLink-1.0.apk `
  --baseline-sha256 2bc17ed7508fcfe70cdced8402459b044ef9347e66237d94a25eb50eb60d4229 `
  --family HomeSidePanelFeature `
  --output build/sidebar-edit-freeze-20261119/new-candidate
```

## Device and Scope

Read-only inspection found the installed phone package still at
`6d9fc10750c88047f76a0bb0a9b3af602cad45475ef814ca02e900b3ad23c912`,
mapped by WeChat PID 28995. Existing logs/ANR files did not establish an ANR
associated with this editing report. No installation, restart, UI interaction,
configuration/database change, image deletion or log clearing was performed.

This repair addresses entering editing and losing the view's lifecycle/callbacks.
Storage latency, synchronous image import and independent missing per-field
callback/payload contracts were not changed or claimed to be fixed.
