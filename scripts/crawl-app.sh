#!/usr/bin/env bash
# Goes through the whole app on sample data, off screen: every screen, button and text field, on the
# desktop and phone layouts. Exit code 1 and tmp/crawl/report.md when anything crashed.
# Arguments go to the crawler: out=tmp/crawl layout=both|desktop|phone depth=5 actions=1500 minutes=20
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :desktopCMP:crawlApp -PcrawlArgs="$*"
