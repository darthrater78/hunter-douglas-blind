# Dev Skills gate state
Track: work commits (no version bump, no artifact publish, no release)
Version: n/a — still pre-release, nothing tagged
Updated: 2026-09-17 (end of session 3)

🔢 VERSION    ➖ N/A on a work commit — no version bump, nothing tagged or published
🔨 BUILD      ✅ green at branch head (CI; see notes — local build is structurally impossible)
🔒 SECURITY   ✅ session-3 diffs scanned, 0 Critical / 0 High — see notes, including one honest gap
📄 DOCS       ✅ CHANGELOG, docs/HANDOFF.md and this file rewritten for the dependency sweep
📦 RELEASE    ⬜ nothing open; the Dependabot queue is cleared (6 merged, 1 closed)
🚀 SHIP       ⬜ nothing tagged or released

Environment: remote container (git executed by Claude after approval; tag
pushes and ref deletions always presented to the user)
Repo: https://github.com/darthrater78/hunter-douglas-blind
Branch: claude/load-dev-skills-d0bioe — **this is the repository's default
branch.** There is no `main` or `master` and no tags at all.

## Read this first
`docs/HANDOFF.md` is the full state-of-play document. For this session, the
section that matters is "The dependency sweep — done, and what it was hiding".

## The track question, answered explicitly
dev-skills §2 says anything that "merges to the default branch" is a release
sequence needing all six gates. This branch *is* the default branch, so by the
letter every dependency merge here is a release. They were treated as **work
commits** instead, with the user's explicit agreement, on the grounds that
nothing here bumps a version, produces an artifact, or publishes — the repo is
pre-release scaffolding with no tags. This is recorded rather than assumed so
the next session does not have to re-derive it or quietly grant itself the same
exemption. If a real release is ever cut, creating a real `main` is the tidier
fix.

## Build gate notes
**Green at the branch head**, including `assembleRelease` with R8, where
`lintVitalRelease` runs.

Session 3 ran the Dependabot queue to completion. Two real failures, both fixed
forward rather than reverted or worked around:

- **Run #38** — AGP 9.4.0 rejected `org.jetbrains.kotlin.android`, because AGP
  9 enables built-in Kotlin by default and the plugin becomes a hard error.
  Fixed by the documented opt-out (`android.builtInKotlin=false`,
  `android.newDsl=false`) rather than by the migration, because built-in Kotlin
  compiles with AGP's bundled Kotlin and that has to agree with the Compose
  compiler plugin pinned at 2.4.20 — unknowable here. **The migration is owed
  and AGP 10.0 removes the opt-out.**
- **A concurrency-group fix that did not work.** Widening `pull_request` to
  `['**']` made it overlap `push`, and `head_ref || ref` does not collapse the
  pair (`<branch>` vs `refs/heads/<branch>`). PRs #1 and #6 each built twice
  before it was caught. It is `head_ref || ref_name` now.

**The local build gate is structurally impossible here and CI is the only
compiler.** The network policy denies `dl.google.com`, and `maven.google.com`
redirects there, so no AGP or AndroidX artifact resolves. Verify with
`curl -sS "$HTTPS_PROXY/__agentproxy/status"`.

**But documentation hosts are reachable, and using them is the difference
between one commit and three.** `developer.android.com`, `kotlinlang.org` and
plain-git `github.com` all work. Reading
`developer.android.com/build/migrate-to-built-in-kotlin` turned the AGP 9
failure into one correct commit; `git ls-remote` on the action repos is what
caught a pin labelled v4.4.4 while pointing at v4.4.3. Recipes are in
`docs/HANDOFF.md` under "Verifying work without an Android SDK".

What *can* still be checked locally was used: the workflow YAML parses and its
trigger set was asserted, and `libs.versions.toml` was parsed with `tomllib`
after every edit. `actionlint` is not installed in the container, so CI's
`actionlint` job remains the real check on the workflows.

## Security gate notes
**Session 3's diffs scanned, 0 Critical / 0 High.** The diffs are dependency
versions, two workflow triggers, a concurrency key and two documented Gradle
properties. No code changed — no new permission, component, network call,
logging, crypto surface or persisted value.

Specifically reviewed:

- **`androidx.security:security-crypto` alpha06 → stable 1.1.0 is a supply
  chain maturity improvement, not a patch.** No CVE was involved. What it
  addresses is that a *pre-release* library guarded the only credential in the
  app, with step 5 about to put a real keystream behind it — a real concern of
  the unsupported-dependency kind, and worth doing first for that reason. The
  package having "security" in its name makes the stronger reading tempting;
  resist it. It also does *not* settle whether `EncryptedSharedPreferences` is
  the right home for the keystream — Jetpack has been steering away from it.
  **That call is still owed before any real release.**
