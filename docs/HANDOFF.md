# Handoff

Written 2026-09-17, updated mid-fifth-session. The fourth session was the
first to run on the owner's build server rather than the sandboxed Claude Code
container; the fifth is running there too. Branch
`claude/load-dev-skills-d0bioe` is this repository's **default branch** (there
is no `main` or `master`, and no tags). No release has been tagged.

**The fifth session is a UI-quality pass, not build-order work** — see
"Session 5" immediately below before anything else in this document. Steps 1–4
and the build-order table further down describe state as of the end of
session 4 and are otherwise still current.

This is the state-of-play document for whoever picks the project up next. The
README describes what the app is meant to be; this describes what is actually
true about it today, which is not the same thing.

---

## Start here

Two distinctions carry most of the useful information in this project.

**Confirmed against real hardware versus assumed.** The scaffold was written
without any shade present, so a lot of it was reasonable-but-unverified, and
two of those assumptions turned out to be wrong the first time a real device
was in range. Keep that distinction alive as you work; it is why the debug
screen still shows raw bytes next to decoded fields, and why the control
sliders are labelled with percentages rather than "Open" and "Close".

**Nothing in this app can move a shade yet.** The one remaining blocker is
keystream onboarding (step 5), which is deliberately last; the user has
reconfirmed that ordering more than once. Do not pull it forward. The UI says
so out loud rather than failing opaquely; see "The no-keystream state" below.

**Where things stand:**

- **Build-order work left:** step 5 (and the part of step 6 that is really
  step 5).
- **Dependencies and CI:** nothing open. No Dependabot PRs, every catalog line
  current as of 2026-09-17, Dependabot alerts on with none reported, full lint
  gating CI, every action SHA verified.
- **Waiting on a phone:** three checks nothing on a server can do (below).
- **Builds locally.** On the build server,
  `./gradlew :protocol:test assembleDebug test assembleRelease lint` is exactly
  what CI runs, and it passes.

### Waiting on a phone

Install the debug APK and check:

1. **Themes after the Compose BOM jump.** 2024.12.01 → 2026.09.00 is close to
   two years of Material 3 across 22 artifacts, and the OLED theme leans on the
   `surfaceContainer` roles and `surfaceTint`. Look at the shade list, the
   detail screen and settings under **Black (OLED)** and **Light**: container
   backgrounds, and whether the OLED scheme's deliberately-not-black containers
   still read as intended. It is its own commit (`7acf5b6`), so a revert is
   cheap.
