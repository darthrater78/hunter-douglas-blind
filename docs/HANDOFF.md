# Handoff

Written 2026-09-17. Branch `claude/load-dev-skills-d0bioe` at `3388685` — not
merged, no PR open, no release tagged.

This is the state-of-play document for whoever picks the project up next. The
README describes what the app is meant to be; this describes what is actually
true about it today, which is not the same thing.

---

## Start here

The single most useful distinction in this project is **confirmed against real
hardware** versus **assumed**. The scaffold was written without any shade
present, so a lot of it was reasonable-but-unverified — and two of those
assumptions turned out to be wrong the first time a real device was in range.
Keep that distinction alive as you work; it is why the debug screen shows raw
bytes next to decoded fields.

Build order progress (numbering follows the README):

| Step | State |
|---|---|
| 1 `:protocol` + unit tests | ✅ 40 tests |
| 2 Scanner + raw debug screen | ✅ **offsets confirmed on real hardware** |
| 3 Capability mapping | ✅ typeId 8 → TDBU, confirmed |
| 4 GATT connect + battery read | ✅ **first real GATT connection worked** |
| 5 Keystream import + first write | ⬜ next — blocked on a decision, see below |
| 6 Derive-from-capture, tilt, secondary | ⬜ core classes exist, no UI |
| 7 Persistence, labels/rooms, actions | ✅ model + stores exist, no UI |
| 8 ActionRunner + CommandWorker | ✅ exist, nothing drives them yet |
| 9 Glance widgets | ⬜ TODO stubs only |
| 10 Battery sweep + notifications | ⬜ `BatteryReader` is ready to schedule |
| 11 Quick Settings tile | ⬜ TODO stub only |
| 12 Home Assistant bridge | ⬜ optional |

CI/CD works. It did not before — see "How this got here".

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
- **`secondary: 0.2%` is a real rail position**, not a decode artifact. It was
  initially suspected to be an off-by-one; the hex shows raw 9 genuinely in the
  secondary field.

## Corrected by real hardware

Two documented claims were wrong. Both came from inferring protocol behaviour
from the openHAB binding rather than measuring it.

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
- The **command frame layout** (§3). It is verified against five sniffed
  ciphertext vectors and re-encrypts byte-for-byte, which is strong — but no
  frame has ever been written to a real shade. Step 5 is where that gets tested,
  and it is the first thing in this project that can physically move something.
- The **capability table** beyond typeId 8. It is ported from the openHAB
  binding; only one entry has been seen on hardware.
- Whether the sequence byte is validated (replay protection) or ignored.
  `ActionRunner` keeps an in-memory counter that resets on process death; if
  the shade turns out to enforce monotonicity, that needs persisting.

---

## Next step, and the decision that gates it

**Step 5: keystream import and the first real write.** Everything underneath it
is now confirmed rather than assumed, which is the right moment to attempt it.

It needs onboarding UI for one of the three paths in `PROTOCOL.md` §7, and
which one to build first depends on what the user has:

| Path | Needs | Notes |
|---|---|---|
| Import a known AES key | 32-char hex key for the home | Simplest UI. `FrameCipher.deriveKeystreamFromKey` already exists and is tested. |
| Derive from a capture | `btsnoop_hci.log` with an ATT write to `CAFE1001…` | `KeystreamDeriver` exists and is tested. UI is a guided multi-step flow — the most work. |
| Unenrolled shade | A factory-reset or never-enrolled shade | No key at all: send frames in the clear. Zero crypto UI. Best way to test the write path in isolation. |

**Ask before building.** Which path is right is a fact about the user's
situation, not a design preference, and building the wrong one first wastes the
largest UI chunk in the project.

`LOW_BATTERY_PERCENT` (currently 20) is already defined in `BatteryReader.kt`
for step 10, so the sweep and the UI cannot drift apart on the threshold.

---

## Environment gotchas

**The Android modules cannot be built in the Claude Code container.** There is
no Android SDK, and the network policy blocks `dl.google.com` /
`maven.google.com`, so AGP will not even resolve. `./gradlew :protocol:test`
fails there too — at the *root* project's plugin block, before it reaches
`:protocol`. This is not fixable from inside the sandbox; CI is the compiler.

What *can* be verified locally:

- `:protocol` in an isolated Gradle project with only Maven Central
  (Kotlin JVM plugin + JUnit 5). This is how the 40 tests get run.
- Workflow files with `actionlint` (it shellchecks `run:` blocks too).

Practical consequence: **every Kotlin change to `:app`/`:ble`/`:data`/`:ui`/
`:widget` is unverified until CI runs.** Budget a round trip. Four consecutive
CI failures were burned discovering pre-existing defects this way — all real,
none introduced, but each cost a cycle.

---

## Blocked, not skipped

These were identified in the audit and cannot be completed from the sandbox:

- **Gradle dependency locking and `gradle/verification-metadata.xml`.** Both are
  generated from a successful dependency resolution, which needs Google Maven.
  Worth doing from a normal dev machine — it is the only way to get a lockfile
  to audit against.
- **Currency of the pinned versions.** `gradle/libs.versions.toml` marks AGP and
  the AndroidX entries `UNVERIFIED`. That marker now means less than it did:
  CI has built the whole project green with them, so they demonstrably *exist
  and work*. What is still unknown is whether they are *current* — AGP 8.7.3 and
  `compileSdk`/`targetSdk` 35 are the ones to look at hardest, and targetSdk 35
  may be at or below the Play Store's floor by now. Dependabot is configured for
  both ecosystems and can finally run, since CI works.
- **`androidx.security:security-crypto` is at `1.1.0-alpha06`**, and it guards
  the only credential in the app. Jetpack has been steering away from
  `EncryptedSharedPreferences`. Make a deliberate decision before any real
  release rather than carrying an alpha forward by default.

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
directly, no secrets involved. Artifacts expire after 14 days.

---

## How this got here

Worth knowing, because it explains why so many defects surfaced at once: **CI
had never successfully run.** `gradle/actions/setup-gradle` was pinned to a SHA
that exists in no tag of that repository and is not fetchable from it at all, so
every run died at "Setup Gradle" — and because `release.yml` gates on a passing
CI run, releases were unreachable too.

With that unblocked, the first four runs each surfaced a genuine pre-existing
defect that had simply never had the chance to fail: `kotlinOptions` removed in
Kotlin 2.x, a theme parent (`android:Theme.Material.DayNight.NoActionBar`) that
does not exist, WorkManager's auto-initializer conflicting with
`Configuration.Provider`, and Tink's compile-only annotations tripping R8.

The WorkManager one is the one to remember: it was a *functional* bug, not lint
noise. With the default initializer present, WorkManager comes up with its own
configuration before `PowerViewApplication`'s is read, `CommandWorker.Factory`
never registers, and every widget and tile command fails at worker
instantiation — a baffling runtime failure that would have been chased much
later, from a much worse vantage point.

---

## Working conventions

- `.claude/dev-skills-gates.md` holds gate state and survives the container
  because it is committed. Read it before any git write.
- Commit approval does not carry across sessions. A standing approval granted in
  one session means nothing in the next.
- Tag pushes and ref deletions are always handed to the user to run, never
  executed directly.
