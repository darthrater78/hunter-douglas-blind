# Changelog

All notable changes to this project are documented here.

## [Unreleased]

### Changed
- **The shades app bar's two text buttons became a `More` overflow menu.**
  Three destinations (Actions, Raw scan, Settings) do not fit as three labels
  on a phone's app bar. The trigger is a word rather than an icon because the
  project pulls in no icon dependency — the same reason the action editor names
  its icon choices instead of drawing them.
- **`ActionRunner` and `ActionResult` move from `:widget` to `:data`.** The
  runner is a GATT-backed domain service over `:ble`, not a home-screen
  surface — `BatteryReader` is the existing precedent for exactly that shape in
  exactly that module. `:ui` and `:widget` are peers that both drive commands,
  so neither should have to depend on the other to reach the funnel. `:widget`
  keeps `CommandWorker` and the Glance surfaces.
- **`ActionRunner` reports *why* a command failed.** It returned a bare
  `Boolean` and told callers wanting detail to go and inspect
  `ShadeStore`/`KeystreamStore` themselves. The new `CommandOutcome` separates
  failures knowable without touching BLE (`NotAttempted`: no keystream, no
  `homeId`, unknown shade, missing permission, Bluetooth off, malformed MAC)
  from `TransportFailed`, which carries the stage it got to. That distinction
  is about the physical world rather than about error handling: `NotAttempted`
  means the shade certainly did not move, while a failed *write* may have
  landed before the acknowledgement was lost. `ActionRunner.send` and
  `checkReadiness` expose this to in-app UI; the widget and `CommandWorker`
  paths still collapse to a single failure, which is all a home-screen icon can
  say. This matters now because, with keystream onboarding deferred to last,
  `NoKeystream` is the expected outcome of every command in the app.
- **`0x2A19` is a percentage, not a coarse bucket.** `docs/PROTOCOL.md` claimed
  the battery characteristic returned 10/50/100 for low/medium/high, following
  the openHAB binding's behaviour. A real Duette TDBU returned 65. The
  Bluetooth SIG defines Battery Level as a uint8 percentage, and the evidence
  is consistent with these shades honouring that, so the doc is corrected and
  the reading is now shown as `65% (high)` rather than `medium (raw 65)`.
  `ShadeMetadata.batteryBucket` is renamed to `batteryPercent` with
  `@SerialName("batteryBucket")`, so readings already on disk still decode.
- `docs/PROTOCOL.md` §8: the `velocity` byte is probably misnamed. A stationary
  shade reported `0xC0` (`0b1100_0000`), which reads like a flags byte rather
  than a speed.

- **The weekly battery sweep and low-battery notifications** (build order step
  10). `BatterySweepWorker` reads every shade not marked mains-powered and posts
  one summary notification for those at or below `LOW_BATTERY_PERCENT`.
  `POST_NOTIFICATIONS` is declared now that something actually posts, as the
  manifest's own note said to do.
- The sweep is shaped by the fact that reading a battery costs the shade the
  power being measured: weekly rather than daily, sequential rather than
  parallel, skipping mains-powered shades, and bailing out immediately when
  `BLUETOOTH_CONNECT` is missing instead of working through a dozen doomed
  connections. A failed read is not retried and not reported — the shade is
  swept again next week, and a notification about a failed *reading* is noise
  about the app rather than news about the shades.

### Added
- **The Quick Settings tile (build order step 11, in part).** One designated
  `ShadeAction`, run through the same `CommandDispatch` call a widget tap
  uses, so the tile is a second button on one funnel rather than a second
  path to BLE. Shortcuts, the other half of step 11, are not built yet.

  **Which action it runs is chosen in the app's settings, not on the tile.** A
  tile is one button with nowhere to put a picker, unlike a widget, which gets
  a configuration activity when it is placed. The id is stored rather than the
  action, so renaming or retargeting needs no reconfiguration — the same
  contract widgets have.

  **The last run's outcome is held in memory and deliberately not persisted.**
  There is exactly one tile, so there is exactly one value, and it is worth
  only as much as the process it was learned in: after the app has been
  killed, "last run failed" is stale news the user cannot act on from a tile.
  The app is the record, which is what the failure subtitle points at.

  **It does nothing from the lock screen.** The spec asked for a lock-screen
  control, and it is not wanted: this tile moves physical objects in someone's
  home, and a phone on a table should not be a remote for them. A tap goes
  through `unlockAndRun`, which demands the lock screen first and runs
  straight through when the device is already unlocked, so the owner pays
  nothing for it. A locked tile also shows a generic name and "Unlock to use"
  instead of the action's label — an action is named for where it is and what
  it does, which is not a stranger's to read off a lock screen.

  Exported, like any tile, but bound behind `BIND_QUICK_SETTINGS_TILE` so only
  the system can reach it.

  `Tile.subtitle` is API 29, so it is guarded; below that the label carries
  everything and nothing is lost. `startActivityAndCollapse` is branched on
  API 34, where the `Intent` overload was replaced by a `PendingIntent` one
  and now throws — the `PendingIntent` is `FLAG_IMMUTABLE`.