2. **Widgets on Glance 1.2.0 and WorkManager 2.11.2.** Both were bumped in the
   fourth session with no source changes. They carry both widgets, the tile and
   the command funnel. Add a widget, run an action (expect "Setup not
   finished"), and check the battery widget renders.
3. **Scanning on an Android 8–11 device**, if one is available. The fourth
   session fixed a bug that meant scanning could never have worked there (see
   "What changed in the fourth session"). The fix compiles and passes lint; it
   has not run. Expect a location permission prompt on first launch.

Build order progress (numbering follows the README):

| Step | State |
|---|---|
| 1 `:protocol` + unit tests | ✅ 40 test cases |
| 2 Scanner + raw debug screen | ✅ **offsets confirmed on real hardware**; API 26–30 scanning fixed but untried |
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
interval**. The CHANGELOG carries the design reasoning for each.

---

## Session 5: UI-quality pass (in progress)

The user redirected from build-order step 5 to a UI overhaul: the app is
functionally complete through step 4 but every screen is stock Material 3
with no icons, no accent color, and navigation buried behind a single text
"More" overflow menu — three things called out directly ("the UI needs a
total overhaul, there are no notification settings, and the widget is far too
large and can't be resized"). **Step 5 is still next once this is done** — it
was not pulled forward, and nothing about it changed.

A plan was written and approved before any code changed (Claude Code's plan
mode). Two decisions the user made explicitly, worth keeping if this session
gets interrupted: **add a real accent color** (not staying neutral, not
wallpaper-derived dynamic color), and **phased delivery** — each phase gets
its own build/verify/commit before the next starts, rather than one large
change.

### The four phases

1. **Notification settings** — ✅ done, committed `29e5961`, pushed.
   `SettingsStore` gained a fourth preference, `notificationsEnabled`
   (default true, same `Flow`/suspend-setter/`distinctUntilChanged` shape as
   the other three). `BatterySweepWorker` checks it immediately before the
   `BatteryNotifier` call and nowhere else — sweeps and readings are
   unaffected by the toggle, only the notification is. Settings screen got a
   toggle plus a link to `Settings.ACTION_APP_NOTIFICATION_SETTINGS` for
   sound/vibration/importance, which live in system settings for this app's
   one notification channel.

2. **Widget resize fix** — built and verified, **not yet committed** as of
   this writing. Root cause: neither `ShadeActionWidget` nor `BatteryWidget`
   overrode `GlanceAppWidget.sizeMode`, so Glance defaulted to
   `SizeMode.Single` — it composes **once**, at the widget's initial size,
   and never again. The manifest XML (`resizeMode="horizontal|vertical"` in
   both `*_widget_info.xml`) tells the *launcher* resizing is allowed, but
   nothing on the Glance side ever reacted to it, so the content was frozen
   at whatever size it first rendered — which is exactly "too large and
   can't be resized." Fix: both widgets now override
   `sizeMode = SizeMode.Responsive(...)` with a curated set of `DpSize`
   breakpoints (cell math `70dp * cells - 30dp`, which lines up exactly with
   `battery_widget_info.xml`'s declared 180x110dp minimum at 3x2 cells).
   `BatteryWidget` additionally reads `LocalSize.current.height` and feeds it
   into `batteryRowsToShow`'s existing `max` parameter (`maxRowsForHeight` in
   `BatteryWidget.kt`), so the row count adapts to the widget's actual size
   instead of clipping at the old fixed `MAX_BATTERY_ROWS = 5`.
   Resumed on 2026-09-18: `maxRowsForHeight` now takes the total row count
   and, when rows will be hidden, reserves room for the "N more — open the
   app" line first — at the 110dp minimum the old version showed two rows
   and clipped that line, the one telling you the list is incomplete. Row
   height estimate corrected 22dp → 24dp; `BatteryWidgetSizeTest` covers it.
   `ShadeActionWidget`'s grid stays driven by slot count, which is a content
   decision, not a size one, and was already correct. **Device check owed**:
   place both widgets, drag the resize handles through a few sizes, confirm
   content reflows instead of clipping — nothing about resizing can be
   confirmed from the build server.

3. **Navigation restructure** — not started. Bottom `NavigationBar`
   (Shades/Actions/Settings) replacing the text "More" `DropdownMenu`; Raw
   scan moves out of top-level nav into Settings as a "Developer" section
   entry (it's a hardware-verification tool, not a destination a shade owner
   needs daily); real `Icons.AutoMirrored.Filled.ArrowBack` icon button
   replacing the text "Back" `TextButton`. **Deliberately keeping the
   hand-rolled `route`/`selectedMac` state in `PowerViewApp.kt` rather than
   adopting `navigation-compose`** — it's pinned in the catalog but has never
   once been resolved by a build in this project's history, and a bottom nav
   plus one level of push screens is still comfortably within what the
   current approach handles. Recorded as a decision, not an oversight; the
   existing code comment about swapping it in "when the graph is big enough
   to earn it" still stands for later.

4. **Visual design refresh** — not started. Adds
   `androidx.compose.material:material-icons-core` (BOM-versioned, no
   separate pin) for real icons throughout — bottom nav, back arrow, the
   `ActionIcon` picker (`UP`/`DOWN`/`STOP`/`HALF`/`CUSTOM`) in
   `ActionEditorScreen.kt`, currently a row of text-label buttons because
   "the project pulls in no icon dependency" (a documented choice this phase
   deliberately reverses). Hand-authored Light/Dark `ColorScheme`s in
   `PowerViewTheme.kt` from a chosen accent seed hue, with `OledScheme`
   re-derived from the new `DarkScheme` the same way it already is today.
   Layout polish (card elevation/spacing, icon-leading headers, a FAB for
   "New action") scoped to refinement, not re-architecting any screen's
   layout. This is the phase where "does it actually look better" can only
   be judged on a phone — mandatory device review across all four themes
   before it's considered done.

### Gate notes for this session

Each phase runs the full CI task list locally
(`./gradlew :protocol:test assembleDebug test assembleRelease lint`) before
being proposed for commit — nothing deferred to "CI will catch it." Phase 1:
141 tests, 0 failures, clean lint, R8 release build green. Phase 2: same full
build green; `:widget`'s 50 tests (unaffected modules skipped by Gradle's
UP-TO-DATE checking, which is correct here — only `:widget` changed) also 0
failures. `.claude/dev-skills-gates.md` carries the live gate state and is
more current than this section by construction — read it first if resuming.

If this session ends mid-phase, the plan above (phases, file lists, the
`sizeMode`/`maxRowsForHeight` reasoning, the navigation-compose decision) is
everything needed to resume without re-deriving it from the diff.

---

## What changed in the fourth session

The first session with an Android SDK and Google Maven, so the first that could
check things the earlier sessions had to infer. Seven commits, plus the owner's
merge of PR #9:

| Commit | What |
|---|---|
| `560e09e` | Docs corrected against what a local build and Google Maven showed |
| `7a4c3e5` | Built-in Kotlin adopted; the AGP 9 opt-out removed |
| `8decaad` | Android 8–11 scanning fixed, lint errors fixed, CI gated on full lint |
| `9935a96` | Five AndroidX lines bumped that Dependabot never offered |
| `cb0a090` | Docs for the above |
| `af0c7a2` | PR #9, setup-gradle 4.4.3 → 6.3.0 (merged by the owner) |
| `238fbb5` | setup-gradle kept on the open-source cache |

**`560e09e` does not build on its own.** A failed command in a chained commit
script skipped the Kotlin commit and pushed the docs commit first, which
removes `kotlin-android` from the catalog while the modules still apply it.
`7a4c3e5` was pushed straight after and says so. Only matters when bisecting.
The lesson is in "Working conventions".

### Scanning could never have worked on Android 8–11

Found because full `./gradlew lint` was run for the first time. It had never
passed and CI had it commented out, while `lintVitalRelease` (inside
`assembleRelease`) stayed green, so nothing noticed.

`ShadeScanner.hasScanPermission` checked `BLUETOOTH_SCAN`, which only exists
from API 31, so on API 26–30 it always read as denied and `scan()` closed
immediately. Fixing the check alone would not have been enough: below 31 a BLE
scan also needs `ACCESS_FINE_LOCATION`, a runtime permission there, and
`MainActivity` never requested it (its comment claimed the pre-31 permissions
were all install-time). Without it a scan starts and silently returns nothing.

- `ShadeScanner.scanPermission()` names the right permission per API level.
- `MainActivity` requests `ACCESS_FINE_LOCATION` below 31. It was already
  declared with `maxSdkVersion="30"`; nothing new is declared.
- `stopScan` is always attempted on close (catching `SecurityException`), and
  `scan()` carries `@SuppressLint("MissingPermission")` like `ShadeGattClient`,
  because the permission lives in `:app`'s manifest where `:ble`'s lint cannot
  see it.

The other lint error was `QuickSettingsTile` calling the deprecated
`startActivityAndCollapse(Intent)`, which it already only does below API 34;
lint flags it regardless of the version branch, so it is suppressed with a
comment. Lint **warnings** remain (`OldTargetApi` on targetSdk 35, `UseKtx`,
`UnusedAttribute`, `ObsoleteSdkInt`); none gate CI.

### Built-in Kotlin, and the line that makes it work

The third session opted out of AGP 9's built-in Kotlin because it could not see
which Kotlin that would compile with, and it has to match the Compose compiler
plugin at `kotlin = 2.4.20`. AGP 9.4.0's POM depends on Kotlin 2.2.10, but the
root build file's `alias(libs.plugins.kotlin.jvm) apply false` puts
kotlin-gradle-plugin 2.4.20 on the shared build classpath, and
`./gradlew :ui:buildEnvironment` confirms 2.4.20 resolves. So `kotlin-android`
is gone from the five Android modules, the root build file and the catalog, and
`android.builtInKotlin` / `android.newDsl` are gone from `gradle.properties`.
Nothing Kotlin-related blocks AGP 10.

**That root `kotlin.jvm` line is load-bearing for every module.** Removing it
was tried: the build does not configure at all ("already on the classpath with
an unknown version"). The build file says so. When bumping `kotlin`, the
Compose plugin moves with it through the same catalog entry.

### Dependencies: an empty queue did not mean current

The third session concluded everything was current once the Dependabot queue
was empty. Checked against Google Maven directly, five AndroidX lines were
behind with no PR offering them, and are now bumped (no source changes):

| Catalog key | Was | Now |
|---|---|---|
| `activity-compose` | 1.9.3 | 1.13.0 |
| `lifecycle` | 2.8.7 | 2.11.0 |
| `datastore` | 1.1.1 | 1.2.1 |
| `work` | 2.10.0 | 2.11.2 |
| `glance` | 1.1.1 | 1.2.0 |

Why Dependabot skipped them is not established. The catalog's `UNVERIFIED`
markers are gone; every AndroidX/AGP line is marked `current` as of
2026-09-17. **Check Google Maven directly now and then** rather than reading an
empty queue as currency.

### Dependabot alerts are on

During the third session's sweep only *version updates* (driven by
`dependabot.yml`) were on; *alerts / security updates* were off
(`GET .../dependabot/alerts` → 403). The owner has since enabled both. The
alerts endpoint returns `[]`, so the advisory watch exists and reports nothing
open.

What that is and is not: GitHub's advisory match against the dependency graph,
not a lockfile audit. No `osv-scanner` run has happened; it is now possible from
the build server and is the stronger check before a release. The sweep itself
fixed no known vulnerability, because none was ever reported, and the
security-crypto move off alpha was supply-chain maturity, not a patch.

### setup-gradle v6.3.0 on the open-source cache

PR #9's SHA resolves to the upstream v6.3.0 tag. From v5 the action defaults to
`cache-provider: enhanced`, a **proprietary, closed-source** caching service
with its own terms of use. The owner chose `basic`, the open-source cache v4
used, and both `setup-gradle` steps (`ci.yml`, `release.yml`) set it with a
comment. CI's log confirms "Basic Caching". **If a future bump leaves CI
logging "Enhanced Caching", that setting has been lost.**

Also re-checked: every other action pin resolves to its labelled tag, and
`gradle-wrapper.jar` matches Gradle's published SHA-256 for 9.7.1.

### Also

- `SKILLS-RECOMMENDATION.md` (scratch findings for claude-vibe-skills) is
  deleted. Its content is retrievable from `de2c721`.
- `dependabot.yml`'s comment no longer points at the old markers, and says
  security updates come from repository settings, not that file.

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

**This is a class of error the single-module harness cannot catch by
construction**, because `internal` always resolves inside one module. A real
build on the build server does catch it. If you are ever back in the sandboxed
container, the grep in "Building and verifying" is the remedy.

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
`3fe7a27`).

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

