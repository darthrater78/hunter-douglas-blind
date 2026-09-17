# Handoff

Written 2026-09-17. Branch `claude/load-dev-skills-d0bioe` at `3fe7a27` — not
merged, no PR open, no release tagged.

This is the state-of-play document for whoever picks the project up next. The
README describes what the app is meant to be; this describes what is actually
true about it today, which is not the same thing.

---

## Start here

Two distinctions carry most of the useful information in this project.

**Confirmed against real hardware versus assumed.** The scaffold was written
without any shade present, so a lot of it was reasonable-but-unverified — and
two of those assumptions turned out to be wrong the first time a real device
was in range. Keep that distinction alive as you work; it is why the debug
screen still shows raw bytes next to decoded fields, and why the control
sliders are labelled with percentages rather than "Open" and "Close".

**Nothing in this app can move a shade yet.** Every other build-order step is
done. The one remaining blocker is keystream onboarding (step 5), which was
deliberately deferred to last at the user's request. The UI is built to say so
out loud rather than failing opaquely — see "The no-keystream state" below.

Build order progress (numbering follows the README):

| Step | State |
|---|---|
| 1 `:protocol` + unit tests | ✅ 40 tests |
| 2 Scanner + raw debug screen | ✅ **offsets confirmed on real hardware** |
| 3 Capability mapping + per-shade UI | ✅ detail screen offers only what a capability claims |
| 4 GATT connect + battery read | ✅ **first real GATT connection worked** |
| 5 Keystream import + first write | ⬜ **the only thing blocking real control** |
| 6 Derive-from-capture, tilt, secondary | ⬜ core classes exist, no UI |
| 7 Persistence, labels/rooms, actions | ✅ shade list, rooms, naming, action editor |
| 8 ActionRunner + CommandWorker | ✅ driven from in-app sliders |
| 9 Glance widgets | ⬜ TODO stubs only |
| 10 Battery sweep + notifications | ✅ weekly sweep, one summary notification |
| 11 Quick Settings tile | ⬜ TODO stub only |
| 12 Home Assistant bridge | ⬜ optional |

---

## What changed this session

Five commits, each green in CI (`1a99674`, `9e7760c`, `dac12fe`, `1a99a41`,
`3fe7a27` — confirm the last one, see "CI status" below).

1. **The command funnel moved from `:widget` to `:data`** and stopped collapsing
   every failure into `false`. `CommandOutcome` now separates reasons knowable
   without touching BLE (`NotAttempted`: no keystream, no `homeId`, unknown
   shade, missing permission, Bluetooth off, malformed MAC) from
   `TransportFailed`, which carries the stage it reached.
2. **The app became a real app.** `MainActivity` hosted the debug scan screen
   and that was the whole of it; now `PowerViewApp` hosts a shade list grouped
   into rooms, a per-shade detail screen, an actions list and an action editor.
   The debug screen is one route inside it, still reachable.
3. **In-app controls** (step 8): a slider and Send per rail, calling
   `ActionRunner.send` directly.
4. **Saved actions are reachable**: create, edit, run, delete.
5. **The weekly battery sweep** (step 10) with one summary notification.

### The `NotAttempted` / `TransportFailed` split is about physics, not errors

Worth understanding before touching `ActionRunner` or any UI that reports a
command. These frames move something. `NotAttempted` means nothing was
transmitted and the shade certainly did not move. `TransportFailed(WRITE)` means
a frame may have landed with only the acknowledgement lost, so the shade's real
position is unknown until its next advertisement. The UI text says "may or may
not have moved" for exactly that case, and that wording is load-bearing — do
not tidy it into something confident.

The same reasoning drives two other rules already implemented: retry is offered
only for `TransportFailed` (no `NotAttempted` reason is fixed by pressing again),
and an action whose shades were *all* blocked before BLE reports the single
cause once rather than listing every shade as failed.

### The no-keystream state

