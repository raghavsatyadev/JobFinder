# Advanced matching replay (Phase 8)

Run on 2026-10-15. The old app's advanced-matching decisions, replayed through the new `OllamaJobCheck` on the same local model and the same user prompt.

- **Sample:** every job the old app kept (12) and 10 random jobs it excluded by the LLM (blacklist exclusions left out), all decided after the prompt was last refined (2026-10-14 18:00 UTC), read from the old Supabase database.
- **Result:** 20 of 22 decisions agree (91%). Every exclusion agrees, most with the same reason word for word.
- **The 2 differences** are jobs the old app kept and the new run excludes (2057: experience; 3496: location). The old backend's logs show both were real model answers, not failures, and the inputs (title, location, work mode, tags, description) are identical. So these are the local model's run-to-run variation on borderline jobs, even at temperature 0.
- **Re-run:** not available. The replay harness (`MatchingReplayTest`) was deleted with the unit tests on 2026-10-17; it is in git history before that date.

Model `jobfinder-qwen:latest`, 22 jobs, agreement 20/22.

| Job | Title | Company | Old app | New app | Agree | Time |
|---|---|---|---|---|---|---|
| 1785 | Senior Android Engineer / EV Charging / London (Hybrid) - CROSS PLATFORM (Chance to work w | La Fosse | kept | kept | yes | 16s |
| 1798 | Native IOS/Android Developer Lead | Dawn InfoTek | kept | kept | yes | 1s |
| 1907 | Mobile AVP Software Engineer | Barclays | kept | kept | yes | 1s |
| 1912 | Software Engineer HMI Application | emagine | kept | kept | yes | 0s |
| 1923 | Android - SDE 2 | Primetrace | kept | kept | yes | 0s |
| 1924 | Mobile Dev (Android) IRC298357 | GlobalLogic | kept | kept | yes | 0s |
| 1925 | Software Engineer HMI Application | emagine | kept | kept | yes | 0s |
| 1989 | Staff Software Engineer - Capital One Travel - Android | Capital One | kept | kept | yes | 1s |
| 2057 | Senior Software Engineer-Android | Air Arabia | kept | excluded: Minimum experience is 3 years, which is less than the required 10 years. | **no** | 0s |
| 2058 | Senior Embedded Android Developer | Persistent Systems | kept | kept | yes | 0s |
| 2715 | Senior Mobile Engineer | act digital | kept | kept | yes | 0s |
| 3496 | Staff Software Engineer, Android Design System | Reddit, Inc. | kept | excluded: Location is San Francisco. User allows abroad relocation only in specific countries; US is | **no** | 1s |
| 2121 | Senior iOS Developer (Native) | Sidekick Interactive | excluded: Role is iOS-only; user requires native Android or Android-heavy roles. | excluded: Role is iOS-only; user requires native Android or Android-heavy roles. | yes | 0s |
| 2708 | Lead Security Engineer | Calance | excluded: Role is Security/Offensive Security, not native Android. Primary focus conflicts with requ | excluded: Role is Security/Offensive Security, not Android development. Also requires US work author | yes | 1s |
| 1792 | Senior Mobile Engineer | Omaze | excluded: Primary stack is React Native, not native Android. User requires native Android focus. | excluded: Primary stack is React Native, not native Android. User requires Android-heavy roles. | yes | 1s |
| 1799 | Senior Android Engineer | TekRek | excluded: Contract role. User explicitly excludes contract roles. | excluded: Contract role. User explicitly excludes contract roles. | yes | 0s |
| 2212 | Sr. Manager - Hardware and Firmware Engineering | American Innovations | excluded: Role is firmware/hardware engineering, not native Android. Primary stack is C/C++/embedded | excluded: Role is firmware/hardware engineering, not native Android. Primary stack is C/C++/embedded | yes | 1s |
| 1830 | Senior UX Researcher | Okta | excluded: Role is UX Researcher, not native Android developer. Primary focus is research, not Androi | excluded: Role is UX Researcher, not native Android developer. Primary focus is research, not Androi | yes | 1s |
| 2049 | Team Lead - Mechanical | Rockwell Automation | excluded: Role is Mechanical Engineering, not Android/mobile development. | excluded: Role is Mechanical Engineering, not Android/mobile development. | yes | 0s |
| 2205 | IT Division Manager | New Jersey Courts | excluded: Role is IT infrastructure/management, not native Android development. Primary focus is Win | excluded: Role is IT/Infrastructure management, not native Android development. Primary focus is Win | yes | 1s |
| 1795 | Senior Product Marketing Executive | Apadmi | excluded: Role is Product Marketing, not a native Android development role. | excluded: Role is Product Marketing, not a native Android development role. | yes | 0s |
| 1914 | Principal Mobile Engineer | Payoneer | excluded: Location is Gurugram, which is not in the allowed list of Indian cities. | excluded: Location is Gurugram, which is not in the allowed list of Indian cities. | yes | 1s |

# Merged AI calls replay (2026-10-18)

The AI filter and the ATS review for the same job, first one request per task and then in one merged request (`MergedJobCheck`), on each online key the user set up. 6 jobs with saved filter decisions, the current resume, real keys from the app's protected store, a copy of the live database.

- **Re-run:** `./gradlew :desktopCMP:aiReplay --args="tmp/ai-replay.md 6"` (`desktopCMP/.../desktop/dev/AiReplay.kt`). A model that doesn't answer a first job in 60 s is skipped; a call out of quota waits 65 s and is tried again (up to 3 times).
- **NVIDIA deepseek-v4.1-flash and OpenRouter deepseek-v4-flash:free** didn't answer in 60 s (the smoke run on 2026-10-18 00:18 saw NVIDIA time out at 3 min twice per call), so they were skipped.
- **Groq gpt-oss-120b:** the merged request is over the free key's per-request limit (HTTP 413, about 8,000 tokens a minute), so every merged call fell back to one by one. The app now stops merging on a model after its first 413 (until restart), so this costs one wasted request, not one per job.
- **Result:** merging saved nothing on any key that answered. It needs a model with a bigger per-request limit to compare answers; replay again when one is set up.

| Model | Mode | Requests/job | Filter agrees | ATS same as alone (±1) | Errors | s/job |
|---|---|---|---|---|---|---|
| NVIDIA / deepseek-ai/deepseek-v4.1-flash | skipped | - | - | - | no answer in 60 s | - |
| OPENROUTER / deepseek/deepseek-v4-flash:free | skipped | - | - | - | no answer in 60 s | - |
| GROQ / openai/gpt-oss-120b | alone | 2.00 | 3/6 | - | 0 | 84 |
| GROQ / openai/gpt-oss-120b | merged | 1.00 | 3/6 | 0/0 | 6 (413, no ATS part) | 33 |

"Filter agrees" is against the saved decisions, made by the local model; Groq agreeing on 3 of 6 is the model's view, not a merge effect (same in both modes).