## The third session: the dependency sweep

Kept short; the CHANGELOG and commit messages carry the detail. Seven
Dependabot PRs: #5 security-crypto → 1.1.0, #2 JUnit 6.1.3, #8 actions ×3, #3
Gradle wrapper 9.7.1, #1 AndroidX minor-and-patch ×5, #6 Compose BOM
2026.09.00, all merged; #4 AGP 9.4.0 closed and landed directly. Three lessons
that still apply:

1. **An empty check list is not a pass.** Four PRs had never been built, because
   the `pull_request` trigger read `branches: [main, master]` in a repo with
   neither. Both triggers are `['**']` now. To get a verdict on a stale PR, use
   GitHub's "Update branch"; `@dependabot rebase` does not work from an agent.
2. **"minor-and-patch" says nothing about risk.** A minor AndroidX bump demanded
   AGP ≥ 9.1.0 and `compileSdk` ≥ 37.
3. **Measure couplings rather than inheriting them.** "AGP 8 will not run on
   Gradle 9" was false. AGP 9 needs Gradle ≥ 9.6.0, not the reverse.

`compileSdk` is 37 because AndroidX demanded it. **`targetSdk` stays 35
deliberately**: it opts the app in to new runtime behaviour, wants device
testing, and is likely at or below the Play Store floor. That is a release
decision, pinned with its reasoning in `libs.versions.toml`.

