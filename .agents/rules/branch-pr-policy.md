---
trigger: always_on
description: Commit directly to master and push to origin master; never force-push; no branches or PRs.
---

# RULE: COMMIT & PUSH POLICY (STRICT)

This repo is single-developer. All work lands on `master`.

1. **Commit directly to `master`.** No feature branches, no worktree branches left behind.
2. **Push to `origin master` is allowed without asking** — after each verified, building step
   and at the end of every phase.
3. **Never force-push** (`git push --force`, `-f`, `--force-with-lease`), never rewrite pushed
   history, never `git reset --hard` over uncommitted work.
4. **No pull requests.** Don't run `gh pr create` or `gh pr merge`.
5. Before committing: `scripts/ci-local.sh` green (ktfmt + compile) and the tests for the change
   (see [android-build.md](android-build.md)). The git hooks in `CMP/.githooks` enforce the same.
6. Commit messages: Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`,
   `refactor:`), scope optional, e.g. `feat(server): add jobs routes`.
