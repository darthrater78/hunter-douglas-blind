# Recommendations for the `dev-skills` skill

**Scratch file — safe to delete once actioned.** It is not part of the project
and nothing references it.

Written 2026-09-17 from the third session on `hunter-douglas-blind`, which
cleared a seven-PR Dependabot queue. Every finding below is something that
session *demonstrated*, not something inferred from reading the skill.

- **Skill version reviewed:** `dev-skills` 2.22.0
- **Source repo to apply against:** `darthrater78/claude-vibe-skills`
- **Evidence repo:** `darthrater78/hunter-douglas-blind`, commits `aeb04a4`
  through `2092954`

Findings are ordered by value. **A is a factual error in the skill** and the
rest are gaps or misleading emphasis.

---

## A. `WORKFLOW_REFERENCE.md` states something false about Dependabot security updates

**Severity: high.** This is not a missing topic — it is a claim that actively
tells the reader they are covered when they are not, which is worse than
silence because it stops them checking.

### Current text (`WORKFLOW_REFERENCE.md`, ~line 2369)

> **Security updates need no configuration.** Dependabot opens security PRs for
> known advisories on any ecosystem listed here, regardless of `schedule` — but
> only for ecosystems that have an entry. An ecosystem you left out is an
> ecosystem nobody is watching.

### Why it is wrong

GitHub has three separate things sharing the Dependabot name:

| Feature | Enabled by | Produces |
|---|---|---|
| **Version updates** | `.github/dependabot.yml` | scheduled "Bump X from A to B" PRs |
| **Alerts** | repository setting (Code security) | advisory matches against the dependency graph |
| **Security updates** | repository setting, **requires alerts** | PRs that fix a specific advisory |

The quoted paragraph is wrong in *both* directions:

1. Security updates **do** need configuration — they need Dependabot alerts and
   Dependabot security updates switched on at the repository level. A
   `dependabot.yml` alone never produces a security PR.
2. Security updates do **not** depend on an ecosystem having an `updates:`
   entry. They work from the dependency graph regardless of that file.

### Evidence

`hunter-douglas-blind` had a correct `dependabot.yml` covering both of its
ecosystems (`github-actions`, `gradle`), with weekly version PRs flowing for
months. Queried directly:

```
GET /repos/darthrater78/hunter-douglas-blind/dependabot/alerts
403  {"message": "Dependabot alerts are disabled for this repository."}
```

Seven open PRs, six merged. **Not one named a CVE or GHSA identifier.** The
only occurrence of the word "vulnerabilities" across all seven bodies is inside
a boilerplate compatibility-score badge URL. The project had never had a CVE
watch, and the skill's text is exactly why nobody noticed.

### Suggested replacement

> **Security updates are a separate feature and they are NOT configured by this
> file.** `dependabot.yml` controls *version updates* — the scheduled "Bump X
> from A to B" PRs. Advisory-driven *security updates* are repository settings:
> Settings → Code security → **Dependabot alerts** and **Dependabot security
> updates**. Both must be on, and they work from the dependency graph whether or
> not an ecosystem has an `updates:` entry here.
>
> A repo with a perfect `dependabot.yml` and alerts disabled is current but
> unwatched — it gets version churn and zero CVE coverage, which looks like
> security work and is not. Claude cannot change repository settings, so when
> alerts are off this is surfaced to the user as a finding, not fixed silently.

*Worth confirming against GitHub's current docs when applying:
https://docs.github.com/code-security/dependabot*

---

## B. `SKILL.md` §4.1 "Automate the watch" only covers half the watch

**Severity: high.** This is the rule that produced the state above. Followed
perfectly, it yields a repo with version churn and no CVE coverage.

### Current text (`SKILL.md`, ~line 375)

> - **Automate the watch.** If the project has no dependency-update automation,
>   recommend it once — `.github/dependabot.yml` covering every ecosystem the repo
>   uses (`WORKFLOW_REFERENCE.md`). A weekly PR is how a project stays current
>   between security gates instead of discovering a year of drift at once.

### Suggested replacement

> - **Automate the watch — both halves of it.** Staying *current* and being
>   *watched for advisories* are different mechanisms and a project can easily
>   have one without the other:
>   1. **Version updates** — `.github/dependabot.yml` covering every ecosystem
>      the repo uses (`WORKFLOW_REFERENCE.md`). A weekly PR is how a project
>      stays current between security gates instead of discovering a year of
>      drift at once.
>   2. **Alerts and security updates** — repository settings, not a file.
>      Check rather than assume:
>      ```
>      GET /repos/{owner}/{repo}/dependabot/alerts
>      ```
>      A `403 "Dependabot alerts are disabled for this repository"` is a
>      **Gate 3 finding**, not a pass. Claude cannot flip a repository setting,
>      so surface it with the path: Settings → Code security → enable Dependabot
>      alerts and Dependabot security updates.
>
>   Having only (1) is the trap: the queue of "Bump X" PRs *looks* like security
>   maintenance. Merging all of it fixes no vulnerability if nothing was ever
>   reported.

