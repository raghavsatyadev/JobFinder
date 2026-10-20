# Known Flows (per app)

Fill this in for your app. Keep each flow proven. A flow with fixed steps runs as a Maestro flow
(or a Mobly test); keep an ARTEMIS prompt only for flows that are really dynamic, and turn an
ARTEMIS run that found a fixed path into a Maestro flow. Helper script:
`D=.agents/skills/android-device-test/scripts/dev.sh`

Write ARTEMIS prompts as explicit steps: name the screen the app is on, name each element to tap,
and end with what to report.

Launch an app with `am start`, not `monkey` (monkey switches auto-rotate on, SKILL.md gotcha 9):

```bash
adb shell am start -n "$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER <package> | tail -1 | tr -d '
')"
```

## 1. Visit every tab (debug build, fake backend)

* **Primary (Maestro):**
  ```bash
  ~/.maestro/maestro/bin/maestro --platform android --device <serial> test .agents/skills/android-device-test/flows/tabs.yaml
  ```
* **ARTEMIS (only if the flow file is out of date):**
  ```text
  "In JobFinder, on the Jobs tab, tap each bottom tab in turn: Searches, AI filters, Settings,
  Help, then Jobs. Report the heading shown on each."
  ```
* **ADB fallback:**
  ```bash
  bash $D tap "Searches"; bash $D find "Job searches"
  ```
* **Verify:** the landmarks "Search jobs", "Job searches", "What to filter out", "Search
  frequency" and "Questions and answers" appear in turn, and `adb logcat -b crash` has no
  `io.github.raghavsatyadev.jobfinder` entries. Proven on an SM-S938B (2026-10-15).

Note: the "Add search" FAB text is not exposed as a text node; use the screen title instead.
