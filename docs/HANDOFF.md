# Handoff

Written 2026-09-17, updated the same day in a second session. Branch
`claude/load-dev-skills-d0bioe` — not merged, no PR open, no release tagged.

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

**Nothing in this app can move a shade yet.** The one remaining blocker is
keystream onboarding (step 5), which is deliberately last — the user
reconfirmed that ordering when this session offered to start it. Do not pull
it forward; steps 9 and 11 are built around it, and step 6 is really part of
onboarding anyway. The UI is built to say so
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
| 9 Glance widgets | ✅ widget, grid, config activity, per-instance state |
| 10 Battery sweep + notifications | ✅ weekly sweep, one summary notification |
| 11 Quick Settings tile + shortcuts | ◐ tile done; shortcuts **next** |
| 12 Home Assistant bridge | ⬜ optional |

Appearance is not a build-order step. A theme picker (Follow system / Light /
Dark / Black (OLED)) landed in the second session; see the CHANGELOG entry for
why the OLED scheme's containers are not themselves black.

---

## What changed in the second session

**Step 9 (the Glance widget) and most of step 11 (the Quick Settings tile).**
Both were written blind and both compiled first try, including through R8.

**The Quick Settings tile.** One designated action, run through the widget's
`CommandDispatch`. Its action is chosen in the app's settings because a tile
has nowhere to put a picker, and its last-run state is in memory on purpose —
there is one tile, so one value, and after a process death "last run failed"
is stale news nobody can act on from a tile. It is reachable from the lock
screen by design: a tap goes through `unlockAndRun` and a locked tile shows
neither the action's name nor its last result. The spec asked for a
lock-screen control and the user did not want one — if that ever reverses,
the whole of it is `onClick` and the `locked` branch in `tileLabel` /
`tileSubtitle`.

**Step 9, the Glance widget.** One to six buttons per widget instance, a
configuration activity to choose which saved actions they run, and results
written back to every widget showing the action. Three things in it are worth
knowing before touching it, all recorded in the CHANGELOG entry: it is
deliberately *not* expedited WorkManager work (that crashes below API 31 at
this project's `minSdk`), tap debounce is three layers deep because a
duplicate tap costs shade battery, and a success clears the button rather than
showing a tick, since the widget knows a frame was acknowledged and not that
anything moved.

Verifying it was the interesting part. The container has no Android SDK and no
Glance to compile against, so every Glance call was checked against the
AndroidX sources on GitHub before being written — which settled, among others,
that `provideContent` is a top-level extension needing an import while
`update` is a member needing none, and caught that a leading-dot class name in
a *library* manifest resolves against the app's `applicationId` rather than
the module's namespace, so the receiver and config activity are named in full.
Everything that could be pulled out of Glance's way lives in
`WidgetPresentation.kt` and is tested off-device.

**A theme picker with an OLED black option** — not a build-order step, asked for
directly. `SettingsStore`/`ThemeMode` in `:data`, `PowerViewTheme` plus a
settings screen in `:ui`, and the shades app bar's two text buttons collapsed
into a `More` overflow to make room for a third destination. The design
reasoning (why the OLED containers are not themselves black, why `surfaceTint`
has to be black, why OLED ignores the system setting) is in the CHANGELOG
entry rather than repeated here.

## What changed in the first session

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

## Next step: shortcuts, then step 6 — with step 5 still last

**Step 5 stays last.** It was deferred deliberately, and the user reconfirmed
that when this session offered to start it. An earlier version of this
document recommended pulling it forward; that recommendation is withdrawn, and
the reasoning is kept below only because the decision it records is still open
and will still be needed when step 5's turn comes.

So the work in front of you is **the rest of step 11: launcher shortcuts**.
The tile landed; shortcuts did not. What they need is a transparent
trampoline activity that reads an action id from its intent, calls
`CommandDispatch.enqueue` and finishes, plus dynamic shortcuts published
whenever the action list changes. The awkward part is deciding *when* to
republish them, since nothing currently observes `ActionStore` outside a
screen.

After that, step 6 (the guided derive-from-capture UI) is the last thing
before step 5, and it is really part of keystream onboarding, so it may be
worth taking together with it.

### The step 5 decision, when its turn comes

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
(strip `@Serializable` in the harness only), `ActionResult.kt`, `ThemeMode.kt`,
the `:ui` files `ShadeFormatting.kt`, `ActionDraft.kt` and `ThemeSelection.kt`,
and `:widget`'s `WidgetPresentation.kt`.

**The other half of verifying blind: read the API before calling it.** The
Glance work was written against the AndroidX sources on GitHub
(`raw.githubusercontent.com/androidx/androidx/androidx-main/glance/...`),
which is reachable from here even though Google Maven is not. Checking a
signature costs one fetch; guessing one costs a CI round trip, and guessing
wrong about whether something is a member or a top-level extension is a
coin flip either way — an unresolved import and a missing import are both
hard errors. `BatteryLevel`/`batteryLevelOf`
live inside `BatteryReader.kt`, which imports Android, so the harness needs a
small verbatim copy of just those declarations.

This paid for itself this session: it caught a compile error in a new test file
before CI saw it. **When you write new logic, put the pure part in a file with no
Android imports so it can be checked this way.** That is why `groupIntoRooms`
and the outcome-wording functions live in `ShadeFormatting.kt` rather than
inside the Compose files that use them.

Test counts as of this commit: 40 in `:protocol`, 4 in `:data`
(`ActionResultTest`), 44 in `:ui` (`ShadeFormattingTest` 26, `ActionDraftTest` 9,
`ThemeSelectionTest` 7, `TileActionDescriptionTest` 2), 20 in `:widget`
(`WidgetPresentationTest` 17, `TilePresentationTest` 6).

Also verifiable locally: workflow files with `actionlint`.

**Everything Compose remains CI-verified only.** Budget a round trip for it.

---

## CI status

**Green on the branch head.** Run #14 on `0113457` passed, including
`assembleRelease` with R8, which is where `lintVitalRelease` runs. That run
also settles the previous session's open question: `0113457` carries the same
battery-sweep code as `3fe7a27`, so the sweep compiles and no commit on this
branch is now unverified by a compiler.

The two `cancelled` runs in the history (`3fe7a27`, `e0d459b`) are not
failures. CI sets `cancel-in-progress: true` on a per-ref concurrency group, so
each was killed by the push that followed it.

```
https://github.com/darthrater78/hunter-douglas-blind/actions
```

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
