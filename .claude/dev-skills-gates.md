# Dev Skills gate state
Track: work commit (no version bump, no artifact publish, no release)
Version: n/a — still pre-release, nothing tagged
Updated: 2026-09-17

🔢 VERSION    ⬜ not owed on a work commit
🔨 BUILD      ✅ CI green on every module — see notes
🔒 SECURITY   ✅ full audit run; 0 Critical / 0 High outstanding
📄 DOCS       ✅ README, CHANGELOG, docs/PROTOCOL.md, docs/HANDOFF.md current
📦 RELEASE    ⬜ no PR open
🚀 SHIP       ⬜ nothing tagged or released

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe

## Read this first
`docs/HANDOFF.md` is the full state-of-play document: what is confirmed against
real hardware versus assumed, the next step and the decision gating it, the
sandbox's build limitations, and what is blocked rather than skipped.

## Build gate notes
CI (`.github/workflows/ci.yml`) is green on every module: `:protocol` tests,
`assembleDebug`, unit tests, debug APK artifact, and `assembleRelease` with R8.
That is what makes this ✅ rather than ⬜ — but note it is CI, not a local dev
build. **The Android modules cannot be compiled in this container at all** (no
SDK; Google Maven unreachable, so AGP will not resolve). `:protocol` is verified
locally in an isolated Gradle project: 40 tests, 0 failures, including one
vector captured from real hardware.

## Security gate notes
Audited with SECURITY_REFERENCE + SECURITY_ANDROID + QUALITY_REFERENCE +
QUALITY_ANDROID and the WORKFLOW_REFERENCE workflow procedure. Found 1 Critical,
5 High, 11 Medium, 4 Low; all Critical and High fixed. Detail is in CHANGELOG.md
under Unreleased → Fixed.

Standing properties, re-confirmed: no network code anywhere, no logging of any
kind, no eval/exec/reflection/SQL/WebView, one exported component (the launcher
activity), keystream in EncryptedSharedPreferences and excluded from backup and
device transfer, `.gitignore` covers signing material. The only key-like literal
in the repo is the openHAB project's published AES test vector, in `:protocol`
test sources.

Three Medium items remain open and are blocked rather than skipped — Gradle
dependency locking, `verification-metadata.xml`, and the currency (not validity)
of the AGP/AndroidX pins. All need Google Maven. See `docs/HANDOFF.md`.

## Release gate notes
`release.yml` will fail by design until `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` are set: without them the build produces an
unsigned APK that Android cannot install, and the workflow refuses to publish
one. CI uploads a debug-signed APK on every push for testing in the meantime.

## Note for the next session
Commit approval does not carry across sessions. A standing approval granted in
this one means nothing in the next — ask again.