**Pin comments lie; resolve the SHA.** CI was dead for the project's whole
early history because setup-gradle was pinned to a SHA in no tag, and later a
pin labelled v4.4.4 pointed at v4.4.3. `git ls-remote --tags <repo>` settles it.

**This branch is the default branch.** Dependabot targets it with no
`target-branch`, and by the letter of "merges to the default branch are
releases" every merge here is one. They are treated as work commits while
nothing is versioned, tagged or published. If a real release is cut, creating a
real `main` is the tidier fix.

---

## Next build-order step: step 5

**Step 5 stays last, and is next.** Step 6 comes with it rather than before it:
step 6's one remaining deliverable is the guided derive-from-capture flow,
which *is* keystream onboarding and ends in the same place. The rest of step 6
is done: `CommandQueue` is owned by `ShadeGattClient`, and tilt and secondary
have controls on the detail screen and rows in the action editor.

The one open piece of step 6 is **persisting the sequence counter**.
`ActionRunner` keeps an in-memory per-shade counter that resets on process
death; whether that matters depends on whether the shade validates sequence
monotonicity, which no one has observed. It is a `docs/PROTOCOL.md` §8
question, answerable the first time a real write lands.

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

## Building and verifying

### On the build server (normal case)

The server has an Android SDK at `~/Android/Sdk` (platforms 35 and 37) and
reaches Google Maven. `local.properties` with `sdk.dir=/home/serveradmin/Android/Sdk`
is ignored by git; create it if missing.