- **The home-screen widget (build order step 9).** One to six buttons per
  widget, each running a saved `ShadeAction` through the same `ActionRunner`
  funnel as the in-app controls. `ShadeActionWidget` + its receiver,
  `WidgetConfigActivity` to choose which actions a given instance runs,
  `WidgetStatus` to report results back, and `CommandDispatch` as the single
  tap-to-work call the Quick Settings tile will also use.

  A tap does no BLE work. Glance gives a callback a short window and a shade
  exchange takes seconds, so the callback marks the button pending and
  enqueues `CommandWorker`; the worker settles the button when it finishes.
  Every terminal path in the worker reports, because a button stuck on
  "Sending…" forever is worse than one that admits it failed.

  **Not expedited work, despite the original TODO saying so.** Below API 31
  WorkManager satisfies an expedited request by promoting the worker to a
  foreground service, which needs `getForegroundInfo()` — whose default
  implementation throws — plus two `FOREGROUND_SERVICE*` permissions and a
  declared service. At `minSdk = 26` that is a crash on Android 8 through 11,
  not a degraded experience.

  State is per widget *instance*, not global: two widgets can point at the
  same action, and one being mid-run is not a fact about the other's button.
  Results, though, are written to every widget showing the action — a shade
  moving is a fact about the action, not about which button was pressed.
  Tap debounce has three layers, because a tap that queues a duplicate BLE
  round trip costs battery on the shade: the composition drops the tap target
  while a slot is pending, the callback re-reads the stored state behind it,
  and `ExistingWorkPolicy.KEEP` on a per-action unique work name catches
  whatever still gets through, including taps on a second widget.

  A success clears the button back to idle rather than showing a tick. The
  widget cannot verify that a shade moved — only that a frame was
  acknowledged — and a confirmation mark would claim more than `ActionRunner`
  knows. The in-app screens keep the nuance, including the load-bearing "may
  or may not have moved"; a home-screen button just says "Failed — open the
  app".

  The widget follows the launcher's theme (dynamic colour on API 31+) rather
  than the app's own setting. A widget sits on someone else's wallpaper, where
  the black OLED scheme would be wrong as often as right.

  `updatePeriodMillis` is 0. A widget waking on a timer would connect to
  shades to learn that nothing had changed, spending the battery it exists to
  report on; updates are pushed from the tap and from the worker.

- **A theme picker, with an OLED black option.** Settings (app bar → More →
  Settings) offers Follow system / Light / Dark / Black (OLED), persisted in a
  new `SettingsStore`. The app previously used bare `MaterialTheme`, i.e.
  whatever the system said, with no way to choose.

  Light and dark stay on Material 3's baseline palette deliberately — the app
  has no brand colours, and inventing some in the change that adds a picker
  would mean every screen changed appearance for reasons unrelated to the
  setting the user just touched.

  The OLED scheme is the dark scheme with the background/surface family pulled
  to black, and two details in it are the difference between an OLED theme and
  an unreadable one. The containers are *not* black: Material draws cards,
  sheets and the app bar from the `surfaceContainer` roles, and a black card on
  a black background is an invisible card, so the background is a true
  `0xFF000000` — which is what actually switches OLED pixels off, and where
  most of a screen's area is — while the containers sit on a near-black ramp
  just bright enough to read as edges. And `surfaceTint` is black, because
  Material blends the tint into a surface in proportion to its elevation, so a
  nominally black elevated surface would otherwise come out grey and the theme
  would quietly fail at exactly the components it most needs to work on.

  OLED does not follow the system light/dark setting. Someone who picks it has
  asked for black, and going light during the day would be a different theme
  than the one they chose; `Follow system` already exists for that.

  `values-night/themes.xml` gives the window a black background so a cold start
  does not flash AppCompat's mid-grey before Compose draws. That applies to
  both dark themes, since a resource qualifier can only see the system's night
  setting and not the app's own `ThemeMode` — harmless, because the standard
  dark scheme's surfaces are nearly that dark anyway.