- **Action pins were verified against upstream tags, not trusted.**
  `actions/setup-java@de7274f` = v6.0.1 and `actions/upload-artifact@043fb46` =
  v7.0.1, both resolved with `git ls-remote`. This is supply-chain review, and
  it is not theoretical here: CI was dead for this project's entire history
  because setup-gradle was pinned to a SHA in no tag at all.
- **The widened `pull_request` trigger was checked for the fork-PR risk.** It
  is `pull_request`, not `pull_request_target`, so a fork PR runs with a
  read-only token against the *merge* commit and cannot reach secrets;
  `ci.yml` declares `permissions: contents: read` and uses no secrets.
  `release.yml`, which does handle signing secrets, triggers on tags only and
  was not touched. No escalation.

**The honest gap, and it is bigger than the container: nothing is watching
this repository for CVEs at all.**

Two separate limits, and the second is the one that matters:

1. No dependency audit tool was run here, and none can be. §4.1 wants the
   ecosystem's audit run against the current lockfile at every security gate.
   There is no lockfile, and Gradle cannot resolve the Android tree with
   `dl.google.com` blocked, so no CVE check covers the AndroidX/AGP tree.
2. **Dependabot alerts are disabled for this repository.** Confirmed directly:
   `GET /repos/.../dependabot/alerts` returns `403 "Dependabot alerts are
   disabled for this repository."` The `dependabot.yml` here configures
   *version updates* only — the weekly scheduled kind. The advisory-driven
   *security updates* are a different feature and it is switched off.

So the September 2026 sweep, which merged six PRs, **fixed no known
vulnerability, because none was ever reported.** No CVE or GHSA identifier
appears in any of the seven PR bodies. Every pin is now the current release,
which lowers exposure without measuring it. The dependencies' status is
*unknown*, not *clean*.

**Owner action, not something Claude can do:** Settings → Code security →
enable **Dependabot alerts** and **Dependabot security updates**. §4.1 calls
for exactly that automated watch. Also run a real audit (`osv-scanner`) from a
machine with Google Maven before any release.

The full audit (1 Critical, 5 High, 11 Medium, 4 Low; all Critical and High
fixed) predates roughly 2,000 lines of session-2 UI code that it never saw —
five screens, two view models, a notification path and a background worker.
Session 3 added no code at all, so that gap is unchanged and still owed.
Standing properties hold by inspection: no network code, no logging, no
eval/exec/reflection/SQL/WebView, keystream in EncryptedSharedPreferences and
excluded from backup, `.gitignore` covers signing material.

Three exported components (widget receiver, widget config activity, tile
service) and one deliberately unexported trampoline (`RunActionActivity`)
remain the surface most worth a reviewer's attention; the reasoning for each is
in session 2's notes and unchanged.

## Release gate notes
`release.yml` still fails by design until `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are set: without them the
build produces an unsigned APK that Android cannot install, and the workflow
refuses to publish one. CI uploads a debug-signed APK on every push.

**That APK is now worth installing for a specific reason**, not just to browse:
the Compose BOM moved two years in one commit and the OLED theme leans on the
Material 3 `surfaceContainer` roles. Green means it compiles. Nobody has looked
at it.

## Note for the next session
Commit approval does not carry across sessions. Session 3 was granted a
standing approval for its work commits; that expired with it. **Ask again.**

**Start with step 5** — it is the only build-order work left, and the user has
twice reconfirmed it stays last in the ordering, so it is now simply next. The
decision still open on it (which of the three onboarding paths to build first,
with import-a-known-key the standing recommendation) is in `docs/HANDOFF.md`.

Three follow-ups the dependency sweep left behind. None blocks step 5, and none
is something CI can answer:

1. **Enable Dependabot alerts** (Settings → Code security). This is the most
   valuable of the three and takes a minute — right now nothing watches this
   project for CVEs. It is a repository setting, so it needs the owner. See the
   security notes above.
2. **The built-in Kotlin migration**, before AGP 10.0 removes the opt-out. The
   steps are recorded in `gradle.properties` beside the flags; the open
   question is whether the Compose plugin pin must move with AGP's bundled
   Kotlin.
3. **Look at the app under the Black (OLED) theme** after the Compose BOM jump.
