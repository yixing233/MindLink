# Phone Code Integration (2026-11-19)

## Source Identity

The phone's active build is
`/root/workspace/mindlink_patch/mdlpatch-codex-20261119/work/mindlink-patched.apk`,
SHA-256 `6d9fc10750c88047f76a0bb0a9b3af602cad45475ef814ca02e900b3ad23c912`.
It matches the installed APK and the main phone project's precleanup backup.
The WeChat process mapped that installed APK during the read-only inspection.

The cleaned `GlassPatch.java` has the same live footer geometry, More resizing,
tail spacer, ancestor binding, independent layers and tips refresh blocks as
the active source. Both source trees are archived under
`build/phone-integration-20261119/`. The phone was not modified or reinstalled.

## Native Integration

- `FloatingChatFooterFeature.State` selects `ChatFooterOverlay` for a measured
  native input tree. Its existing pre-draw is the only margin writer. The old
  panel path remains for unsupported trees and undersized windows.
- Geometry commits after repeated stable measurements. For footer height H,
  visible height V and gap G: top = -(V+G), bottom = V+G-H; occupied height is 0.
- Glass bounds use the same visible height. Input and panel backgrounds are
  replaced by owned transparent drawables and restored by reference.
- Open panel scroll lift is suppressed, but closed keyboard lift remains native.
  Exiting the overlay restores a witnessed native scroll request. Closing the
  panel discards that old request. Hidden footers never veto window drawing.
- More defaults to 220dp, configurable through
  `floating_chat_footer_more_height_dp` (220..360). More and emoji are limited by
  the visible window. Native port-height changes update the restoration snapshot.
- `ChatTailSpacer` adds last-item scroll space rather than viewport padding,
  including native keyboard obstruction. Reverse layouts use adapter position 0.
  List replacement and module disable clear the owned offset. The list may keep
  a zero-height decoration for reuse, whose callback has no strong Activity/view.
- `ChatGlassSurface` resolves background siblings up to five ancestors, excluding
  the complete consumer branch while retaining the actual consumer coordinates.
  Explicit remote backdrops remain supported; a source containing the consumer
  is redirected to a sibling. Each separated pill owns its effect state.
- Tips surfaces rerecord on scroll-signature changes or after 120ms during drawing.
  Existing resource-ID-based tips chrome ownership is retained.
- Restoring separated headers now releases samplers, effects and shadow layers.

The external phone logging/control files, forced-fit override, debug hooks and
smali injection wrapper were not imported. Existing feature/config switches,
notification hooks and small-window protections remain in the native code.

## Host Bridge

The isolated bridge source is `scripts/chat-tail/mdl/spacer/TailDecoration.java`.
Its compile-only AndroidX contracts are p2 (ItemDecoration), h3.b() (item count),
RecyclerView.N(p2), D0(), u0(View), and LinearLayoutManager.h (reverse layout).
The main DEX must not contain compile-only host classes.

The embedded DEX matches the phone's verified bridge byte-for-byte:
SHA-256 `b03efdd6008f131f4d1985548add6c7bc40a6d538b4478adcc4feb1f0bc32ac5`.
The host optimizer inlined p2's empty constructor: this bridge invokes
Object.<init>, not p2.<init>. Recheck all symbols when upgrading WeChat.
`chat_tail_bridge_checks.py` validates its DEX checksums, length, single class
and loader boundary. JVM fixtures test offsets and ownership; they do not
execute ART's DEX loader.

## Build and Verification

`scripts/build_chat_candidate.py` compiles seven native source families over an
explicit SHA-256-identified baseline, checks member ABI and unrelated classes,
and preserves every non-signature APK entry except classes2.dex byte-for-byte,
including the desktop/side-panel payloads and original compressed streams.
The ordinary full-source and scoped build routes include the new helpers.

Build example (use a new output directory):

```powershell
python -I scripts/build_chat_candidate.py `
  --baseline build/chat-feedback-20261117/candidate-015/MindLink-1.0.apk `
  --baseline-sha256 602689332b33da5adb324500b02e31c1d0f8110cd361d6fc48bcaa105e844773 `
  --output build/phone-integration-20261119/new-candidate
```

Focused tests cover stable geometry, compact/limited panels, native port updates,
background/clip restoration, real hook entry, close/disable scroll restoration,
hidden drawing, list replacement, reverse layout, ancestor exclusions and pill
layer ownership. Logs and build manifests are retained in the integration build
directory. JVM and APK checks are not device/GPU acceptance of the newly built
Windows package. It uses the repository signing key; no phone signature migration
or application-data deletion is performed.

## Final Result

- Final artifact: `build/phone-integration-20261119/candidate-005/MindLink-1.0.apk`.
- SHA-256: `2bc17ed7508fcfe70cdced8402459b044ef9347e66237d94a25eb50eb60d4229`.
- Size: 44,247,383 bytes. Seven frozen source hashes match the current worktree.
- Full regression exit code 0: `build/phone-integration-20261119/regression-verified.log`.
- Footer: original 139/509 assertions plus 40 native-overlay assertions passed.
- Backdrop: 30 recovery cases / 611 assertions, plus the extended chat-glass tests.
- Bridge DEX integrity and scoped ownership checks passed.
- Real Android SDK compilation, D8, assembly, member ABI, signature and alignment
  checks passed. All 633 non-target secondary-DEX classes were preserved.
- Independent `apk_checks.py` passed ZIP/CRC, DEX checksums, class uniqueness,
  original repository certificate/v3, zipalign, launcher and both isolated
  Compose payloads. Report: `candidate-005/apk-verification/verification.txt`.
- Only classes2.dex changed; no installation or runtime/GPU claim for this package.
