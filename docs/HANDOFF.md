# Handoff

Written 2026-09-17, rewritten the same day at the end of a third session.
Branch `claude/load-dev-skills-d0bioe` — which is this repository's **default
branch**, not a feature branch; there is no `main` or `master` and no tags at
all — green in CI, with no release tagged. **The Dependabot queue is cleared:**
six PRs merged, one closed as superseded, nothing open. Step 5 is now the only
thing left; see "The dependency sweep" below for what the queue turned out to
be hiding.

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

**One thing is waiting: step 5.** The Dependabot queue that used to sit in
front of it is done. Two follow-ups it left behind are real but neither blocks
step 5, and both are things CI cannot answer — the built-in Kotlin migration
before AGP 10, and a visual check of the Compose BOM jump against the OLED
theme. Both are described under "The dependency sweep".

Build order progress (numbering follows the README):

| Step | State |
|---|---|
| 1 `:protocol` + unit tests | ✅ 40 tests |
| 2 Scanner + raw debug screen | ✅ **offsets confirmed on real hardware** |
| 3 Capability mapping + per-shade UI | ✅ detail screen offers only what a capability claims |
| 4 GATT connect + battery read | ✅ **first real GATT connection worked** |
| 5 Keystream import + first write | ⬜ **the only thing blocking real control** |
| 6 Derive-from-capture, tilt, secondary | ◐ tilt/secondary/queue done; the capture UI *is* step 5 |
| 7 Persistence, labels/rooms, actions | ✅ shade list, rooms, naming, action editor |
| 8 ActionRunner + CommandWorker | ✅ driven from in-app sliders |
| 9 Glance widgets | ✅ widget, grid, config activity, per-instance state |
| 10 Battery sweep + notifications | ✅ configurable sweep, notification, battery widget |
| 11 Quick Settings tile + shortcuts | ✅ tile, and four dynamic shortcuts |
| 12 Home Assistant bridge | ⬜ optional |

Two things that are not build-order steps landed in the second session and
are easy to miss in that table: a **theme picker** (Follow system / Light /
Dark / Black (OLED)) and a **battery widget** with a **configurable sweep
interval**. The CHANGELOG carries the design reasoning for each — why the
OLED scheme's containers are deliberately not black, and why the battery
widget never connects to a shade.

---

## What changed in the second session

Six commits, all green in CI except one that was red for eleven minutes and
is described below because the reason is worth knowing.

1. **A theme picker with an OLED black option** (`161b57c`) — not a
   build-order step, asked for directly. `SettingsStore`/`ThemeMode` in
   `:data`, `PowerViewTheme` and a settings screen in `:ui`, and the shades
   app bar's two text buttons collapsed into a `More` overflow to make room
   for a third destination.
2. **Step 9, the Glance widget** (`9193ad2`) — one to six buttons per widget
   instance, a configuration activity, and results written back to every
   widget showing the action.
3. **Step 11, the Quick Settings tile** (`14a31ae`).
4. **The tile stopped working from the lock screen** (`7add46c`), at the
   user's request — see below.
5. **A battery widget, and the fix for the red build** (`73a4906`).
6. **The battery sweep interval became a setting** (`05af903`).

### The red build, and what it says about the local harness

`e10c16b` (launcher shortcuts) failed CI. `ActionShortcuts` was declared
`internal` and `PowerViewApplication` calls it — and `internal` is per Gradle
module, so `:app` could not see it. Thirty-eight tests had passed locally
against code that compiled nowhere.

**This is a class of error the off-device harness cannot catch by
construction**, because it compiles files inside a single module where
`internal` always resolves. The remedy is in "Verifying work without an
Android SDK" below: a grep that checks every `:app` reference into another
module is `public`. Run it before any push that adds one.

### The battery work, which is most of what this app is for

The weekly sweep and the low-battery notification already existed and had
nothing between them: a notification fires only on a threshold crossing, and
otherwise the app had to be opened. So:

