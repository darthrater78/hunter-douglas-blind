# PowerView Gen 3 — Direct BLE Control (Android)

An Android app that reads shade state and commands shade position for
Hunter Douglas PowerView **Generation 3** shades over BLE, with **no
Gateway and no Hunter Douglas account**.

Protocol details are derived from reading the openHAB binding
`org.openhab.binding.bluetooth.hdpowerview` (EPL-2.0) — see
[`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the full reference and
`:protocol`'s KDoc for the implementation detail. That binding's code isn't
copied here (different license); the protocol was reimplemented from
understanding it.

## Status

**See [`docs/HANDOFF.md`](docs/HANDOFF.md) first** — it records what is
confirmed against real hardware versus merely assumed, which is the distinction
that matters most in this project, plus the next step and its open decision.

Starting framework. `:protocol` (advertisement parsing, command frame
encoding, AES-CTR keystream handling, capability lookup) is fully
implemented and unit-tested against real sniffed test vectors — see
[Verifying `:protocol`](#verifying-protocol) below. Everything past that
follows the build order below; see each module's TODOs for exactly what's
next.

## Module map

```
:protocol   pure Kotlin/JVM, zero Android deps — frame math, unit tested here and now
:ble        Android — scanning, GATT client, per-connection command serialization
:data       Android — repository, persisted metadata + encrypted keystream storage, saved actions
:ui         Android/Compose — shade list UI (currently: the build-order-step-2 debug screen)
:widget     Android — the single command-execution funnel (ActionRunner/CommandWorker);
            Glance widgets/tile/config activity are TODO stubs, deferred per the build order
:app        Android application — manifest/permissions, MainActivity, DI wiring
```

`:protocol` has no Android dependency on purpose: it's unit-testable on any
JVM today, and portable later to a Python/`bleak` bridge for Home Assistant
(build order step 12) without a rewrite.

## Build order

1. ✅ `:protocol` + unit tests against the sniffed test vectors. No hardware needed.
2. ✅ Scanner + a raw debug screen listing MAC / RSSI / decoded state / hex payload — confirms offsets against real shades before any writes. Confirmed against real hardware: a Duette TDBU (typeId 8) decodes to the right capability from a live advertisement.
3. Capability mapping and per-shade UI model. (`Capabilities`, `Shade` exist.)
4. ✅ GATT connect + battery reads (unencrypted, low risk). (`BatteryReader` + the debug screen's per-shade Read battery button. Device-info characteristics and the weekly sweep worker are still TODO — the sweep is step 10.)
5. Keystream import + first real write. (`FrameCipher.deriveKeystreamFromKey`, `KeystreamStore`, `ShadeGattClient.writeCommand` exist; the import UI is TODO.)
6. Keystream derivation-from-capture flow, tilt, secondary, sequence handling, command queue. (`KeystreamDeriver`, `CommandQueue` exist; the guided capture UI is TODO.)
7. Persistence, labels/rooms, the `ShadeAction` model. (`ShadeStore`, `ActionStore`, `ShadeAction`/`Command` exist.)
8. `ActionRunner` + `CommandWorker`, driven from in-app buttons first. (Both exist; in-app buttons to drive them are TODO.)
9. Glance widgets: 1×1, then the grid, then the config activity, with pending/failed states. (Stubbed with TODOs in `:widget` — deferred because this container has no Android SDK to compile/verify Glance code against.)
10. Battery sweep worker and low-battery notifications.
11. Quick Settings tile and shortcuts. (Stubbed with a TODO in `:widget`.)
12. Optional: Home Assistant bridge via a Python port of `:protocol` + `bleak` + an ESPHome BLE proxy.

## Verifying `:protocol`

The frame layout in `CommandFrameBuilder` was derived empirically: XOR-decoding
the five sniffed ciphertext vectors from the spec against each other (using
the shared AES-CTR keystream) turned up a one-byte discrepancy against an
inline hex-template annotation in the original spec text — the field-offset
table elsewhere in that same spec, and this empirical decode, agree with each
other and with what's implemented. `FrameCipherTest` re-encrypts all five
vectors with the real AES-128 key and asserts an exact ciphertext match, so
this isn't just "compiles" — it's checked against real sniffed traffic.

```
./gradlew :protocol:test
```

39 tests pass as of this scaffold (`AdvertisementParserTest`,
`CapabilitiesTest`, `CommandFrameBuilderTest`, `FrameCipherTest`,
`KeystreamDeriverTest`).

The rest of the modules need an Android SDK to build (none is installed in
the container that wrote this scaffold — `:protocol` was verified standalone
instead). CI (`.github/workflows/ci.yml`) builds the whole project on a
GitHub-hosted runner, which has the SDK preinstalled.

## Versions that need confirming before the first full build

`gradle/libs.versions.toml` marks each dependency `VERIFIED` (looked up
against Maven Central) or `UNVERIFIED`. AGP and every AndroidX line are
`UNVERIFIED` because the container that scaffolded this project could not reach
`dl.google.com`/`maven.google.com`, the only place that metadata is published.

**That marker now means less than it used to.** CI has built the whole project
green with these versions, so they demonstrably exist and work together. What
remains unknown is whether they are *current*. Check
[the AGP release notes](https://developer.android.com/build/releases/gradle-plugin)
and [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions),
then drop the markers. `agp` and `compileSdk`/`targetSdk` deserve the most
attention — targetSdk 35 may already be at or below the Play Store's floor.
Dependabot is configured and can finally open PRs for these, now that CI runs.

`androidx.security:security-crypto` (used by `KeystreamStore`, the encrypted
keystream storage) has historically only shipped pre-1.0 / alpha releases
upstream — confirm its current status before shipping; if it's still alpha,
that's worth a deliberate call given what it's protecting (see
`docs/PROTOCOL.md` §4 on what a keystream lets you do).

## Known protocol unknowns

See `docs/PROTOCOL.md` §8 — purpose of the second GATT characteristic,
whether writes want a response, sequence-byte validation, the `velocity`
field, and a few others. All need real hardware to resolve.

## CI/CD

- `.github/workflows/ci.yml` — runs `:protocol`'s tests first (fastest failure
  signal), then `assembleDebug`, `test` and `assembleRelease` across every
  module. Triggers on every branch, on PRs to the default branch, and on
  manual dispatch. All three matter: the release gate below requires a CI run
  for the exact commit being tagged, and pre-release tags are allowed to come
  from feature branches.
- `.github/workflows/release.yml` — on a `v*` tag: verifies the tag is on the
  default branch and that CI passed for that commit, builds the APK, checks it
  is signed and not debug-signed, then attaches it to a GitHub release.
- `.github/workflows/lint-workflows.yml` — actionlint over
  `.github/workflows/**` (checksum-verified binary), so a broken workflow file
  is caught without waiting for the full Android build.
- `.github/dependabot.yml` — weekly PRs for GitHub Actions and Gradle
  dependencies.

### Release signing

`app/build.gradle.kts` builds a signed release APK when these environment
variables are set, and an **unsigned** one when they are not:

| Variable | Release workflow secret |
|---|---|
| `RELEASE_KEYSTORE_PATH` | derived from `KEYSTORE_BASE64` |
| `RELEASE_KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` |
| `RELEASE_KEY_ALIAS` | `KEY_ALIAS` |
| `RELEASE_KEY_PASSWORD` | `KEY_PASSWORD` |

Until those four repository secrets exist, the release workflow **fails
instead of publishing**. That is deliberate: an unsigned APK cannot be
installed (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`), so publishing one produces
a release that looks fine and is useless to every user who downloads it.

Generate a keystore with `keytool -genkeypair -keystore release.keystore
-alias release -keyalg RSA -keysize 2048 -validity 10000`, then
`base64 -w0 release.keystore` into the `KEYSTORE_BASE64` secret. Never commit
the keystore — `.gitignore` already covers `*.keystore`/`*.jks`.

## Security notes

- The write keystream (spec §1.4/§4) is stored via `EncryptedSharedPreferences`
  (Android Keystore-backed), separate from the plain shade metadata blob —
  see `KeystreamStore` vs `ShadeStore`. It is excluded from cloud backup and
  device-to-device transfer (`data_extraction_rules.xml`, `backup_rules.xml`).
- BLE permissions are scoped to `neverForLocation` since the app filters on
  manufacturer data, not beacons. Permissions for features that are not built
  yet (foreground service, notifications) are deliberately **not** declared —
  they go in alongside the code that needs them.
- Release builds run R8 (`isMinifyEnabled = true`). `app/proguard-rules.pro`
  keeps the two things reached reflectively: `@Serializable` models in `:data`
  and `CommandWorker`, which `WorkManager` resolves by class name.
- No network calls exist anywhere in this app (by design — no Gateway, no
  account) — nothing here talks to the internet at all.