With step 5 last, `NotAttempted.NoKeystream` is the expected outcome of every
command in the app. `ActionRunner.checkReadiness` runs when a detail screen
opens, and a blocked shade shows "Setup not finished" with an explanation
instead of a live-looking button. When you build step 5, that is the state that
should stop appearing — it is a good end-to-end signal that onboarding worked.

---

## The test shade

One physical device has been used for every hardware confirmation so far.
Anything below that is not reproduced on a second unit should be treated as
"true of this shade", not "true of the protocol".

```
MAC      C6:83:B4:47:08:51
homeId   63548  (0xF83C)
typeId   8      -> capability 7, TOP_DOWN_BOTTOM_UP (Duette TDBU)
payload  3C F8 08 00 00 09 00 00 C0     (9 bytes, company ID stripped)
battery  65%    (read over GATT from 0x2A19)
RSSI     -62 to -70 dBm at normal room distance
```

That payload is pinned as a regression test in `AdvertisementParserTest`. It is
the **only** non-synthetic vector in the suite: every other test builds a
payload and reads it back, which proves the parser is self-consistent but can
never prove the offsets match what a shade actually broadcasts.

---

## Confirmed against real hardware

- **Advertisement offsets** (`docs/PROTOCOL.md` §2). Nine real bytes is exactly
  what the field table consumes — homeId(2) + typeId(1) + primary(2) +
  secondary(2) + tilt(1) + velocity(1) — with nothing left over and nothing
  missing. A layout shifted by one byte would leave a ragged edge.
- **Capability lookup.** typeId 8 resolves to `TOP_DOWN_BOTTOM_UP` as a known
  type, and a TDBU does have two rails and no tilt.
- **The GATT connect path.** `ShadeGattClient` connect → discover → read →
  disconnect works on real hardware. This also exercised the registration-leak
  fix, since every battery read is a full connect/disconnect cycle.
- **`secondary: 0.2%` is a real rail position**, not a decode artifact.

## Corrected by real hardware

Two documented claims were wrong. Both came from inferring protocol behaviour
from the openHAB binding rather than measuring it. **This is the single best
reason to distrust anything else inherited from that binding.**

1. **Battery is a percentage, not a coarse bucket.** `PROTOCOL.md` claimed
   `0x2A19` returned 10/50/100 for low/medium/high. The shade returned **65**.
   Corrected in the doc; the field is now `batteryPercent`, carrying
   `@SerialName("batteryBucket")` so readings already on users' devices still
   decode. *Do not drop that annotation* — with `ignoreUnknownKeys = true` a
   rename silently nulls stored data rather than erroring.
2. **`velocity` is probably not a velocity.** A stationary shade reported
   `0xC0` = `0b1100_0000`. Both high bits set reads like a flags/status byte.
   Recorded in §8 as an open question rather than renamed on one data point.

## Still assumed — treat with suspicion

- Everything in `docs/PROTOCOL.md` §8 (the open-questions list).
- **"0% is fully open."** Inherited from the openHAB binding, and the binding
  has now been wrong twice about this hardware. This is why the control sliders
  are labelled with bare percentages and carry an on-screen note rather than
  saying Open/Close. Confirm it on the first real write, then the labels can
  become words.
- The **command frame layout** (§3). Verified against five sniffed ciphertext
  vectors and it re-encrypts byte-for-byte, which is strong — but no frame has
  ever been written to a real shade. Step 5 is where that gets tested, and it is
  the first thing in this project that can physically move something.
- The **capability table** beyond typeId 8, ported from the openHAB binding.
- Whether the sequence byte is validated (replay protection) or ignored.
  `ActionRunner` keeps an in-memory counter that resets on process death; if
  the shade turns out to enforce monotonicity, that needs persisting.

---

## Next step: step 5, and the decision still open

**Keystream import and the first real write.** Everything underneath it is
confirmed, every surface that needs it is built, and it is the only thing
standing between this app and actually controlling a shade.