- **`BatteryWidget`** lists every battery-powered shade, worst first, with a
  header counting the low and the unread. It **never connects to a shade** —
  reading a battery spends the power being measured, so it renders only what
  `ShadeStore` holds, and a tap opens the app where the per-shade "Read
  battery" button is the deliberate way to spend that power.
- Two things it refuses to imply, both tested: a never-read shade shows "Not
  read yet" rather than 0% and does **not** count as low, because unknown is
  not low; and a reading older than two sweep periods carries its age rather
  than posing as current. Never-read shades sort above even the flattest
  known one.
- **The sweep interval is a setting** — daily to monthly, or off, defaulting
  to weekly so an existing install is unchanged. Off comes with a "Check now"
  button, because otherwise Off means "never see a reading again". A change
  takes effect immediately (`CANCEL_AND_REENQUEUE`); app start still uses
  `KEEP` so the period is not restarted on every launch.
- The widget's staleness threshold **follows that setting** (two periods)
  rather than a fixed fortnight, which would mark everything permanently
  stale on a monthly sweep and stay silent through a fortnight of failures on
  a daily one.

One latent bug was caught here and is worth remembering: all three settings
share one DataStore, so without `distinctUntilChanged` **changing the theme
re-emitted the sweep interval and rescheduled the sweep**, restarting its
period every time someone toggled dark mode — which could have stopped sweeps
firing at all. Nothing in testing would have shown that.

### The command surfaces, and the one funnel under them

There are now four ways to run a `ShadeAction`: the in-app sliders, the
Glance widget, the Quick Settings tile and a launcher shortcut. All four go
through `CommandDispatch` → `CommandWorker` → `ActionRunner`, so none of them
owns BLE code and a change to command behaviour lands everywhere at once.

Things in there that look like details and are not:

- **Not expedited `WorkManager` work.** Below API 31 an expedited request is
  satisfied by promoting the worker to a foreground service, which needs
  `getForegroundInfo()` — whose default throws — plus two
  `FOREGROUND_SERVICE*` permissions and a declared service. At `minSdk = 26`
  that is a crash on Android 8–11, not a degraded experience.
- **Tap debounce is three layers deep** (composition, stored state,
  `ExistingWorkPolicy.KEEP` on a per-action unique name) because a duplicate
  tap costs a connect/disconnect cycle on the *shade's* battery.
- **Success clears a widget button rather than showing a tick.** The widget
  knows a frame was acknowledged, not that a shade moved.
- **The tile does nothing from the lock screen**, and a locked tile shows
  neither the action's name nor its last result — an action is named for a
  room and a thing done to it. The spec asked for a lock-screen control; the
  user did not want one. To reverse it: `onClick`, plus the `locked` branch
  in `tileLabel`/`tileSubtitle`.