```
./gradlew :protocol:test assembleDebug test assembleRelease lint
```

That is exactly CI's task list. Run it before pushing. It builds the debug APK,
runs 141 test cases, exercises R8 on the release build and runs full lint.
Useful extras:

- `./gradlew :ui:buildEnvironment`: which Kotlin Gradle plugin actually resolves.
- `https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml`:
  the real latest version of any AndroidX artifact, independent of Dependabot.
- `git ls-remote --tags https://github.com/<owner>/<repo>`: what an action pin
  really points at.

What it cannot do: render a screen, or touch a shade. Compose appearance, widget
behaviour and anything BLE still need a phone.

Test counts, by annotated test method:

| Module | Methods | Files |
|---|---|---|
| `:protocol` | 20 | 18 `@Test` + 2 `@ParameterizedTest` |
| `:data` | 4 | `ActionResultTest` |
| `:ui` | 47 | `ShadeFormattingTest`, `ActionDraftTest`, `ThemeSelectionTest` |
| `:widget` | 50 | `WidgetPresentationTest`, `BatteryWidgetPresentationTest` |

121 methods; `:protocol`'s two parameterised tests expand its 20 to 40 cases,
for **141 executed**, which is what Gradle and CI report.

**Keep putting pure logic in files with no Android imports.** It is why
`groupIntoRooms` and the outcome-wording functions live in `ShadeFormatting.kt`
rather than inside Compose files, and it keeps them unit-testable on the JVM.

### In the sandboxed Claude Code container (fallback)

Sessions 1–3 ran there. No Android SDK, and the network policy denies
`dl.google.com` (`maven.google.com` redirects to it), so AGP never resolves and
even `./gradlew :protocol:test` fails at the root plugin block. CI is the only
compiler there. Confirm the block with
`curl -sS "$HTTPS_PROXY/__agentproxy/status"`.

What still works there:

- **An isolated JVM harness** for files with no Android imports: a throwaway
  project with `kotlin("jvm") version "2.4.20"`, JUnit, `jvmTarget` 17 and no
  toolchain block (the container has JDK 21 only). Qualifying files: all of
  `:protocol`, `Shade.kt`, `ShadeAction.kt` (strip `@Serializable`),
  `ActionResult.kt`, `ThemeMode.kt`, `ShadeFormatting.kt`, `ActionDraft.kt`,
  `ThemeSelection.kt`, `WidgetPresentation.kt`. About 62 of the tests run there.