- **Saved actions are reachable**: a list screen that runs, edits and deletes
  them, and an editor. Gen 3 shades have no on-shade scenes, so a `ShadeAction`
  *is* the scene (spec §3.1), and widgets, the tile and shortcuts will all
  reference these by id.
- Every field in the action editor is opt-in, with a switch of its own. A null
  field in a `Command` means "leave this rail where it is" — a real instruction
  to the shade — so a slider alone could not distinguish "set tilt to 0" from
  "don't touch the tilt". Shades with nothing enabled are dropped on save
  rather than being sent a command that says nothing and still costs a full
  connect/disconnect cycle.
- An action whose shades were all blocked before BLE reports the single cause
  once instead of listing every shade as failed. Until keystream onboarding
  exists that is every action, and six failure rows would send the user
  investigating six non-problems.
- An action editor draft lives in its view model, so rotating the device
  mid-edit does not discard a half-built action.
- **In-app controls that drive `ActionRunner` directly** (build order step 8).
  The shade detail screen offers a slider and Send per rail, and only for the
  rails the shade's capability claims. Each rail sends only its own field: the
  command frame carries an explicit unset sentinel per field, so leaving tilt
  alone is what the hardware is actually told, rather than the app re-sending a
  value it believes to be current.
- Controls explain themselves before they are pressed. `ActionRunner.checkReadiness`
  runs when the screen opens, and a blocked shade shows why — most often
  "Setup not finished", since no keystream exists until build order step 5.
  Nothing here can move a shade yet; the UI says that plainly instead of
  offering a button that silently does nothing.
- Positions are offered as percentages rather than Open/Close, with a note that
  PowerView's "0% is fully open" convention is inherited from the openHAB
  binding and not yet confirmed on hardware. A mislabelled button on a motor is
  worse than an unlabelled number.
- **The app has a real shade list** (build order step 7). `PowerViewApp` hosts a
  shade list grouped into rooms, and a per-shade detail screen for naming it,
  filing it in a room, marking it mains-powered, reading its battery and
  forgetting it. `MainActivity` hosts this instead of the debug scan screen,
  which becomes one route inside the app rather than the whole of it — it is
  still what confirms decoding against hardware, and `docs/PROTOCOL.md` §8 still
  has open questions for it to answer.
- Shades that have never been saved are listed separately, under "Found
  nearby", rather than mixed in with set-up ones. Naming one is what persists
  its `homeId`, and without a `homeId` a shade can never be commanded, so the
  screen says that rather than presenting the save as cosmetic.
- `ShadeFormattingTest` in `:ui` — 17 tests over room grouping, capability-gated
  position display and relative "last seen" text.
- `ActionResultTest` in `:data` — first unit tests outside `:protocol`, covering
  the aggregation the UI branches on to tell "the whole action is blocked on
  setup" apart from "some shades were unreachable".
- First hardware-captured test vector: `3C F8 08 00 00 09 00 00 C0`, sniffed
  from a Duette TDBU, pinned in `AdvertisementParserTest`. Every other vector
  in the suite is synthetic — built by the test and read back — which proves
  the parser is self-consistent but cannot prove the offsets match what a shade
  actually broadcasts. Nine real bytes is exactly what the field table
  consumes, so a layout shifted by one would not fit. The advertisement offsets
  are now confirmed, not merely assumed.
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
  keystream storage), `ActionStore` (saved `ShadeAction`s), `ActionRunner`
  (the single command-execution funnel).
- `:widget`: `CommandWorker` (`WorkManager` integration). Glance widget UI, the
  widget config activity, and the Quick Settings tile are stubbed with TODOs —
  deferred pending an Android SDK to build/verify them against. (`ActionRunner`
  started here and has since moved to `:data` — see Changed.)
- `:ui`: `DebugScanScreen` — the build-order-step-2 raw scan debug view.
  (`PowerViewApp`, `ShadeListScreen` and `ShadeDetailScreen` joined it later —
  see Added above.)
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
