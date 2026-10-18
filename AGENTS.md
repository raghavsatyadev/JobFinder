# AGENTS.md

Shared instructions for every coding agent in this repo — Claude, Gemini, Codex, Cursor.
`CLAUDE.md` and `GEMINI.md` just point here.

**JobFinder** (a rewrite of first2apply) — job-search scraper and tracker: Compose Multiplatform desktop app with an embedded Ktor server, plus an Android client over home Wi-Fi; all AI on local Ollama.
Modules: `:shared`, `:composeApp`, `:server`, `:desktopCMP`, `:androidCMP`, `:migrator` · app module `:androidCMP` · applicationId `io.github.raghavsatyadev.jobfinder` ·
base branch `master`. Values also live in `agent-kit.env`.

## How to work

- Do only what the task asks: no unrequested features, tests, files, docs or refactors.
- Never write unit tests ([no-unit-tests.md](.agents/rules/no-unit-tests.md)).
- Ask only when blocked. Commit and push to `master` without asking (see branch policy).
- Before "done", run the check for the change (android-build.md); if none can run, say why.
- When done and checked, stop. Report in five lines or fewer, in ASD-STE100 Simplified Technical English.
- Claude Code: medium effort for scoped edits; high for native or architecture work. A second
  agent only for a review the user asked for.
- Decision model: global skill `local-model`. Global skills live in `global_skills/` (no agent reads it
  directly); install them with `bash global_skills/install.sh`.
  Turn on the local decision model first (`bash ~/.local-model/lm-on`), then use `jgl`,
  `lm-ask`, `lm-rank` and `rg` as the skill says. Rule checks for `lm-diffcheck` (run before a
  review): [.agents/diff-checks.txt](.agents/diff-checks.txt).

## Rules — `.agents/rules/`

| Rule | Read it when |
| --- | --- |
| [shared-code-first.md](.agents/rules/shared-code-first.md) | *always* — UI and logic in common code; platform modules only implement interfaces (Linux, macOS, iOS planned) |
| [no-unit-tests.md](.agents/rules/no-unit-tests.md) | *always* — no unit tests in any module; verify on device, desktop and screenshots |
| [android-build.md](.agents/rules/android-build.md) | before calling any task done — the code must compile |
| [format.md](.agents/rules/format.md) | touching any Kotlin — ktfmt Google style is mandatory |
| [artemis-mobile-testing.md](.agents/rules/artemis-mobile-testing.md) | *on demand* — using ARTEMIS MCP tools |
| [branch-pr-policy.md](.agents/rules/branch-pr-policy.md) | *always* — see the summary below |
| [parallel-agents.md](.agents/rules/parallel-agents.md) | the user asks for agents in parallel worktrees — pin the base commit, cap Gradle memory, clean up after |
| [pure-local-llm-inference.md](.agents/rules/pure-local-llm-inference.md) | touching any AI/LLM code — local Ollama `jobfinder-qwen` by default, online only with the user's DPAPI-kept key, never `nimble`, fail open |

## Skills — `.agents/skills/`

One folder per skill; each `SKILL.md` `description` says when to read it. Claude Code sees them
through the `.claude/skills/` links that `scripts/setup-env.*` create.

## Commands

New machine: run the setup doctor first — `scripts/setup-env.ps1` (Windows) or
`scripts/setup-env.sh` (macOS/Linux).

```bash
scripts/ci-local.sh                      # ktfmt + compile<APP_VARIANT>Kotlin — run before pushing
scripts/gradle-agent.sh <tasks>          # agents: Gradle with errors only; full log in tmp/
./gradlew :androidCMP:assembleDebug   # build
git config core.hooksPath .githooks  # once: hooks run the format and CI checks
```

`ci-local.sh` flags: `--fix` reformats, `--format-only` skips the compile, `--committed-only`
matches CI exactly. Never format with a `ktfmt` from your PATH; `ci-local.sh` fetches the right one.

## Working on a bug

Follow [fix-issue](.agents/skills/fix-issue/SKILL.md)
(`/fix-issue <url>` in Claude Code): reproduce on the device → diagnose from real device state →
fix with a test that fails on the old behaviour → reinstall and re-run the reproduction → commit, stop.
A green unit test is not device verification. Inspect the device with ARTEMIS, not
`uiautomator dump`, which rebinds accessibility services.

## Housekeeping

`tmp/` (agent scratch), `memory/` (persistent local data such as device serials),
`.cache/` and every `.env` are git-ignored. Never commit credentials. Never put a token in a remote URL or
`.git/config`; `gh` and the credential manager handle auth. Never clean `tmp/` unless the
user asks — see [workspace-cleanup](.agents/skills/workspace-cleanup/SKILL.md).
