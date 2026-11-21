# Continuing host UI and original WeKit sidepanel repair

## Verified starting point

The device connected at `192.168.1.148:42831`. Its APK was read-only checked as
`1de1e72fa291374b51e6cfc1a80a5e4960f17a135dccf6c1ed3893db42186f43`,
17,574,930 bytes. This is a point-in-time identity, not permission to install.
The WeChat process remained running; no restart, UI touches, screenshots, message,
payment, configuration reset, log clearing or publication was performed.

The local full candidate `b50a55aa62abda443ada7da5a29c9d93f6a31d38041e398f716b1ea3f23888d2`
contains the actual copied WeKit Compose sidepanel and all 89 classes of the prior
effects-only repair, byte-exact. The small effects-only APK does not contain the
new sidepanel payload/resources and is not a complete delivery of this task.

## Necessary repair work

Code review of the actual candidate identified remaining conditions not covered
by the prior passing JVM tests:

- Default Compose window recomposer lookup can select the host DecorView instead
  of the isolated sidepanel's LifecycleOwner.
- Invalid saved Hitokoto settings can reject the entire sidepanel snapshot rather
  than report a local card error.
- Footer fallback can subtract a stale height from a GONE native panel and clip
  the input area to 1 pixel.
- A panel mode retaining IME can reuse a stale keyboard lift after IME grows.
- Three consecutive backdrop failures can suppress recovery after real source
  changes; parent background-only mutations can leave an old sampled frame.

The local plan authorizes only necessary corrections in these areas, isolated
compilation, verification, recompression and signing. Installation and real-device
acceptance remain separate permission gates. Existing desktop, working chat header,
bubble effects, user configuration, pictures and logs are preserved.

## Packaging boundary

New `scripts/recompress_checked_candidate.py` compresses only the two indexed
sidepanel `.bin` assets. It preserves all decompressed bytes/index hashes and every
other non-signature ZIP entry's original compression stream. Root DEX stays STORED.

`apk_checks.py` accepts only STORED/DEFLATED assets, matching AssetManager.open and
the loader's decompressed DEX/hash checks. `complete_candidate_checks.py` now also
checks original desktop compression methods, sizes and raw streams.

The narrow host and new sidepanel compilation also require an updated runtime
inventory: the new sidepanel compilation/hash/class counts and the preserved
installed desktop identity must be accurate. Runtime dependency versions, resource
identity and attribution remain unchanged.

New failure tests cover pinned identity, existing output/report/temporary files,
helper identity, content changes, unintended desktop expansion and different raw
streams even with equal decompressed contents and equal compressed sizes. Current
recompression/identity check: **41 assertions passed**. Actual Android asset loading,
Compose rendering, GPU blur, IME and account reads are not proved by these tests.

## Evidence and unfinished gates

Working evidence: `build/complete-host-repair-20261114-01603b9b/`.
Narrow host compilation preparation:
`build/host-acceptance-repair-20261114-a0623237-01/`.
Sidepanel lifecycle build preparation:
`build/sidepanel-lifecycle-fix-20261114-205412-b44bf35e/`.

## Local validation completed

- Footer: 242 assertions; original recursion/panel exposure negative replays fail as expected.
- Backdrop state and real-source sampler/layer fixtures: 521 assertions, 25 sampler cases; old permanent failure-budget and stale parent-color negative replays fail as expected.
- Sidepanel Java configuration: 52 assertions. Actual pinned Compose/Lifecycle boundary checks: 49 assertions; Android/JSON boundaries remain fixtures, not rendered Android UI.
- Final actual isolated sidepanel Gradle compilation passed. APK SHA-256:
  `f3fa064ac06663522ef82b1551577b0dd19146dce7b3be8bc6b7e96475ab495f`.
  Resource table, all resource files and four ABI native libraries are byte-exact to the verified full baseline; dependencies and copied component bodies unchanged.