- **`RunActionActivity` is not exported**, verified against AOSP rather than
  assumed: the system starts a shortcut's intent under the *publishing* app's
  identity (`LauncherAppsService.startShortcutInner` — "Note the target
  activity doesn't have to be exported"). Exporting it, the easy reflex,
  would let any installed app move the shades.

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

## The dependency sweep — done, and what it was hiding

**All seven Dependabot PRs are resolved.** Six merged, #4 closed as superseded.
Nothing is open. The previous version of this document laid out a suggested
order and called it "a plan, not a verdict" — which was the right caution,
because the plan was wrong in three places and only building them showed it.

| PR | Bump | Outcome |
|---|---|---|
| #5 | security-crypto `1.1.0-alpha06` → `1.1.0` | merged first — needed nothing else |
| #2 | junit-jupiter `5.11.4` → `6.1.3` | merged |
| #8 | `actions` group ×3 | merged |
| #3 | gradle-wrapper `8.14.3` → `9.7.1` | merged |
| #4 | agp `8.7.3` → `9.4.0` | closed; landed directly instead |
| #1 | `minor-and-patch` ×5 | merged, after AGP 9 + compileSdk 37 |
| #6 | compose-bom → `2026.09.00` | merged, same |

### What it did not do: fix a single vulnerability

Worth being blunt about, because "cleared the Dependabot queue" reads like
security work and this was not that. **No known vulnerability was fixed,
because none was ever reported.** GitHub has two separate Dependabot features
and this repository has only one of them switched on:

- **Version updates** — driven by `.github/dependabot.yml`, scheduled weekly.
  On. All seven PRs were these.
- **Security updates / alerts** — driven by the GitHub Advisory Database,
  raised only when a dependency matches a published advisory. **Off.** The API
  says so outright:

```
GET /repos/darthrater78/hunter-douglas-blind/dependabot/alerts
403  "Dependabot alerts are disabled for this repository."
```

No CVE or GHSA identifier appears anywhere in the seven PR bodies; the only
occurrence of the word "vulnerabilities" is inside a boilerplate badge URL. So
by construction the sweep could not have fixed one.

Two claims that are easy to conflate, and should not be:

- `androidx.security:security-crypto` alpha06 → stable 1.1.0 is a **supply
  chain maturity** improvement — a pre-release library guarding the app's only
  credential — **not** a patch for a known exploit. The package having
  "security" in its name makes the stronger reading tempting.
- The gate file's "0 Critical / 0 High" is a review of the **diff**, which is
  what dev-skills means by that gate. It is **not** a CVE scan of the
  dependency tree, and no such scan has ever run here — Gradle cannot resolve
  the Android tree with `dl.google.com` blocked.

What the sweep genuinely bought is **currency**, which lowers future exposure
without measuring present exposure. The honest status of this project's
dependencies is *unknown*, not *clean*.

**The fix is a repository setting, and only the owner can flip it:** Settings →
Code security → enable **Dependabot alerts** and **Dependabot security
updates**. Until that is on, nothing is watching this project for CVEs at all,
and dev-skills §4.1 expects exactly that watch. A one-off `osv-scanner` run
from a machine that can reach Google Maven would give a present-tense answer
for the tree as it stands.

The security-shaped work that *was* done this session was defensive rather than
remedial: both action SHAs resolved against their upstream tags, the
`setup-gradle` pin caught claiming v4.4.4 while pointing at v4.4.3, and the
widened trigger confirmed to be `pull_request` rather than `pull_request_target`
(read-only token, no secrets, so a fork PR cannot reach anything). Useful, and
none of it a vulnerability fix.

### The three things the plan got wrong

**1. Four of the seven had never been built.** Not red — *empty*. Their base
predated `ci.yml`'s `push: branches: ['**']`, and the `pull_request` trigger
read `branches: [main, master]`, which matches nothing in a repository whose
default branch is `claude/load-dev-skills-d0bioe`. Neither trigger fired. An
empty check list reads like "fine" in a way a red X never does, and that is how
#1 and #8 came to be described as "low risk by definition" without a verdict
behind either. Both triggers are `['**']` now.

If you ever need a verdict on a stale PR here: GitHub's "Update branch" works
(`update_pull_request_branch`), and it fires a push event that CI does answer.
`@dependabot rebase` does **not** work from an agent — the mention is stripped
before it reaches Dependabot.

**2. "minor-and-patch" said nothing about risk.** Every bump in #1 failed, and
so did all 22 artifacts in #6, on the same condition: the AndroidX artifacts
require AGP ≥ 9.1.0 *and* `compileSdk` ≥ 37. A minor bump of a library can
demand a major bump of the build plugin. So #1 and #6 were not the easy
warm-up items — they were downstream of the hardest one, and the queue's real
shape was the opposite of the order it was written in.

**3. Gradle 9 and AGP 9 are coupled one way, not both.** This document
previously said "AGP 8.7.3 will not run on Gradle 9 — so merging either alone
breaks the build", and held both back as one large change. Gradle 9.7.1 built
green on AGP 8.7.3. The real constraint, from #4's own failure, is:

```
Minimum supported Gradle version is 9.6.0. Current version is 8.14.3.
```

AGP 9 needs Gradle ≥ 9.6.0; Gradle 9 does not need AGP 9. So the wrapper went
first, alone, and the AGP major became a separately-gated change instead of a
two-major migration landing at once. That claim was inherited rather than
measured — the same failure mode this project has already recorded twice
against the openHAB binding.

### AGP 9 landed with built-in Kotlin switched off

AGP 9.0 enables built-in Kotlin by default, which turns applying
`org.jetbrains.kotlin.android` into a hard error rather than a redundancy. Five
modules apply it, plus the root build file, so the documented migration is
genuinely small — delete six lines and the catalog entry.

It was **not** taken, and the reason matters. Built-in Kotlin compiles with the
Kotlin that AGP bundles, and that has to agree with the Compose compiler plugin
pinned at the catalog's `kotlin = 2.4.20`. Which Kotlin AGP 9.4.0 bundles
cannot be established from this container at all, so taking it would have been
a guess costing a CI round trip on the default branch. `android.builtInKotlin=false`
and `android.newDsl=false` keep the pairing that already builds green.

**This is owed, with a hard deadline: AGP 10.0 removes the opt-out.** The steps
are recorded in `gradle.properties` beside the flags, and the one open question
is whether the Compose plugin pin must move with AGP's bundled Kotlin. Guide:
https://developer.android.com/build/migrate-to-built-in-kotlin

### compileSdk 37, targetSdk still 35

`compileSdk` is 37 because the AndroidX artifacts demanded it. `targetSdk`
stays at 35 deliberately: it opts the app in to new *runtime* behaviour and
wants testing on a device, and this app cannot yet move a shade. The README
flags targetSdk 35 as likely at or below the Play Store floor — real, and a
release decision with its own gate, not part of a dependency sweep. The
reasoning is pinned beside the value in `libs.versions.toml` so the gap does
not read as an oversight.

### The one check nobody has run

**The Compose BOM jump is verified only as "it compiles".** 2024.12.01 →
2026.09.00 is close to two years of Material 3 across 22 artifacts, and the
OLED theme leans on the `surfaceContainer` roles and `surfaceTint` — exactly
the kind of thing that shifts over that span. Nothing in the container can
render a screen.

Install the debug APK CI uploads and look at the shade list, the detail screen
and the settings screen under **Black (OLED)** and under **Light**, at
container backgrounds and at whether the OLED scheme's deliberately-not-black
containers still read as intended. It is its own commit, so a revert is cheap
if they do not.

### Action pins, and a lesson that repeats

`actions/setup-java` v6.0.1 and `actions/upload-artifact` v7.0.1 were taken
after resolving both SHAs against the upstream tags directly, rather than
trusting the comments beside them. The `gradle/actions/setup-gradle` pin was
labelled `# v4.4.4` while pointing at **v4.4.3**, both before and after #8 —
Dependabot moved it from the annotated tag object to the commit, which is the
correct form, and carried the wrong label across. The label was corrected down
to v4.4.3 rather than the pin moved up, because the label is what Dependabot
reads to decide what to offer next; gradle/actions is on v6.0.1 now, so expect
an offer.

This is the third time a pin comment has mattered in this repository. CI here
was dead for the project's entire history because setup-gradle was pinned to a
SHA in no tag at all. Resolve the SHA; do not read the comment.

### Note on this branch being the default branch

`claude/load-dev-skills-d0bioe` is the repository's default branch. There is no
`main`, no `master`, and no tags. That is why Dependabot targets it without any
`target-branch` setting in `dependabot.yml`, and it is worth knowing before
applying any rule that says "merges to the default branch are releases" — by
the letter, every dependency merge here is one. They were treated as work
commits, which is the honest reading while nothing is versioned, tagged or
published. If a real release is ever cut, creating a real `main` is the tidier
fix.

---

## Next step: step 5 — everything else is done

**Step 5 stays last.** It was deferred deliberately, and the user reconfirmed
that when this session offered to start it. An earlier version of this
document recommended pulling it forward; that recommendation is withdrawn, and
the reasoning is kept below only because the decision it records is still open
and will still be needed when step 5's turn comes.

**Step 5 is now the only thing left**, and step 6 comes with it rather than
before it. That is not a scheduling preference: step 6's one remaining
deliverable is the guided derive-from-capture flow, which *is* keystream
onboarding — it ends in a keystream in `EncryptedSharedPreferences`, the same
spine step 5 introduces. Building it separately would be building step 5
under another number. The rest of step 6 is already done and was before this
session: `CommandQueue` is owned by `ShadeGattClient`, and tilt and secondary
have controls on the detail screen and rows in the action editor.

The one genuinely open piece of step 6 is **persisting the sequence
counter**, and it cannot be settled here. `ActionRunner` keeps an in-memory
per-shade counter that resets on process death; whether that matters depends
on whether the shade validates sequence monotonicity as replay protection,
which no one has observed. It is a `docs/PROTOCOL.md` §8 question, answerable
the first time a real write lands — which is step 5.

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

**Two blind spots this method does not cover. Both bit in one session.**

*Cross-module visibility.* The harness compiles files in a single Gradle
module, so `internal` always resolves — but `internal` is per module, and
`:app` calling an `internal` declaration in `:widget` is a hard error that
only CI sees. It cost a red build (run #22, `ActionShortcuts`). Before
pushing anything `:app` calls, run:

```
for sym in $(grep -o "com\.scrivtech\.powerview\.widget\.[A-Za-z]*" \
      app/src/main/kotlin/com/scrivtech/powerview/app/*.kt | sed 's/.*\.//' | sort -u); do
  grep -rhn "\(object\|class\|fun\|val\) $sym\b" widget/src/main/kotlin/ | head -1
done
```

Every hit must read `public`.

*The AndroidX sources on `androidx-main` are newer than the pinned version.*
Most signatures are stable across the gap, but not all: `LazyColumn` on main
takes a required `verticalScrollMode` that Glance 1.1.1 does not have. When a
signature looks newer than expected, either find the release branch or avoid
the API — the battery widget caps its rows instead of scrolling for exactly
this reason, which turned out to be the better design anyway.

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

**What is reachable from here, precisely — it is not "no Google".** The
network policy denies `dl.google.com`, and `maven.google.com` 301-redirects
there, so no AndroidX or AGP artifact or POM can be fetched and Gradle cannot
resolve an Android build. Confirm it yourself with
`curl -sS "$HTTPS_PROXY/__agentproxy/status"`, which logs the rejected CONNECT.

But Google's *documentation* host is fine. `developer.android.com` and
`kotlinlang.org` both serve normally, and so does `github.com` over plain git —
`git ls-remote https://github.com/<owner>/<repo>` resolves any action's tags,
which is how the setup-gradle pin was caught pointing at v4.4.3 under a v4.4.4
label.

That distinction earned its keep in the third session. AGP 9 failed CI on the
`kotlin-android` plugin, and the obvious reflex — delete the plugin from six
files — would have been wrong in a way CI would have taken two more round trips
to reveal. Fetching
`developer.android.com/build/migrate-to-built-in-kotlin` instead showed both
the migration *and* the documented opt-out, and the note that built-in Kotlin
compiles with AGP's bundled Kotlin, which is what makes the Compose plugin pin
a live question. One fetch, one correct commit.

So: **artifacts no, documentation yes.** Read the docs before guessing at an
API or a migration, exactly as the Glance work did against the AndroidX
sources.

This paid for itself in an earlier session too: the harness caught a compile
error in a new test file before CI saw it. **When you write new logic, put the pure part in a file with no
Android imports so it can be checked this way.** That is why `groupIntoRooms`
and the outcome-wording functions live in `ShadeFormatting.kt` rather than
inside the Compose files that use them.

Test counts as of this commit, by annotated test method:

| Module | Methods | Files |
|---|---|---|
| `:protocol` | 20 | 18 `@Test` + 2 `@ParameterizedTest` |
| `:data` | 4 | `ActionResultTest` |
| `:ui` | 47 | `ShadeFormattingTest`, `ActionDraftTest`, `ThemeSelectionTest` |
| `:widget` | 50 | `WidgetPresentationTest`, `BatteryWidgetPresentationTest` |

Earlier versions of this document said "40 in `:protocol`". That is the
*executed case* count CI reports — the two parameterised tests expand — not
the method count, and it cannot be checked from the container because
`:protocol:test` fails at the root plugin block before it reaches the module.
Both numbers are right about different things; this table counts methods,
which is the one you can verify here with `grep -c '@Test'`.

Of those, 62 run in the off-device harness (everything in `:ui` and
`:widget` that has no Android imports, plus the `:data` pure files).

Also verifiable locally: workflow files with `actionlint`.

**Everything Compose remains CI-verified only.** Budget a round trip for it.

---

## CI status

**Green on the branch head**, including `assembleRelease` with R8, which is
where `lintVitalRelease` runs. Every commit on this branch has been seen by a
compiler.

The third session's runs went #33–#37 ✅ (the Dependabot merges, most of them
cancelled by the next merge landing — see below), **#38 ❌ AGP 9**, #39 ✅ the
built-in Kotlin opt-out, #40 ✅ compileSdk 37, then the #1 and #6 merges.

Two genuine failures exist in this branch's history and both are fixed rather
than papered over:

- **#22**, `ActionShortcuts` was `internal` where `:app` needed it public. See
  "The red build" above for why the local harness cannot catch that class of
  error at all.
- **#38**, AGP 9.4.0 rejecting the `org.jetbrains.kotlin.android` plugin. Fixed
  forward in the next commit rather than reverted, because the cause was
  named precisely in the log and the fix was a documented flag. See "The
  dependency sweep" above.

The `cancelled` runs are **not** failures. CI sets `cancel-in-progress: true`
on its concurrency group, so a run dies when the next push to the same branch
starts. Merging six PRs in quick succession produced a row of them. If you push
twice in quick succession, read the *later* run.

That group is now keyed on `github.head_ref || github.ref_name` — note
`ref_name`, not `ref`. The `pull_request` trigger overlaps `push` in this
repository, and `head_ref || ref` does *not* collapse the pair (`<branch>`
versus `refs/heads/<branch>`), which quietly cost a duplicate build on every
pull request until it was caught.

```
https://github.com/darthrater78/hunter-douglas-blind/actions
```

## Blocked, not skipped

These were identified in the audit and cannot be completed from the sandbox:

- **Gradle dependency locking and `gradle/verification-metadata.xml`.** Both are
  generated from a successful dependency resolution, which needs Google Maven.
  Worth doing from a normal dev machine — it is the only way to get a lockfile
  to audit against.
- **Currency of the pinned versions — answered.** The whole Dependabot queue
  is merged, so AGP, the Gradle wrapper, the AndroidX entries and the action
  pins are current as of 2026-09-17. `UNVERIFIED` markers were deleted as each
  bump landed. What the marker never meant is still worth keeping in mind: it
  said "known to work, not known to be current", and a green build says
  nothing about currency either. Dependabot is the thing that keeps answering
  this between security gates — leave it pointed here.
- **`androidx.security:security-crypto` is on the stable `1.1.0`** as of PR #5.
  That closes the alpha concern, which was pressing because step 5 is about to
  put a real keystream behind it. It does **not** settle the larger question:
  Jetpack has been steering away from `EncryptedSharedPreferences` entirely,
  and whether it is the right home for the keystream is a call still owed
  before a real release.
- **`navigation-compose` is in the version catalog but referenced by no module**,
  so its pin has never been resolved by any build. Screen state is currently a
  saved route string plus a MAC in `PowerViewApp` — deliberate, and less code
  than a `NavHost` at this size. If the screen graph grows, switching is easy,
  but expect the first build that references it to be the one that discovers
  whether 2.10.1 resolves against the pinned Compose BOM. It rode along in PR
  #1 and is therefore the one bump in that group that nothing has verified.

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
  one session means nothing in the next. The second session was given one
  ("continue the automatic commit for this work since there's so many steps")
  and it expired with it — **ask again**.
- Tag pushes and ref deletions are always handed to the user to run, never
  executed directly.
- Commit messages in this repo explain *why*, at length, and record what was
  verified versus what CI still had to check. Keep that up — it is most of the
  reason this document can be written at all.
