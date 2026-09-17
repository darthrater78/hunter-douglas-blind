# Dev Skills gate state
Track: work commits (no version bump, no artifact publish, no release)
Version: n/a — still pre-release, nothing tagged
Updated: 2026-09-17

🔢 VERSION    ⬜ not owed on a work commit
🔨 BUILD      ✅ CI green through `1a99a41`; `3fe7a27` pending — see notes
🔒 SECURITY   ⚠️ last full audit predates this session's code — see notes
📄 DOCS       ✅ README, CHANGELOG, docs/PROTOCOL.md, docs/HANDOFF.md current
📦 RELEASE    ⬜ no PR open
🚀 SHIP       ⬜ nothing tagged or released

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe

## Read this first
`docs/HANDOFF.md` is the full state-of-play document: what is confirmed against
real hardware versus assumed, the next step and the decision still open on it,
how to verify pure-Kotlin code locally without an Android SDK, and what is
blocked rather than skipped.

## Build gate notes
CI is green on `1a99674`, `9e7760c`, `dac12fe` and `1a99a41`, each through
`assembleRelease` with R8 (which is where `lintVitalRelease` runs).

**`3fe7a27` (the weekly battery sweep) was pushed at the end of the session and
its run was still in flight.** That commit's Android code has not been seen by a
compiler. Confirm it before building anything on top of it.

The Android modules still cannot be compiled in this container (no SDK; Google
Maven unreachable). What *can* be checked locally has grown, and is worth using:
any file with no Android imports compiles and tests in an isolated
Maven-Central-only Gradle project. Recipe and current file list are in
`docs/HANDOFF.md` under "Verifying work without an Android SDK". It caught a
compile error before CI this session.

Test counts: 40 in `:protocol`, 4 in `:data`, 35 in `:ui`.

## Security gate notes
The full audit (1 Critical, 5 High, 11 Medium, 4 Low; all Critical and High
fixed) was run last session and covers the code as it stood then. **This session
added roughly 2,000 lines — five new UI screens, two view models, a notification
path and a background worker — none of which that audit saw.** Re-run before any
release.

Standing properties still hold by inspection: no network code anywhere, no
logging of any kind, no eval/exec/reflection/SQL/WebView, one exported component
(the launcher activity), keystream in EncryptedSharedPreferences and excluded
from backup and device transfer, `.gitignore` covers signing material. The only
key-like literal in the repo is the openHAB project's published AES test vector,
in `:protocol` test sources.

New surface a re-audit should look at specifically:
- `POST_NOTIFICATIONS` is now declared and `BatteryNotifier` posts. It re-checks
  the grant at runtime and stays silent without it.
- `BatterySweepWorker` connects to every non-mains shade weekly. It bails out
  early without `BLUETOOTH_CONNECT` rather than attempting doomed connections.
- `ActionRunner` moved to `:data` and now returns typed outcomes. It still
  never throws, which is the property the widget/worker callers depend on.

Three Medium items remain open and are blocked rather than skipped — Gradle
dependency locking, `verification-metadata.xml`, and the currency (not validity)
of the AGP/AndroidX pins. All need Google Maven. A fourth is worth raising in
priority: `androidx.security:security-crypto` is still at `1.1.0-alpha06`, and
step 5 is about to put a real keystream behind it.

## Release gate notes
`release.yml` will fail by design until `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` are set: without them the build produces an
unsigned APK that Android cannot install, and the workflow refuses to publish
one. CI uploads a debug-signed APK on every push, and it is now worth installing
— the app has a real UI, and everything except moving a shade works.

## Note for the next session
Commit approval does not carry across sessions. A standing approval granted in
this one means nothing in the next — ask again.
