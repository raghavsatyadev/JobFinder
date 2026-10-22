#!/usr/bin/env bash
# Sourced by the kit scripts. Reads agent-kit.env from the kit root (the Gradle project dir; may be a git subfolder).
# Sets APP_MODULE, APP_VARIANT, APP_DIR, APPLICATION_ID, BASE_BRANCH, KTFMT_VERSION, NDK_VERSION.
_kit_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_MODULE=":app"; APP_VARIANT="Debug"; APPLICATION_ID=""; BASE_BRANCH="development"; KTFMT_VERSION=""; NDK_VERSION=""
if [ -f "$_kit_root/agent-kit.env" ]; then
  # Not `. <(...)`: macOS's bash 3.2 reads nothing from a process substitution there.
  eval "$(tr -d '\r' < "$_kit_root/agent-kit.env")"
fi
APP_DIR="${APP_MODULE#:}"; APP_DIR="${APP_DIR//://}"
# macOS ships no Java: without JAVA_HOME, use the runtime bundled with Android Studio.
_studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = "Darwin" ] && [ -x "$_studio_jbr/bin/java" ]; then
  export JAVA_HOME="$_studio_jbr"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export APP_MODULE APP_VARIANT APP_DIR APPLICATION_ID BASE_BRANCH KTFMT_VERSION NDK_VERSION