- **Documentation hosts are reachable** even though artifacts are not:
  `developer.android.com`, `kotlinlang.org`, plain-git `github.com`, and the
  AndroidX sources at `raw.githubusercontent.com/androidx/androidx/androidx-main/...`.
  Read the API or migration guide before guessing; one fetch beats a CI round
  trip. Beware that `androidx-main` is newer than the pinned releases.

Its blind spot: the harness compiles everything in one module, so `internal`
always resolves, and `:app` calling an `internal` declaration in `:widget`
only fails in CI (run #22). Before pushing anything `:app` calls from there:

```
for sym in $(grep -o "com\.scrivtech\.powerview\.widget\.[A-Za-z]*" \
      app/src/main/kotlin/com/scrivtech/powerview/app/*.kt | sed 's/.*\.//' | sort -u); do
  grep -rhn "\(object\|class\|fun\|val\) $sym\b" widget/src/main/kotlin/ | head -1
done
```

Every hit must read `public`.

---

## CI status

**Green on the head (`238fbb5`)**: `:protocol` tests, debug APK, unit tests,
release APK with R8, and full lint. `Lint workflows` (actionlint) is green too.

Genuine failures in this branch's history, all fixed forward:

- **#22**, `ActionShortcuts` was `internal` where `:app` needed it public.
- **#38**, AGP 9.4.0 rejecting `org.jetbrains.kotlin.android`; opted out, then
  migrated in the fourth session.
- **`560e09e`**, pushed out of order and not buildable alone (see the fourth
  session). Its CI run failed; `7a4c3e5`, pushed a minute later, is green.

`cancelled` runs are **not** failures. CI's concurrency group has
`cancel-in-progress: true`, so a run dies when the next push to the same branch
starts; read the *later* run. The group is keyed on
`github.head_ref || github.ref_name` (not `ref`), which is what makes a PR's
`push` and `pull_request` runs collapse into one.

```
https://github.com/darthrater78/hunter-douglas-blind/actions
```

## Open, not blocking

- **Gradle dependency locking and `gradle/verification-metadata.xml`.** Were
  blocked on resolving the tree; the build server can now. Not done.
- **An `osv-scanner` run** against the resolved tree before any release.
- **Where the keystream lives.** security-crypto is on stable 1.1.0, but its
  `EncryptedSharedPreferences`/`MasterKey` APIs are deprecated upstream (the
  build warns in `KeystreamStore`). Whether it is the right home for a real
  keystream is a call owed before a release, and step 5 is when it starts to
  matter.
- **`navigation-compose` 2.10.1 is in the catalog but used by no module**, so
  no build has ever resolved it. Screen state is a saved route string plus a MAC
  in `PowerViewApp`, deliberately.
- **`targetSdk` 35**, a release decision (see the third session).
- **`connectGatt` deprecation** warning in `ShadeGattClient.kt`. Harmless today.

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
directly, no secrets involved. Artifacts expire after 14 days. The same APK
builds locally at `app/build/outputs/apk/debug/app-debug.apk`. It is worth
installing now for the three device checks under "Waiting on a phone".

---

## How CI got un-stuck (history worth keeping)

**CI had never successfully run** before the first session.
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

- `.claude/dev-skills-gates.md` holds gate state and is committed. Read it
  before any git write.
- **Commit approval does not carry across sessions.** Each session has been
  given approval for its own work; none of it carries forward. Ask again.
- **Merging PRs is the owner's call.** In the fourth session `gh pr merge` was
  blocked by the permission classifier and the owner merged PR #9 themselves.
  Verify the PR (SHA, CI, what changed), then hand it over with the command.
- Tag pushes and ref deletions are always handed to the user to run.
- **Do not chain `git commit` steps with `&&` after a command that can fail.**
  In the fourth session a failed `git reset` on a deleted path skipped the first
  commit while the rest of the script committed and pushed, which is how
  `560e09e` landed unbuildable. Use `set -e`, one commit per call, and check
  `git log` before pushing.
- Commit messages in this repo explain *why*, at length, and record what was
  verified versus what was not. Keep that up; it is most of the reason this
  document can be written at all.
