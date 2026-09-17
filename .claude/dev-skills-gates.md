# Dev Skills gate state
Track: work commits (no version bump, no artifact publish, no release)
Version: n/a — still pre-release, nothing tagged
Updated: 2026-09-17 (session 2)

🔢 VERSION    ⬜ not owed on a work commit
🔨 BUILD      ✅ CI green through `9193ad2` (run #18, incl. R8); tile pending CI
🔒 SECURITY   ✅ theme/widget/tile diffs scanned, 0 Critical / 0 High — see notes
📄 DOCS       ✅ README, CHANGELOG, docs/PROTOCOL.md, docs/HANDOFF.md current
📦 RELEASE    ⬜ no PR open
🚀 SHIP       ⬜ nothing tagged or released

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe

## Read this first
`docs/HANDOFF.md` is the full state-of-play document: what is confirmed against
real hardware versus assumed, the next step and the decision still open on it,
how to verify pure-Kotlin code locally without an Android SDK, and what is
blocked rather than skipped.

## Build gate notes
**Green through `9193ad2`** (run #18), including `assembleRelease` with R8.
That covers the theme picker (run #17) and the whole Glance widget, both of
which were written without an Android SDK to compile against.

The previous session's open question is closed too: run #14 on `0113457`
carried the same battery-sweep code as `3fe7a27` and passed, so no commit on
this branch is unverified by a compiler.

**What made the blind Glance commit compile first try** is worth repeating:
every Glance signature was read from the AndroidX sources on GitHub
(`raw.githubusercontent.com/androidx/androidx/androidx-main/glance/...`, which
is reachable here even though Google Maven is not) rather than recalled. One
fetch per signature beats one CI round trip per guess.

The Android modules still cannot be compiled in this container (no SDK; Google
Maven unreachable). What *can* be checked locally has grown, and is worth using:
any file with no Android imports compiles and tests in an isolated
Maven-Central-only Gradle project. Recipe and current file list are in
`docs/HANDOFF.md` under "Verifying work without an Android SDK". It caught a
compile error before CI this session.

Test counts: 40 in `:protocol`, 4 in `:data`, 44 in `:ui`, 23 in `:widget`.

## Security gate notes
**This session's work is scanned and clean** (0 Critical / 0 High).

The widget (step 9) and tile (step 11) add **three exported components**,
which is the thing here most worth a reviewer's attention.

The tile's `<service>` is bound behind `android.permission.BIND_QUICK_SETTINGS_TILE`,
so only the system can reach it despite being exported. Its `PendingIntent`
(the API 34+ `startActivityAndCollapse` path) is `FLAG_IMMUTABLE`.

**Closed: the tile no longer works from the lock screen.** It was built to
the spec's lock-screen requirement, the user said they did not want one, and
it now goes through `unlockAndRun` — so nothing is sent until the device is
unlocked. A locked tile also withholds the action's label and its last
result, since an action is named for a room and a thing done to it.

The two widget components: Both have to be exported — a widget
receiver that is not exported never receives `APPWIDGET_UPDATE`, and the
launcher is what starts a configuration activity — so the question is what
they accept. The receiver acts only on widget ids the system hands it. The
configuration activity treats its `appWidgetId` extra as untrusted and checks
it against the ids `GlanceAppWidgetManager` reports for this provider before
writing anything, so another app cannot use it to rewrite a widget that is not
ours; it holds no permission and exposes nothing beyond the user's own action
labels. Neither touches BLE directly — both go through the existing
`ActionRunner` funnel, which already never throws.

Also new: `androidx.datastore.preferences`, `androidx.compose.runtime`,
`compose-ui`, `material3`, `activity-compose` and `lifecycle-runtime-ktx` are
now declared on `:widget`. No new artifact enters the build — every one was
already resolved for another module, and they are declared here because this
module's own code names them rather than relying on another module's
`implementation` deps leaking onto the compile classpath.

The theme work adds no dependency, no permission, no network or crypto
surface, no logging and no exported component; the one new persisted value is a theme name in its own
`app_settings` DataStore, which falls back to `SYSTEM` on an unrecognised
value rather than throwing. It is deliberately a separate DataStore from the
shade and action stores so a corrupt settings blob costs a theme choice rather
than a shade list. Backup rules need no change — the denylist excludes only
`shade_keystreams.xml`, and a theme preference is right to restore.

The full audit (1 Critical, 5 High, 11 Medium, 4 Low; all Critical and High
fixed) was run last session and covers the code as it stood then. **This session
added roughly 2,000 lines — five new UI screens, two view models, a notification
path and a background worker — none of which that audit saw.** Re-run before any
release.

Standing properties still hold by inspection: no network code anywhere, no
logging of any kind, no eval/exec/reflection/SQL/WebView, one exported component
(the launcher activity), keystream in EncryptedSharedPreferences and excluded
from backup and device transfer, `.gitignore` covers signing material. The only
key-like literal in the repo is the openHAB project's published AES test vector,
in `:protocol` test sources.

New surface a re-audit should look at specifically:
- `POST_NOTIFICATIONS` is now declared and `BatteryNotifier` posts. It re-checks
  the grant at runtime and stays silent without it.
- `BatterySweepWorker` connects to every non-mains shade weekly. It bails out
  early without `BLUETOOTH_CONNECT` rather than attempting doomed connections.
- `ActionRunner` moved to `:data` and now returns typed outcomes. It still
  never throws, which is the property the widget/worker callers depend on.

Three Medium items remain open and are blocked rather than skipped — Gradle
dependency locking, `verification-metadata.xml`, and the currency (not validity)
of the AGP/AndroidX pins. All need Google Maven. A fourth is worth raising in
priority: `androidx.security:security-crypto` is still at `1.1.0-alpha06`, and
step 5 is about to put a real keystream behind it.

## Release gate notes
`release.yml` will fail by design until `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` are set: without them the build produces an
unsigned APK that Android cannot install, and the workflow refuses to publish
one. CI uploads a debug-signed APK on every push, and it is now worth installing
— the app has a real UI, and everything except moving a shade works.

## Note for the next session
Commit approval does not carry across sessions. A standing approval granted in
this one means nothing in the next — ask again.
