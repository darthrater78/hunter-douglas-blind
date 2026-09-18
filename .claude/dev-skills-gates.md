# Dev Skills gate state
Track: work commits (no version bump, no artifact publish, no release)
Version: n/a — still pre-release, nothing tagged
Updated: 2026-09-18 (session 5, resumed; skill v2.24.0 from scratchpad)

🔢 VERSION    ➖ N/A on a work commit — no version bump, nothing tagged or published
🔨 BUILD      ✅ Phase 2 (widget resize) green on the current diff: `./gradlew
               :protocol:test assembleDebug test assembleRelease lint` — 147 tests
               (data 4, protocol 40, ui 47, widget 56), 0 failed, R8 + full lint.
               Re-runs each phase.
🔒 SECURITY   ✅ Phase 2 diff reviewed: Glance sizeMode + layout arithmetic only; no
               new dependency, permission, exported component or external input.
               0 Critical / 0 High. Quality: overflow-line clipping at the minimum
               size found and fixed (maxRowsForHeight reserves room for it), tests
               added in BatteryWidgetSizeTest.
📄 DOCS       ⬜ owed at the end of the UI-overhaul work (CHANGELOG entry/entries),
               not per phase
📦 RELEASE    ⬜ nothing open
🚀 SHIP       ⬜ nothing tagged or released

Environment: local session on the owner's build server (Android SDK, Google Maven
reachable). Git is executed by Claude after approval; tag pushes and ref
deletions are always presented to the user.
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe — this is the repository's default
branch. There is no `main`/`master` and no tags.

## Session 5: UI-quality pass, plan approved

Redirected from build-order step 5 (which stays next once this is done) to a
UI overhaul: visual design, navigation/structure, notification settings, and
a widget-resize bug, delivered as four phased commits with a build+verify+
commit cycle each. Full plan reasoning lives in the session transcript; the
phase breakdown:

1. **Notification settings** — ✅ committed `29e5961`, pushed. `SettingsStore`
   gained `notificationsEnabled` (default true); `BatterySweepWorker` skips
   the `BatteryNotifier` call when off; sweeps and readings are unaffected.
   Settings screen got a toggle plus a link to
   `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
2. **Widget resize fix** — built, scanned, awaiting commit approval; device
   check owed. Root cause found during planning:
   neither `ShadeActionWidget` nor `BatteryWidget` overrides
   `GlanceAppWidget.sizeMode`, so Glance composes once at `SizeMode.Single`
   and never reacts to the launcher's resize handles despite the manifest
   declaring `resizeMode="horizontal|vertical"`. Fix is `SizeMode.Responsive`
   with curated breakpoints on both widgets, plus wiring
   `BatteryWidgetPresentation.batteryRowsToShow`'s existing `max` param to
   `LocalSize.current.height` so row count adapts instead of clipping at a
   fixed 5.
3. **Navigation restructure** — not started. Bottom `NavigationBar`
   (Shades/Actions/Settings) replacing the text "More" overflow menu; Raw
   scan moves into Settings as a Developer entry; real icon back button.
   Deliberately keeping the hand-rolled route state rather than adopting
   `navigation-compose` (pinned in the catalog, never once resolved by a
   build) — recorded as a decision, not an oversight.
4. **Visual redesign** — not started. Adds `material-icons-core`
   (BOM-versioned, no separate pin) for real icons throughout; hand-authored
   Light/Dark color schemes from a chosen accent seed (user chose "add a real
   accent color" over staying neutral or dynamic/wallpaper-based), with
   `OledScheme` re-derived from the new `DarkScheme` the same way it already
   is; consistent card/spacing/icon polish across the six screens without
   re-architecting layouts.

Each phase's build gate re-runs the same CI task list; nothing is deferred to
"CI will catch it." Phone verification (resize behavior, nav feel, the actual
look of four themes) is offered after every phase per `GATE_REFERENCE.md`
("Local artifact handoff") and is not yet done for phase 1.

## The track question, answered explicitly (carried from session 3/4)

dev-skills §2 says anything that "merges to the default branch" is a release
sequence needing all six gates. This branch *is* the default branch, so by the
letter every commit here is a release. They are treated as **work commits**
instead, with the user's explicit agreement, on the grounds that nothing here
bumps a version, produces an artifact, or publishes — the repo is pre-release
scaffolding with no tags. If a real release is ever cut, creating a real
`main` is the tidier fix.

## Note for the next session
Commit approval does not carry across sessions — ask again. `docs/HANDOFF.md`
still holds the full state-of-play as of the end of session 4 (build-order
progress, hardware confirmations, open questions); this file is gate state for
the UI-overhaul phases in progress now.