### Also worth adding to Gate 3 in `GATE_REFERENCE.md`

The gate's dependency check should distinguish two claims that are easy to
conflate, because this session conflated them in its own gate file before
catching it:

- **"0 Critical / 0 High"** from reviewing the *diff* — what the gate normally
  means.
- **A CVE scan of the dependency tree** — a different claim needing a resolved
  tree and an audit tool.

When the second cannot run, the gate should say so explicitly rather than let
the first stand in for it. Suggested wording: *"Dependencies: not audited —
no lockfile / registry unreachable. Currency is not a measurement of
exposure. Status is unknown, not clean."*

---

## C. An absent verdict is not a passing verdict

**Severity: high.** Generalises well beyond Dependabot.

### What happened

Four of the seven PRs (#2, #3, #4, #5) had **zero** check runs — not red,
*empty*. Their base predated `ci.yml`'s `push: branches: ['**']`, and the
`pull_request` trigger was scoped to `[main, master]`, branches that do not
exist in that repo. Neither trigger ever matched, so none of the four had ever
been built.

The previous session's handoff, reading that empty list, described two of the
PRs as **"low risk by definition"**. An empty check list reads like success in
a way a red X never does.

### Suggested addition to `SKILL.md` §2, the re-derivation table

Add a row, and a sentence under the table:

| Gate | Evidence that it passed |
|---|---|
| 🔨 BUILD | ... **A check list with zero runs is ⬜, never ✅.** |

> **Absence of a verdict is not a verdict.** "No runs", "no findings", "no
> alerts" and "no output" are all ⬜ until you have established *why* they are
> empty. An empty result looks far more like success than a failure does, which
> is exactly what makes it dangerous. Confirm the mechanism ran before reading
> its silence as a pass.

---

## D. The skill's own `dependabot.yml` template teaches the risk heuristic that failed

**Severity: medium.**

### Current text (`WORKFLOW_REFERENCE.md`, in the template, ~line 2360)

```yaml
    groups:
      # Patch and minor bumps ride together — low risk, one review.
      minor-and-patch:
        update-types:
          - "minor"
          - "patch"
    # Majors stay ungrouped: each is a breaking change with its own gates.
```

`SKILL.md` §4.1 reinforces it: majors get their own gates, which readers invert
into "minors are safe to batch."

### Why it is misleading

The `minor-and-patch` group in `hunter-douglas-blind` (PR #1) failed **every
one** of its five bumps:

```
Dependency 'androidx.core:core-ktx:1.19.0' requires Android Gradle plugin
9.1.0 or higher. This build currently uses Android Gradle plugin 8.7.3.
... requires ... compile against version 37 or later of the Android APIs.
```

A **minor** bump of a library demanded a **major** bump of the build plugin
plus a `compileSdk` change. The semver delta describes the library's own API
promise; it says nothing about what the library requires of its toolchain.

### Suggested replacement for the comment

```yaml
    groups:
      # Patch and minor bumps ride together: one review, not one risk class.
      # A minor bump can still demand a major toolchain change — only a build
      # settles it. Group for review convenience, never as a risk judgement.
      minor-and-patch:
```

And in §4.1, after the "majors get their own gates" bullet:

> The converse does not hold. A minor or patch bump is *cheaper to review*, not
> *known to be safe* — the version delta bounds the dependency's own API
> promise, not what it requires of your toolchain. Only a build establishes
> whether a bump works.

---

## E. Resolve SHA pins; never trust the comment beside them

**Severity: medium.** `WORKFLOW_REFERENCE.md` already mandates SHA-pinning and
recommends Dependabot to keep pins current, but never says to verify that a pin
matches its label.

### Evidence — two separate instances in one repo

1. **A pin to a SHA in no tag at all.** `gradle/actions/setup-gradle@ac638b010cf753c3731813f9302c85b32d0b30b0 # v4.4.1`,
   where real v4.4.1 is `ac638b010cf58a27ee6c972d7336334ccaf61c96`. Note how
   close those are — the first eight characters match. **CI in that repository
   never ran once in its entire history** because of it, and since `release.yml`
   gated on a passing CI run, releases were unreachable too.
2. **A pin labelled with the wrong version.** `48b5f213… # v4.4.4` was in fact
   the *annotated tag object* for **v4.4.3**. Dependabot then "updated" it to
   `ed408507…`, the *commit* v4.4.3 points to — correct form, same version —
   and carried the wrong `# v4.4.4` comment across untouched, because it did not
   consider that a version change.

The second matters beyond tidiness: **Dependabot reads that comment to decide
what to offer next.** A pin mislabelled as newer than it is suppresses the real
upgrade. `gradle/actions` was on v6.0.1 while the repo sat on v4.4.3 believing
it was on v4.4.4.

### Suggested addition to the workflow audit checklist

> - [ ] **Every action SHA resolves to the tag its comment claims.** Do not read
>       the comment — resolve it:
>       ```
>       git ls-remote https://github.com/<owner>/<repo> | grep -E 'refs/tags/v1\.2\.3'
>       ```
>       Compare against the pin. Note that an annotated tag returns two lines:
>       the tag object and, with `^{}`, the commit it points to. Pin the
>       **commit**. A pin matching neither is a broken pin; a pin matching a
>       different tag than its comment is a suppressed upgrade.

---

## F. Gate 2 has no state for "a local build is structurally impossible"

**Severity: medium.** Optional, but the gate was unsatisfiable as written.

### Current text

> | 🔨 **BUILD** | the project's **local dev workflow** builds it and the app is
> verified working — or ➖ N/A with no build system. CI is not a substitute: it
> runs after the commit this gate is protecting |

### The problem

`hunter-douglas-blind` has a real build system, so ➖ N/A does not apply. But no
local build is possible: no Android SDK in the container, and the environment's
network policy denies `dl.google.com` (`maven.google.com` redirects there), so
AGP cannot resolve at all. The gate therefore had only two outcomes: block
forever, or mark ✅ on something it forbids. Both are wrong, and the second is
what pressure produces.

### Suggested third state

> **➖ N/A** — no build system exists.
>
> **✅ CI-only** — a build system exists but cannot run in this environment
> (missing SDK, unreachable registry, wrong OS). Permitted *only* when all
> three hold, and the reason is written on the tracker:
> 1. The obstacle is structural, not a missing setup step. State it concretely
>    ("`dl.google.com` denied by network policy"), never "it didn't work here".
> 2. The CI verdict covers **the exact tree being merged**, not an ancestor of
>    it. For a PR, update the branch onto the target head first so the run
>    builds the merge result.
> 3. Everything checkable locally *was* checked — config files parsed, pure
>    modules compiled and tested in isolation, linters run.
>
> This is weaker than the local gate and is recorded as such. It does not
> license "CI will catch it" on a project where a local build merely takes a
> while.

---

## G. Smaller notes

- **`@dependabot` commands do not work from an agent.** A comment posted via the
  GitHub MCP tools has the mention neutralised — it arrived as
  `·@·d·ependabot r·ebase` with invisible separators — so Dependabot never sees
  it. The working path is `update_pull_request_branch` ("Update branch"), which
  merges the base in and fires a push event CI responds to. Worth a line
  wherever the skill discusses driving Dependabot PRs, since the failure is
  silent: the comment posts successfully and nothing happens.

- **`github.head_ref || github.ref` does not deduplicate overlapping triggers**,
  and reads as though it does. For a push it yields `refs/heads/<branch>`; for a
  pull request, `<branch>`. Different strings, both runs survive. The correct
  key is `github.head_ref || github.ref_name`. If the skill's workflow templates
  use the former anywhere, it is silently doubling CI cost on every PR.

- **"Merges to the default branch are a release sequence" (§2)** assumes the
  default branch is `main`. This repo's default branch *is* its working branch,
  with no `main`, no `master` and no tags, which made every dependency merge a
  "release" by the letter while nothing was versioned or published. One
  clarifying sentence — that the trigger is *publishing intent*, not the branch's
  name or default status — would stop the next session either over-gating or
  quietly exempting itself.

---

## What this session did NOT establish

Stated so nobody over-reads the above:

- No claim that the skill's security guidance is broadly wrong. Finding A is one
  false paragraph; the rest are gaps and emphasis.
- No CVE was found in `hunter-douglas-blind`, and none was looked for — no audit
  tool can run in that container. "Alerts are disabled" means the exposure is
  **unknown**, not that something is wrong.
- Findings C–F come from a single project with an unusual shape (Android, no
  local SDK, default branch that is a working branch). A, B and E generalise
  cleanly; C is a judgement rule rather than a mechanism; D and F are worth
  weighing against projects that do not look like this one.
