# Dev Skills gate state
Track: work commit (no version bump, no artifact publish, no release)
Version: n/a — this is scaffolding, not a release
Updated: 2026-09-17

🔢 VERSION    ⬜ not owed on a work commit
🔨 BUILD      ⬜ not owed on a work commit (see notes — :protocol verified anyway)
🔒 SECURITY   ✅ self-reviewed, see notes below
📄 DOCS       ⬜ not owed on a work commit (README/CHANGELOG/docs/PROTOCOL.md written anyway)
📦 RELEASE    ⬜ not owed on a work commit
🚀 SHIP       ⬜ not owed on a work commit

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe

## What this commit is
Initial multi-module Gradle scaffold for the PowerView Gen 3 BLE Android app
(per the user-supplied implementation spec) plus CI/CD workflows. See
CHANGELOG.md "Unreleased" section for the full contents list.

## Build gate notes
No Android SDK is installed in this container, so the Android modules
(:app/:ble/:data/:ui/:widget) could not be compiled here. :protocol has zero
Android dependencies and WAS built + tested standalone in an isolated
temp Gradle project (Gradle 8.14.3, JDK 21, offline after initial wrapper/
Maven Central resolution) — 39 tests pass, including all 5 sniffed AES-CTR
frame vectors re-encrypted end-to-end and matched byte-for-byte. CI
(.github/workflows/ci.yml) builds the full project on a GitHub-hosted
runner with the Android SDK preinstalled.

## Security gate notes (self-review, no separate audit tool run)
- No eval/exec/shell/reflection, no SQL, no network code anywhere (by
  design — no Gateway, no account, nothing talks to the internet).
- No hardcoded secrets. The one embedded key-like literal is the openHAB
  binding's own published AES test vector key, used only in :protocol's
  test suite to verify frame encryption against known ciphertexts — not a
  real credential.
- The one real secret in the app (the per-home write keystream — anyone
  with it can command every shade in the house) is stored via
  EncryptedSharedPreferences (Android Keystore-backed) in KeystreamStore,
  kept separate from the plain-text ShadeStore metadata blob.
- Found and fixed during this review: android:allowBackup="true" had no
  exclusion rules, which would have swept the encrypted keystream store
  into cloud backup / device-transfer. Added data_extraction_rules.xml
  (API 31+) and backup_rules.xml (legacy) excluding shade_keystreams.xml
  specifically, while still backing up plain shade labels/rooms/actions
  (legitimate UX value, no secret).
- BLE permissions scoped correctly (neverForLocation, since filtering is on
  manufacturer data, not beacons).
- No dependency audit tool (npm/pip/cargo-audit equivalent) was run: no
  ecosystem lockfile exists yet to audit against (first real
  ./gradlew build, which needs network + Android SDK, will resolve one).
  AGP/AndroidX versions are flagged UNVERIFIED in gradle/libs.versions.toml
  because Google's Maven repo (the only place that metadata is published)
  was unreachable from this container's network policy — flagged loudly
  in-file and in the README rather than silently guessed as fact.
