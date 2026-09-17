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

### Known gaps
- No Android SDK was available to build the Android modules in the
  environment that wrote this scaffold; `:protocol` was verified standalone.
  CI builds everything.
- Several AndroidX/AGP dependency versions are unverified — see the README
  and `gradle/libs.versions.toml` header.
- No app icon yet (uses a system placeholder drawable).
- Release workflow produces an unsigned APK until a signing config is added.
