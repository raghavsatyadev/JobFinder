#!/usr/bin/env bash
# Run the Maestro flows against the running desktop app: pair, feed, status change, refresh, tabs.
# The pairing code is read from the desktop database and passed to Maestro in memory. Maestro's
# debug output (which records the resolved code) goes to a temp folder that is deleted after.
# Usage: androidCMP/maestro/run.sh [--device <serial>] [--release] [flow.yaml ...]
# Without --device it runs on the first emulator adb lists (device tests run on the emulator).
# JOBFINDER_DB points at another desktop database, such as a scratch copy.
# --release pairs by pasting the code (pair-manual.yaml): release builds have no debug deep link.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
kit="$(cd "$here/../.." && pwd)"
device=()
if [ "${1:-}" = "--device" ]; then
  device=(--device "$2")
  shift 2
else
  emulator="$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
  if [ -z "$emulator" ]; then
    echo "No emulator is running. Start one, or pass --device <serial>." >&2
    exit 1
  fi
  device=(--device "$emulator")
fi
pair=pair.yaml
if [ "${1:-}" = "--release" ]; then
  pair=pair-manual.yaml
  shift
fi
flows=("$@")
if [ ${#flows[@]} -eq 0 ]; then
  flows=("$pair" feed.yaml status.yaml refresh.yaml "$kit/.agents/skills/android-device-test/flows/tabs.yaml")
fi

db="${JOBFINDER_DB:-${LOCALAPPDATA:-$HOME/AppData/Local}/jobfinder-data/jobfinder.db}"
code="$(python - "$db" <<'PY'
import json, socket, sqlite3, sys
db = sqlite3.connect(f"file:{sys.argv[1]}?mode=ro", uri=True)
token, port = db.execute("select pairing_token, server_port from settings limit 1").fetchone()

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.connect(("192.0.2.1", 9))  # the default-route interface, as the server picks it
ip = s.getsockname()[0]
port = port or 47821
code = json.dumps({"url": f"http://{ip}:{port}", "token": token, "version": 1}, separators=(",", ":"))
print(code)
PY
)"
link="f2a-debug://pair?code=$(python -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$code")"

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
cd "$here"
for flow in "${flows[@]}"; do
  echo "== $flow"
  # Without --platform, Maestro 2.11 often reports a running emulator as "not connected".
  MAESTRO_CLI_NO_ANALYTICS=1 maestro --platform android "${device[@]}" test --debug-output "$out" \
    -e PAIR_LINK="$link" -e PAIR_CODE="$code" "$flow"
done