- Complete current local regression passed with exit 0; all 4,215 tracked input files stayed unchanged during the run. Evidence: `build/complete-host-repair-20261114-01603b9b/regression-001/`.

## Signed complete candidate and independent verification

Candidate:
`C:/code/MindLink/build/complete-host-repair-20261114-01603b9b/candidate-001/MindLink-1.0-complete-host-sidepanel-repair.apk`

- SHA-256: `3881e08c4f3a3125b290100a5bcac338201f50bddecc56bad975182207ba51a3`
- Size: **26,062,285 bytes (26.06 MB / 24.85 MiB)**.
- +8,487,355 bytes (+8.49 MB) versus the rechecked installed desktop baseline.
- −15,859,712 bytes (−15.86 MB) versus the earlier 41,921,997-byte uncompressed full candidate.
- New sidepanel ZIP compression changes no decompressed DEX/index bytes. Desktop payload bytes **and original compressed streams** are preserved.
- Narrow repaired full feature DEX: `744dd4de3147b698b1832c00cb5e9e4405be0d5fda92eab3efb79590a6ce9e55`, 689,280 bytes, DEX039.
- Four-source narrow compile only; 606 non-target classes are byte-exact to the full candidate. All previous profile/host/sidebar/desktop bridges remain.
- Actual final extracted DEX independently disassembled and incoming/outgoing/inherited platform ABI checked: no missing/incompatible references.
- Signature/certificate/v3, ZIP CRC/order/membership/DEX checksums, alignment, launcher, resources and both actual compiled payloads verified on the signed APK.
- Compared with b50a full candidate, changed decompressed entries are only `classes2.dex`, sidepanel `classes2.bin/index.txt` and updated runtime inventory. The first sidepanel DEX is only newly ZIP-compressed, not changed.
- Evidence: `build/complete-host-repair-20261114-01603b9b/{candidate-001,verification-001,final-dual-runtime-resources.json}`.

Desktop lint-vital was not rerun because the installed desktop is preserved; sidepanel lint-vital was skipped for the explicit offline candidate. Actual release compilation, AAR metadata, duplicate-class/vendor integrity/resource gates passed.

## Authorized installation and startup observation

After the separate user confirmation **install and restart for loading/crash validation**,
the phone baseline was rechecked and still matched `1de1e72f…`. `adb install -r`
returned Success. The installed APK was then hash-verified as
`3881e08c4f3a3125b290100a5bcac338201f50bddecc56bad975182207ba51a3`.

WeChat was force-stopped and launched through LauncherUI under that authorization.
The launch returned Status ok (reported splash component, 1,245 ms); later foreground
was `com.tencent.mm/.ui.LauncherUI`. The observed main process PID 29917 remained the
same across the read-only observation interval. Filtered current-process logs had no
FATAL EXCEPTION, VerifyError, linkage, stack overflow, NPE or OOM markers.

Positive loading evidence: `MindLinkSidePanel` recorded isolated toolbar creation and
`WeKit-first-composition entry=toolbar` in this current process. This establishes the
new compressed sidepanel runtime executed on Android for the toolbar entry; it does
**not** prove the drawer entry or user-visible quality/geometry. ConfigStorage profile
and CoreStorage database hooks registered, but actual `capture/read` completion was
not observed. Real avatar/nickname/status retrieval remains unverified.

Evidence: `build/complete-host-repair-20261114-01603b9b/postinstall-001/sanitized-startup.json`.
Raw logs were not saved/shown; no screenshots or automated touches were performed.
No configuration, images or logs were cleared; no external messages, payments or
publication occurred.

Still pending: drawer opening/rendering and interactions, persistent bottom-bar blur
and stable geometry after page switches, natural input height in IME/panel modes, and
real profile/status capture/read stages. The observed startup is not a full stability
or visual verdict. Further device interaction/screenshot scope requires permission;
do not install another identity using this candidate's authorization.
