# Profile capture / refresh repair after real-device feedback

## Context

The installed complete candidate `3881e08c4f3a3125b290100a5bcac338201f50bddecc56bad975182207ba51a3`
executed the actual WeKit drawer on Android, but the user confirmed avatar and nickname
were still not obtained. This requirement remains failed/unaccepted. The same WeChat
main PID remained 29917 and the exact candidate hash was rechecked. Filtered persisted
stages contained profile/database Hook registration but no configuration capture.
Successful read/publication markers did not exist in that candidate, so absence of
those markers alone was not used to infer which read executed.

## Actual host static evidence

A read-only copy of the currently installed WeChat versionCode3141 APK was placed in
its own new empty input directory. No user data/database was copied. APK SHA-256:
`04792eb19a566395a9fabee9a50be80245826cb774ec64637a22f7f28acd4054`,
266,102,064 bytes.

Confirmed actual DEX members:

- `hm0.j1.u():hm0.b0` — CoreStorage.
- `hm0.b0.c():com.tencent.mm.storage.m3` — ConfigStorage, following account readiness check.
- `m3.l(int,Object):Object` — the current unique identity reader; both exact class anchors are unchanged.
- `hm0.j1.i():hm0.j1` — unique public/static/no-argument self-return, asserts an existing singleton; does not create it.
- `j1.d:b0`, `j1.e:m`, `b0.g:m3` are unique direct typed fields.
- `m.o:hm0.k` is a unique public volatile enum. Existing Enum name `AccountHasReady`
  is exactly the host's readiness condition; the other state is `NotReady`.
- `hm0.f.run()` changes the account to Ready at its end, with the log anchor
  `changeToAccountHasReady`. Core/config constructors or `m.p(boolean)` alone do not prove Ready.

A loaded Class/Method is not proof of initialized host state. No `Field.get(null)`,
Unsafe state read or proactive Kernel getter invocation is added. Observing successful
host calls supplies existing weak instances instead.

Static evidence: `build/profile-capture-repair-20261114-01603b9b/evidence-static-001/`.
Code/input file is untrusted data, never executed; tools are in the separate metadata-tools
folder and Python is invoked with `-I -B`.

## Narrow repair

Only HostDatabaseBridge, HostProfileBridge and SelfProfile production families are
approved for recompilation. Existing desktop, complete original sidepanel runtime,
input/navigation repairs, header and bubbles retain original bytes/compressed streams.

- Observe the unique host-owned initialized Kernel getter after success, inspect direct
  core/account typed fields and the existing enum readiness value, then observe the
  unique direct config field. Weak holder replay supports a later reader registration.
- Optional exact readiness-publication callback uses the confirmed method anchor and
  actual Runnable/typed-account metadata; exceptions or ambiguity never invent Ready.
- Same-instance host reader signals are bounded and module reads are suppressed.
- Pending profile refresh while loading is coalesced to one tail read. Old generations
  cannot clear new loading/dirty. Optional nickname failures remain unknown instead of
  clearing known same-account text; confirmed empty remains empty.
- Capture/request/read/reject/publication emit fixed stages and booleans only. No
  accounts, nicknames, avatar URLs, SQL arguments, paths or exception messages in logs.
- Avatar still uses the proven host avatar record and existing CDN/size/redirect gates;
  no invented avatar interface, arbitrary URL origin or additional upload service.

## Local validation

SelfProfile's old force-dropped/name-cleared negative cases were actually reproduced
before repair. Final local gates executed successfully:

- SelfProfile: 41 cases; TextStatusBridge: 17 cases.
- Config/profile/database bridges: 38 cases; real resolver metadata: 3 cases;
  bundled DexKit public signatures: 8 checks.
- Three-bridge integration: 9 cases compiling the actual HostProfileBridge,
  HostDatabaseBridge and SelfProfile in the same source set. Android/Xposed/DB
  boundaries are synthetic; these are not actual-account tests.
- Existing database suite: all original 14 positive/negative cases retained and
  2 additional missing/ambiguous account-metadata cases, all passing.
- Resolver/background/listener isolation: 93 assertions.
- Complete regression `regression-002`: exit 0, with all 4,217 input files unchanged.
  It includes the new three-bridge integration gate, existing UI-related Java/Smali
  regressions, and prior resource/packaging gates; it does not run Android UI pixels.

The first complete run, `regression-001`, failed compiling the resolver suite because
its compilation-only HostProfileBridge double lacked the new `storageType()` API.
Only that double was extended with a default-deny `null` result; its 93 assertions
passed before the complete rerun. The failed run and unchanged-source evidence were
retained, not overwritten. The old database suite also initially failed 13 of 14
cases because its Kernel fixture lacked account/readiness metadata. Positive fixtures
were migrated to the valid typed Kernel/Account shape and passive successful callbacks,
not rewritten into FAILED expectations and not addressed by weakening production.
That earlier database failure is recorded in tool stdout/coordinator reports, not a
separate log file.

## Three-family build and candidate

Narrow build: `build/profile-capture-repair-20261114-01603b9b/narrow-host-tools/generation-001/`.
Only the three approved sources were compiled. Final feature DEX039 is 697,844 bytes,
646 classes (644 before); only HostDatabaseBridge$3/$4 were added. All 623 non-target
smali classes are byte-exact. Actual mixed ABI checks covered incoming 29, outgoing
290 and inherited 12 references, with zero missing/incompatible/unresolved entries.

- Feature SHA-256: `413a3152e81c7983e5a3a4cc6b39f72a98c34249e5c6c9cbf02fed4a08192092`.
- Source-freeze SHA-256: `3e7b5c4c4b22d3d07590d9b03ee7a1abf239aace92772e791883043c16afa53b`.
- Candidate: `build/profile-capture-repair-20261114-01603b9b/candidate-001/MindLink-1.0-profile-capture-repair.apk`.
- Candidate SHA-256: `9c52a3f4dd2cde21d363b83ba568ecc5c042505f1b7740acb86521c2a7548714`.
- Size: 26,078,669 bytes, +16,384 bytes (16 KiB) over the installed complete baseline.

Packaging's strict comparison permits changes only to `classes2.dex` and
`assets/compose-runtime-inventory.json`. Every other non-signature entry retains its
bytes and original compressed stream, including desktop and complete WeKit sidepanel
payloads, primary DEX, resources, manifest, JNI and notices. Historical desktop source
identity remains the preserved `1de1e72f…` installation; it is not renamed to the
current `3881e08c…` profile baseline or represented as a desktop rebuild. Original
certificate, v3 signature and zipalign checks passed during packaging.

Independent verification of the actual signed candidate is still in progress.
The package is not installed. Local tests/build/signing do not establish real profile
capture, database account confirmation, avatar URL availability, bitmap retrieval or
visible acceptance. The user's prior failure remains unresolved on the phone until
this candidate is separately authorized and checked on-device.

A content-free stage parser was tested with 66 synthetic assertions, preserving the
new capture/read/reject/publish tokens and rejecting appended identity/URL/SQL/error
messages. It has not collected device evidence and performs no device operations.

The prior candidate's install/restart permission is not authorization to install a
new identity. No further restart, screenshots, automated touches, data clearing,
message/payment or publication has occurred.