It needs onboarding UI for one of the three paths in `PROTOCOL.md` §7:

| Path | Needs | Notes |
|---|---|---|
| Import a known AES key | 32-char hex key for the home | Simplest UI. `FrameCipher.deriveKeystreamFromKey` already exists and is tested. |
| Derive from a capture | `btsnoop_hci.log` with an ATT write to `CAFE1001…` | `KeystreamDeriver` exists and is tested. UI is a guided multi-step flow — the most work. |
| Unenrolled shade | A factory-reset or never-enrolled shade | No key at all: send frames in the clear. Zero crypto UI. Best way to test the write path in isolation, but it costs a working shade's pairing. |

**The user was asked and expressed no preference.** The recommendation on the
table, and the reason for it: build **import-a-known-key** first. All three
paths end at the same place — a keystream in `EncryptedSharedPreferences`, a
"this home is unlocked" state, and a first-write screen. The import path builds
that shared spine plus a one-field entry point; derive-from-capture then becomes
a second entry point onto the same spine rather than a parallel stack, and the
unenrolled path becomes a flag that skips the cipher. Starting with either of
the others means building the spine anyway, wrapped in the most expensive
wrapper.

**When the first write happens, make it a small reversible movement** — a few
percent — not a full open. It is the first time this code can move a physical
object, and the frame layout is the least-verified thing in the project.

After step 5: step 6 (derive-from-capture UI), then 9 and 11 (Glance widgets and
the Quick Settings tile — both still TODO stubs, both already have their
`ActionRunner`/`CommandWorker` path built and green).

---

## Verifying work without an Android SDK

**The Android modules cannot be built in the Claude Code container.** No Android
SDK, and the network policy blocks `dl.google.com` / `maven.google.com`, so AGP
will not resolve. `./gradlew :protocol:test` fails there too — at the *root*
project's plugin block, before it reaches `:protocol`. CI is the compiler.

**But more can be checked locally than it first appears, and it is worth
doing.** Gradle 8.14.3 is cached and Maven Central is reachable, so any file
with no Android imports can be compiled and tested in an isolated project:

```
settings.gradle.kts:  repositories { mavenCentral() }  (+ gradlePluginPortal)
build.gradle.kts:     kotlin("jvm") version "2.4.20"; testImplementation junit
                      jvmTarget 17, options.release 17
                      (no toolchain block — the container has JDK 21 only)
run:  ./gradlew --project-dir <harness> test
```

Files that qualify today: all of `:protocol`, plus `Shade.kt`, `ShadeAction.kt`
(strip `@Serializable` in the harness only), `ActionResult.kt`, and the `:ui`
files `ShadeFormatting.kt` and `ActionDraft.kt`. `BatteryLevel`/`batteryLevelOf`
live inside `BatteryReader.kt`, which imports Android, so the harness needs a
small verbatim copy of just those declarations.

This paid for itself this session: it caught a compile error in a new test file
before CI saw it. **When you write new logic, put the pure part in a file with no
Android imports so it can be checked this way.** That is why `groupIntoRooms`
and the outcome-wording functions live in `ShadeFormatting.kt` rather than
inside the Compose files that use them.

Test counts as of this commit: 40 in `:protocol`, 4 in `:data`
(`ActionResultTest`), 35 in `:ui` (`ShadeFormattingTest` 26, `ActionDraftTest` 9).

Also verifiable locally: workflow files with `actionlint`.

**Everything Compose remains CI-verified only.** Budget a round trip for it.

---

## CI status

CI is green on `1a99674`, `9e7760c`, `dac12fe` and `1a99a41` — including
`assembleRelease` with R8, which is where `lintVitalRelease` runs.

`3fe7a27` (the battery sweep) was pushed at the end of the session and **its run
was still in flight**. It is the one commit here whose Android code has not been
seen by a compiler. Check it first:

```
https://github.com/darthrater78/hunter-douglas-blind/actions
```

