# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Added
- Initial multi-module Gradle scaffold (`:protocol`, `:ble`, `:data`, `:ui`,
  `:widget`, `:app`).
- `:protocol`: advertisement parsing, 13-byte command frame builder,
  AES-128-CTR keystream handling, shade capability lookup — fully
  implemented and unit-tested against the sniffed BLE test vectors (39
  tests, all passing).
- `:ble`: `ShadeScanner` (advertisement scanning), `ShadeGattClient`
  (connect/discover/write/read), `CommandQueue` (per-connection op
  serialization).
- `:data`: `ShadeRepository` (merges live scan + persisted metadata),
  `ShadeStore` (plain metadata via DataStore), `KeystreamStore` (encrypted
  keystream storage), `ActionStore` (saved `ShadeAction`s).
- `:widget`: `ActionRunner` (the single command-execution funnel) and
  `CommandWorker` (`WorkManager` integration). Glance widget UI, the widget
  config activity, and the Quick Settings tile are stubbed with TODOs —
  deferred pending an Android SDK to build/verify them against.
- `:ui`: `DebugScanScreen` — the build-order-step-2 raw scan debug view.
- `:app`: manifest with BLE/foreground-service/notification permissions,
  `MainActivity`, `PowerViewApplication` (manual DI wiring + `WorkManager`
  configuration).
- `docs/PROTOCOL.md` — protocol reference, including the empirically
  corrected 13-byte command frame layout.
- CI (`.github/workflows/ci.yml`), release (`.github/workflows/release.yml`),
  and Dependabot (`.github/dependabot.yml`) workflows.

### Added
- Battery status (build order step 4, spec §2.5). `BatteryReader` connects over
  GATT, reads the standard Battery Service (`0x180F` / `0x2A19`), persists the
  reading to `ShadeStore` and disconnects. No keystream is involved — the
  battery characteristic is unencrypted. The value is a coarse low/medium/high
  bucket, not a charge percentage, and is rendered as such. Reads are on demand
  from the debug screen rather than polled, because connecting costs the shade
  power; the weekly sweep and low-battery notifications (step 10) are a
  scheduled caller on top of this, still to come.
- The debug screen now shows what build order step 2 actually calls for: RSSI,
  the raw advertisement payload in hex, and the velocity byte. Decoded fields
  alone only show the parser's opinion of the bytes — confirming an offset
  against real hardware means seeing the hex they came from.
- Scan status is surfaced (Scanning / Stopped / Failed, with the reason) plus a
  retry button and a real empty state. A denied `BLUETOOTH_SCAN`, a disabled
  adapter and "nothing in range" previously all rendered as the same blank
  screen, leaving the debug screen unable to explain the one thing it exists
  to explain.
- Fields a shade's capability says it does not have are now labelled as such,
  so a decoded value from an unused byte is not mistaken for a real position
  (e.g. tilt on a top-down/bottom-up shade).

### Fixed
- **CI and releases could never succeed**: `gradle/actions/setup-gradle` was
  pinned to `ac638b0…`, a SHA that exists in no tag of that repository (and is
  not fetchable from it at all), so both workflows failed at "Setup Gradle".
  With CI unable to pass, `release.yml`'s "CI passed for this commit" gate could
  never be satisfied either. Repinned to v4.4.4 (`48b5f21…`).
- CI now runs on every branch and on manual dispatch. It previously ran only on
  `main`/`master`, which meant a pre-release tag cut from a feature branch could
  never satisfy the release gate — and the gate's own advice ("dispatch CI by
  hand") pointed at a `workflow_dispatch` trigger that did not exist.
- Release builds no longer publish an unsigned APK. `assembleRelease` without a
  signing config emits `app-release-unsigned.apk`; the old glob matched it, the
  old verification step only checked the filename ended in `.apk`, and the
  published release was uninstallable. The workflow now rejects `*-unsigned.apk`
  and refuses a debug-signed APK.
- `ShadeGattClient` leaked a GATT client registration on every failed or dropped
  connection: the disconnect callback cleared the `BluetoothGatt` reference
  without `close()`, so `disconnect()` had nothing left to close. Android caps
  these per process, and out-of-range failures are the expected case (spec
  §3.4), so the ceiling was reachable in normal use.
- `ActionRunner` no longer throws out of the widget/worker command path. It now
  checks `BLUETOOTH_CONNECT` (which `ShadeGattClient` documents as the caller's
  job), validates the stored MAC before `getRemoteDevice`, catches a revoked
  permission, and releases the connection under `NonCancellable`.
- A single unparseable blob in `ShadeStore`/`ActionStore` no longer destroys all
  persisted data. The decode failure was treated as "no data", and the next
  write persisted that empty state over the user's labels, rooms and saved
  actions. Unreadable data is now quarantined under a separate key instead.
- `ShadeGattClient` state shared with the Binder callback thread is `@Volatile`,
  deferreds are cleared after completion, `disconnect()` goes through the same
  `CommandQueue` as every other operation, and an operation timeout now marks
  the connection unusable rather than letting the next operation start while the
  timed-out one is still outstanding.
- `KeystreamStore` validates stored hex instead of silently truncating odd-length
  values and throwing `NumberFormatException` on non-hex ones.
- `ShadeRepository` no longer starts scanning from its constructor (which
  published `this` to a coroutine mid-construction and contradicted its own
  documented contract). `MainActivity` starts the scan explicitly.
- Locale-independent formatting for the hex keystream, the raw advertisement
  payload, and the debug screen's percentages.

### Changed
- Release builds run R8 (`isMinifyEnabled = true`) with keep rules for the
  `@Serializable` models and `CommandWorker`. CI runs `assembleRelease` so a
  bad keep rule surfaces on push rather than at release time.
- `app/build.gradle.kts` gained an environment-variable-driven release signing
  config; no keystore or password is committed.
- Dropped `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` and
  `POST_NOTIFICATIONS` from the manifest — nothing requests or uses them yet,
  and `FOREGROUND_SERVICE_CONNECTED_DEVICE` without a declared `<service>` also
  fails Play review. They go back in with the features that need them.
- Added `.github/workflows/lint-workflows.yml` (actionlint, checksum-verified).
- `release.yml`: `timeout-minutes` on the `gate` job (it polls for up to 30
  minutes), least-privilege `permissions` on the `release` job, the APK path
  passed through `env:` rather than interpolated into the shell script, and
  `set -euo pipefail` on the last run block.

### Known gaps
- No Android SDK was available to build the Android modules in the
  environment that wrote this scaffold; `:protocol` was verified standalone.
  CI builds everything.
- Several AndroidX/AGP dependency versions are unverified — see the README
  and `gradle/libs.versions.toml` header.
- No app icon yet (uses a system placeholder drawable).
- Release signing secrets are not configured yet, so `release.yml` will fail at
  the APK verification step until they are — by design, see the README.
- No Gradle dependency locking or `verification-metadata.xml`: both have to be
  generated from a successful resolution, which needs the Android SDK and
  reachable Google Maven.
