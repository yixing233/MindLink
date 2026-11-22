# Sidebar editing, Xiaomi weather and settings layout (2026-11-21)

## Behavior

- Holding the sidebar enters `edit_home`. Card settings remain behind the edit badge.
- In editing, a second hold starts card dragging. A floating preview follows the pointer, the list scrolls at its edges, and release changes the draft order. Save persists it; discard/close retains the previously saved layout. Consumed pointer-up/cancellation does not commit a move.
- Compose claims the gesture while dragging or resizing an image. The Activity hook leaves that gesture alone. A left swipe closes at 25% displacement, or with a deliberate fling; a cancelled swipe restores the starting state.
- Settings retain Home / Chat / More. Feature cards contain their master switch and an expandable configuration area. Disabled features retain stored parameters. Top-bar search stays under the top-bar dependency; shared glass material lives under More. Toolbar order reads back the saved order, and an explicitly empty tool selection stays empty.
- Sidebar and calendar weather both use Xiaomi v3. Sidebar retains its explicit city code; calendar uses stored coordinates without an overriding city key. Invalid/empty numeric weather is rejected, including when restoring cached snapshots. Calendar cache identity includes provider and request coordinates.

## Reference

Inspected CrabTab `src/utils/weather.ts`, commit `9e1f0f76a62f4285f8c895de3be3b0f9cdad13f4`:
`https://github.com/yixing233/CrabTab/blob/9e1f0f76a62f4285f8c895de3be3b0f9cdad13f4/src/utils/weather.ts`.
Verified the Xiaomi v3 endpoint with public Beijing/Shanghai/Xi'an coordinates. Coordinate-only requests returned real current fields. The reference's Open-Meteo fallback and hardcoded demonstration values are not used.

## Verification

- Full `scripts/tests/regression.py --jdk ...` passed, including 315 side-panel assertions and toolbar saved-order/empty-selection checks. Updated stale launcher/shared-palette assertions and missing fixture methods to match the existing production baseline.
- Material3 checks passed; settings system-bar checks passed 30 assertions and the shared palette contrast checks.
- Real pinned Kotlin/Compose boundary runner passed 52 layout + 73 state/settings/reorder assertions, using this build's release classes.
- Full release build, lintVitalRelease, Java compilation, D8, apktool, resource linking, dependency hashes, signing and alignment passed.
- Android 35 emulator preview exercised the actual Compose views: first hold enters editing, dragging card 1 to index 2 produced `[2,3,1,4,5,6,7]`, and edge scrolling moved card 2 to the end. Settings Home, expanded Chat controls and dark More were rendered and inspected. The preview is local test scaffolding under `build/`, excluded from the release APK.
- Drawer closing and draft lifecycle are tested against actual Java host code with platform doubles. WeChat injection and hardware touch/GPU behavior of this candidate still require device acceptance.

Artifacts under `build/sidebar-settings-20261121/`:

- Final: `candidate-final/MindLink-1.0.apk`, 20,174,952 bytes.
- SHA-256: `d06edc0d55208d822476cfaa44dcb5f678cd289b7450c71d8a3adce3b33e2773`.
- Repository certificate: `c7a67d45660e019746675f99c14156191550f08fc4398b5cef552c9808c9bbda`.
- `apk-verification-final/verification.json`: ZIP CRC/uniqueness, DEX checksums/membership, no host stubs or preview code, payload hashes, packaged notices, v3 signature, alignment and unique launcher all passed.
- The final packaging pass updates only `assets/mindlink/README.md` from the full release build, then re-aligns and signs. All other non-signature entry contents were independently compared byte-for-byte.
- The final signed candidate installed over the emulator preview without data clearing; its launcher cold-started and Android's package verification compilation returned success.
- Screenshots: `preview/settings-overview.png`, `preview/sidebar-reordered.png`, `preview/sidebar-edge-reordered.png`.