If it is red, the likely suspects are the notification code in
`BatteryNotifier.kt` (the `@SuppressLint("MissingPermission")` is there
pre-emptively because `lintVitalRelease` is fatal on `assembleRelease`) and the
`PeriodicWorkRequestBuilder` generic call in `BatterySweepWorker.kt`. Nothing
else in that commit is new API surface.

---

## Blocked, not skipped

These were identified in the audit and cannot be completed from the sandbox:

- **Gradle dependency locking and `gradle/verification-metadata.xml`.** Both are
  generated from a successful dependency resolution, which needs Google Maven.
  Worth doing from a normal dev machine — it is the only way to get a lockfile
  to audit against.
- **Currency of the pinned versions.** `gradle/libs.versions.toml` marks AGP and
  the AndroidX entries `UNVERIFIED`. CI has built green with them, so they
  demonstrably *exist and work*; what is unknown is whether they are *current*.
  AGP 8.7.3 and `compileSdk`/`targetSdk` 35 are the ones to look at hardest, and
  targetSdk 35 may be at or below the Play Store's floor by now. Dependabot is
  configured for both ecosystems and can run.
- **`androidx.security:security-crypto` is at `1.1.0-alpha06`**, and it guards
  the only credential in the app. Jetpack has been steering away from
  `EncryptedSharedPreferences`. Make a deliberate decision before any real
  release rather than carrying an alpha forward by default. This gets more
  pressing the moment step 5 puts a real keystream in there.
- **`navigation-compose` is in the version catalog but referenced by no module**,
  so its pin has never been resolved by any build. Screen state is currently a
  saved route string plus a MAC in `PowerViewApp` — deliberate, and less code
  than a `NavHost` at this size. If the screen graph grows, switching is easy,
  but expect the first build that references it to be the one that discovers
  whether 2.8.5 resolves against the pinned Compose BOM.

---

## Release state

No release has been cut and no tag exists.

`release.yml` will **fail on purpose** until four repository secrets are set:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without
them `assembleRelease` emits `app-release-unsigned.apk`, which Android refuses
to install (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`). The workflow rejects that
rather than publishing a release that looks fine and is useless. Setup is in the
README under "Release signing".

For testing, CI uploads a **debug APK** on every push — debug-signed, installs
directly, no secrets involved. Artifacts expire after 14 days. That APK is now
worth installing: the app has a real UI to walk through, and everything except
moving a shade works.

---

## How CI got un-stuck (history worth keeping)

**CI had never successfully run** before the previous session.
`gradle/actions/setup-gradle` was pinned to a SHA that exists in no tag of that
repository, so every run died at "Setup Gradle" — and because `release.yml`
gates on a passing CI run, releases were unreachable too.

With that unblocked, the first four runs each surfaced a genuine pre-existing
defect that had simply never had the chance to fail: `kotlinOptions` removed in
Kotlin 2.x, a theme parent (`android:Theme.Material.DayNight.NoActionBar`) that
does not exist, WorkManager's auto-initializer conflicting with
`Configuration.Provider`, and Tink's compile-only annotations tripping R8.

The WorkManager one is the one to remember: it was a *functional* bug, not lint
noise. With the default initializer present, WorkManager comes up with its own
configuration before `PowerViewApplication`'s is read, `CommandWorker.Factory`
never registers, and every widget and tile command fails at worker
instantiation. It is also why `BatterySweepWorker` deliberately uses the default
reflective factory instead of being added to `CommandWorker.Factory`.

---

## Working conventions

- `.claude/dev-skills-gates.md` holds gate state and survives the container
  because it is committed. Read it before any git write.
- Commit approval does not carry across sessions. A standing approval granted in
  one session means nothing in the next.
- Tag pushes and ref deletions are always handed to the user to run, never
  executed directly.
- Commit messages in this repo explain *why*, at length, and record what was
  verified versus what CI still had to check. Keep that up — it is most of the
  reason this document can be written at all.
