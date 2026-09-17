# Dev Skills gate state
Track: work commit (no version bump, no artifact publish, no release)
Version: n/a — still pre-release scaffolding
Updated: 2026-09-17

🔢 VERSION    ⬜ not owed on a work commit
🔨 BUILD      ⬜ not owed on a work commit (see notes — what was verifiable was verified)
🔒 SECURITY   ✅ full audit run, 0 Critical / 0 High outstanding — see notes
📄 DOCS       ⬜ not owed on a work commit (README/CHANGELOG updated anyway)
📦 RELEASE    ⬜ not owed on a work commit
🚀 SHIP       ⬜ not owed on a work commit

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe

## What this commit is
Workflow audit + full project audit of the PowerView Gen 3 BLE scaffold, and
the fixes for everything it found. See CHANGELOG.md "Unreleased" → Fixed /
Changed for the itemised list.

Audit totals before fixes: 1 Critical, 5 High, 11 Medium, 4 Low.
Outstanding after fixes: 0 Critical, 0 High. Three Medium items cannot be
closed from this container — listed under "Not fixed" below.

## Security gate notes
Ran with SECURITY_REFERENCE.md + SECURITY_ANDROID.md + QUALITY_REFERENCE.md +
QUALITY_ANDROID.md loaded, and the WORKFLOW_REFERENCE.md workflow audit
procedure over all of `.github/workflows/`.

Verified clean (unchanged from the previous review, re-confirmed):
- No network code anywhere, no eval/exec/shell/reflection, no SQL, no WebView.
- No logging of any kind (`Log.`/`println`/`printStackTrace` all absent), so no
  PII or keystream can reach logcat.
- One key-like literal in the repo: the openHAB binding's own published AES
  test vector, in `:protocol` test sources only. Not a credential.
- Single exported component is the launcher activity, as required.
- Keystream in EncryptedSharedPreferences, excluded from cloud backup and
  device transfer; plain metadata kept in a separate unencrypted store.
- `.gitignore` covers `*.jks`, `*.keystore`, `local.properties`.

Fixed in this commit — the Critical and both Highs from the code audit:
- `gradle/actions/setup-gradle` pinned to a SHA present in no tag of that repo
  and not fetchable from it, which meant CI could never pass and therefore the
  release gate could never be satisfied. Verified against `git ls-remote` and a
  direct fetch attempt; repinned to v4.4.4.
- Release published an unsigned (uninstallable) APK, and the step meant to
  catch exactly that only checked the filename extension.
- GATT client registration leaked on every failed/dropped connection.
- Two uncaught exception paths in the widget/worker command surface.
- Silent total data loss on one unparseable persisted blob.

## Build gate notes
Verified here:
- `:protocol` — 39 tests, 0 failures, re-run after the changes in an isolated
  Gradle project (Gradle 8.14.3, Maven Central only). Includes all five sniffed
  AES-CTR vectors re-encrypting byte-for-byte.
- All three workflow files pass `actionlint` 1.7.12 (checksum-verified binary),
  which shellchecks every `run:` block, and parse as YAML.

NOT verified here, and this is the risk to know about: the Android modules
(`:app`/`:ble`/`:data`/`:ui`/`:widget`) still cannot be compiled in this
container — no Android SDK, and Google Maven (`dl.google.com`/
`maven.google.com`) remains unreachable under this container's network policy,
so AGP itself will not resolve. `./gradlew :protocol:test` fails here for that
reason alone, at the root project's plugin block, before touching `:protocol`.
The Kotlin changes to those five modules are reviewed but uncompiled. CI is the
first thing that will actually compile them. Highest-risk file:
`app/build.gradle.kts` (new `signingConfigs` block + `signingConfig =
signingConfigs.findByName("release")`).

## Not fixed — blocked, not skipped
- Gradle dependency locking and `gradle/verification-metadata.xml`: both are
  generated from a successful dependency resolution, which needs Google Maven.
- The `UNVERIFIED` versions in `gradle/libs.versions.toml` (AGP 8.7.3,
  compileSdk/targetSdk 35, every AndroidX line): same blocker. Re-tested this
  session — `maven.google.com` returns an empty body, `dl.google.com` fails
  outright. Maven Central is reachable, and `kotlin 2.4.20` /
  `kotlinx-coroutines 1.11.0` were confirmed current from it. Dependabot is
  configured for both ecosystems and will correct the rest on its first run,
  which is now possible because CI can run again.
- `androidx.security:security-crypto 1.1.0-alpha06` guarding the keystream:
  its current upstream status cannot be checked from here. Worth a deliberate
  decision before a real release rather than carrying an alpha forward.
