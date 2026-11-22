# Sidebar drag preview and drop resolution (2026-11-22)

## Symptoms reported

- During a card drag the floating preview lost its size and looked distorted /
  wrongly shadowed, especially for tall image cards.
- A fast drag across several cards released after only moving one slot, so the
  new order did not match where the finger ended.

## Changes

- The floating preview is the dragged card's own recorded drawing layer, drawn at
  the locked `height` measured at `start()`. It no longer creates a second card,
  and the original card's slot becomes a fixed-height placeholder, so a short card
  dragged past a tall image card keeps the short card's real size and the tall card
  keeps its own height in the list.
- Drop resolution is delegated to `sidePanelDropIndex`, which crosses every
  midpoint the pointer reached in a single event, including differently sized
  cards, so one fast move can jump multiple slots.
- Pointer events can outrun lazy-list measurement. `SidePanelCardReorder` keeps a
  queue of the order that produced each measured layout (`pendingLayouts`,
  `firstCardIndex`), resolves the drop against the layout that still matches the
  visible items, and only advances the queue once a new order is emitted. Repeated
  events before a remeasure therefore resolve against the correct coordinates, and
  the final pointer-up can still cross one more midpoint.
- `SidePanelCardReorder` now depends only on a `LazyListLayoutInfo` provider and a
  scroll-preserving callback, so the reorder state machine is testable on the JVM
  without an Android list.

## Tests

- Kotlin/Compose JVM boundary runner now drives the whole `SidePanelCardReorder`
  state machine: `start` accounting for content padding, locked height and overlay
  top, two rapid events resolved against stale measurement, reverse motion, stable
  remeasure, a single committed `move_card` destination, idempotent repeated
  `finish`, tall image-sized card, `scrollStep` upper/lower edges, cancel discards,
  and scrolled start coordinates. 103 assertions pass against the real pinned
  Compose runtime (`run_boundary_checks.py`); Android rendering is still separate.
- `scripts/tests/regression.py` passes end to end, including the real Java
  side-panel `gestures` suite (draft/discard/save/ownership) that now runs in the
  standard case list.
- Release build, `lintVitalRelease`, D8, apktool, resource linking, signing and
  zipalign pass. `scripts/tests/apk_checks.py` confirms ZIP CRC, DEX checksums,
  class uniqueness, unchanged primary-DEX membership, the repository v3
  certificate, single launcher, and the isolated side-panel payload.

## Device check (Android 35 emulator, real Compose views)

A local debug-only fixture (`tests/android/DragPreviewActivity.kt`, excluded from
the release APK) rendered text cards plus a 360dp image card, a 4-line text card and
a 120dp image card. Driven with real `adb shell input motionevent` gestures:

- Long press enters edit; a second hold+drag reorders.
- Dragging the short card across the tall image card and the multiline card moved it
  to index 2 (`[image-tall, multiline, short, ...]`); the mid-drag screenshot shows
  the floating preview keeping the short card's own height, not the image's.
- A single fast `MOVE`+`UP` moved a card from index 2 to index 5.
- `ACTION_CANCEL` started a drag but committed nothing; order unchanged.
- Save persisted `[image-tall, multiline, image-small, tail-a, short, tail-b, tail-c]`
  and left edit mode; Discard restored the previously saved order.

## Artifacts

- Final: `build/sidebar-drag-fix-20261122/candidate-003-final/MindLink-1.0.apk`.
- Size: 20,142,184 bytes.
- SHA-256: `390590cde5670ceabab453ed4bd8d8c4efe678474b2eb321cef097f0ed62e349`.
- Repository certificate: `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`.
- APK check report: `build/sidebar-drag-fix-20261122/apk-verification-final/`.
- JVM boundary: `build/sidebar-drag-fix-20261122/boundary-final/` (103 assertions).
- Release payload: `classes.dex` 4051596 bytes / 3589 classes,
  `classes2.dex` 782616 bytes / 653 classes (host members byte-identical to the
  prior candidate, so only the Compose reorder/card-frame payload changed).

Signed with the repository key. The release APK does not contain the fixture
activity, preview manifest entries, `*Checks` classes or the temporary diagnostic
logging used during device investigation.

## Phone installation (root, data preserved)

The phone's previous install used a different certificate, so the package was
reinstalled with data preserved instead of a plain `adb install -r`:

1. Force-stop WeChat; back up the installed APK and `/data/data` + `/data/user_de/0`
   private dirs to `/data/local/tmp` (private data held only cache/oat/files).
2. `pm uninstall dev.mindlink`, install the candidate, verify the repository v3
   certificate.
3. Confirm the module config `wex_config.db` (in
   `/sdcard/Android/media/com.tencent.mm/Mdl`, unaffected by the module uninstall)
   is byte-identical before/after (SHA-256 `dab27156…a03844`).

The first relaunch showed cards snapping back on drop because WeChat had not
cleanly loaded the new module into its process; after a full force-stop and
`LauncherUI` restart the drag reorder commit worked and was confirmed on device.
Reinstalling or updating later must use the repository certificate; a rooted
same-certificate `adb install -r` then works normally.
