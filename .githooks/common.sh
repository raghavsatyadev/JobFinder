#!/usr/bin/env bash
# Sourced by the dispatcher hooks (pre-commit, pre-push, commit-msg).
#   CMP/  the Kotlin/Gradle project + Android Agent Kit  -> kit-pre-commit, kit-pre-push (ktfmt, ci-local)
# Enable with:  git config core.hooksPath CMP/.githooks   (scripts/setup-env.sh does this)

# git runs hooks with a relative GIT_DIR (".git") from the worktree root; `git -C <subdir>` would
# then look for <subdir>/.git. Pin it (and the worktree) to absolute paths before any `git -C`.
if [ -n "${GIT_DIR:-}" ]; then
  GIT_WORK_TREE="$(cd "${GIT_WORK_TREE:-$(git rev-parse --show-toplevel)}" && pwd)"
  GIT_DIR="$(cd "$GIT_DIR" && pwd)"
  export GIT_DIR GIT_WORK_TREE
fi

HOOKS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIT_ROOT="$(cd "$HOOKS_DIR/.." && pwd)"
GIT_ROOT="$(git -C "$KIT_ROOT" rev-parse --show-toplevel)"
KIT_PREFIX="$(git -C "$KIT_ROOT" rev-parse --show-prefix)"   # "CMP/"
ZERO_SHA="0000000000000000000000000000000000000000"

hook_log() { printf '[hooks] %s\n' "$*" >&2; }

# touches <prefix> <file-list>: does the newline-separated list contain a path under <prefix>?
touches() {
  [[ $'\n'"$2" == *$'\n'"$1"* ]]
}

# abs_path <path>: absolute (host-native on Windows) form of a path git gave us relative to the cwd.
abs_path() {
  local p="$1"
  case "$p" in /*|[A-Za-z]:*) ;; *) p="$PWD/$p" ;; esac
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$p"; else printf '%s' "$p"; fi
}

# pushed_files <remote> < stdin-file: files changed by the commits a push would send.
# stdin is pre-push's "<local ref> <local sha> <remote ref> <remote sha>" lines. Existing remote
# branch: <remote sha>..<local sha>. New branch (zero remote sha): commits not already on the
# remote-tracking ref, or, if there is none, not reachable from any remote ref (all of them when
# there is no remote at all).
pushed_files() {
  local remote="${1:-origin}" lref lsha rref rsha tracking
  local -a excl
  while read -r lref lsha rref rsha; do
    [ -n "$lsha" ] && [ "$lsha" != "$ZERO_SHA" ] || continue
    tracking="refs/remotes/$remote/${rref#refs/heads/}"
    if [ "$rsha" != "$ZERO_SHA" ] && git cat-file -e "$rsha^{commit}" 2>/dev/null; then
      excl=("$rsha")
    elif git rev-parse --verify --quiet "$tracking" >/dev/null; then
      excl=("$tracking")
    else
      excl=(--remotes)
    fi
    git -c core.quotepath=off log -m --name-only --pretty=format: "$lsha" --not "${excl[@]}"
  done | sed '/^$/d' | sort -u
}
