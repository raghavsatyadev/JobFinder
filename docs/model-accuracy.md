# Local model accuracy (Phase 13)

Run on 2026-10-16 on this PC (RTX 5080 16 GB, 64 GB RAM) with
`ModelAccuracyCheck` (now deleted, see Re-run). Each smaller model is compared with
the benchmark `jobfinder-qwen:latest` (Qwen3.6 35B-A3B, `num_ctx 16384`) on:

- **Matching:** the 22 labelled jobs of the [matching replay](matching-replay.md), same prompt.
- **ATS:** 5 jobs (4 Android jobs the old app kept, 1 iOS-only job) against the user's resume.

The small models are the Unsloth GGUFs `hf.co/unsloth/Qwen3-*-GGUF:Q4_K_M`, registered with
`num_ctx 16384`. The three hybrid (thinking) Qwen3 models also need `presence_penalty 1.5`, as
Qwen recommends and as `jobfinder-qwen` has: without it they repeated themselves until the
4,000-token limit on most ATS reviews (runs 1 and 2). Thinking is off in every request
(`reasoning_effort: none`).

| Model | Matching vs benchmark | Matching vs old app | ATS reviews answered | ATS verdict vs benchmark | ATS overall Δ | ATS match Δ | Keyword overlap | s/job matching | s/job ATS | Memory GB |
|---|---|---|---|---|---|---|---|---|---|---|
| `jobfinder-qwen` (benchmark) | 22/22 | 20/22 | 5/5 | – | – | – | – | 2.7 | 26 | 22.5 (13.1 on GPU) |
| Qwen3 14B | 11/22 | 13/22 | 4/5 | 2/4 | 1.3 | 22.8 | 38% | 0.7 | 36 | 11.6 |
| Qwen3 8B | 19/22 | 19/22 | 4/5 | 2/4 | 0.7 | 12.8 | 32% | 0.4 | 22 | 7.3 |
| Qwen3 4B Instruct 2507 | 17/22 | 19/22 | 5/5 | 3/5 | 0.9 | 14.2 | 53% | 0.3 | 11 | 5.1 |
| Qwen3 1.7B | 10/22 | 12/22 | 3/5 | 1/3 | 1.7 | 35.0 | 25% | 0.3 | 8 | 3.1 |

ATS per job (overall / keyword match % / shortlisted):

| Model | 1785 Senior Android Engineer | 1923 Android SDE 2 | 1989 Staff SWE Android | 2057 Senior SWE Android | 2121 Senior iOS (native) |
|---|---|---|---|---|---|
| `jobfinder-qwen` | 7.1 / 64 / yes | 7.9 / 64 / yes | 7.1 / 50 / no | 7.4 / 48 / no | 3.5 / 31 / no |
| Qwen3 14B | 7.9 / 80 / no | 9.2 / 92 / no | 8.7 / 86 / no | failed | 4.9 / 20 / no |
| Qwen3 8B | 8.4 / 88 / yes | 7.8 / 58 / no | 8.0 / 63 / yes | 8.0 / 56 / no | failed |
| Qwen3 4B Instruct 2507 | 7.6 / 40 / no | 7.2 / 29 / no | 7.8 / 48 / no | 7.9 / 43 / no | 5.7 / 26 / no |
| Qwen3 1.7B | 8.1 / 100 / yes | failed | 7.8 / 83 / yes | failed | 7.0 / 67 / yes |

## Findings

- **No small model is a drop-in replacement.** The best matching agreement is 19/22 (Qwen3 8B);
  the benchmark itself agrees 20/22 with the old app.
- **Qwen3 14B is worse than 8B at matching:** it excluded 9 of the 12 Android jobs the benchmark
  and the old app kept. Bigger is not safer within this family at Q4.
- **Qwen3 1.7B keeps nearly everything** (12 of the 22 decisions differ) and inflates ATS keyword
  lists (it lists keywords the resume has); it should not be offered for matching.
- **Qwen3 4B Instruct 2507** is the most reliable small model: every answer valid, ATS overall
  within 0.9, but it lists fewer matching keywords, so its keyword match runs about 14 points low
  and it never shortlists.
- **Small models flatter the resume:** the hybrids give 8–9 overall where the benchmark gives
  7–8, and 1.7B shortlisted the iOS-only job.
- Times include model swaps with the agent kit's `nimble` on the same GPU; the first run stalled
  about 10 minutes until `nimble`'s keep-alive ran out.

## Tiers for the setup guide

| PC | Suggested model | Expect |
|---|---|---|
| 24 GB+ RAM, 12 GB+ VRAM | `jobfinder-qwen` (benchmark) | Full accuracy |
| 16 GB RAM, 8 GB VRAM | Qwen3 8B (with `presence_penalty 1.5`) | Matching mostly agrees (19/22); some ATS reviews fail |
| 8–16 GB RAM, 4–6 GB VRAM | Qwen3 4B Instruct 2507 | Every answer valid; matching 17/22; ATS keyword match runs low |
| Less | An online model with the user's own key (Phase 13) | – |

**For the model sources feature:** a model added from Hugging Face or a URL must be registered
with the model's recommended sampling settings (for Qwen3 hybrids `presence_penalty 1.5`) and
`num_ctx 16384`, and the accuracy check must treat an unfinished answer (`length`) as a failure.

## Re-run

Not available. The check (`ModelAccuracyCheck`) was deleted with the unit tests on 2026-10-17;
it is in git history before that date.

## In the app

Settings → AI model → Models on this computer (desktop) shows the PC's tier from this table,
adds a model from the Ollama library, Hugging Face (`hf.co/<repo>:<quant>`) or a direct GGUF link,
registers it as `jobfinder-<name>` with `num_ctx 16384` (and `presence_penalty 1.5` for Qwen3
hybrids), then runs `AccuracyCheck`: the AI filter on the 6 latest kept and 6 latest AI-excluded
jobs, and the 2 latest ATS reviews, compared with the answers saved in the database. Results are
kept in `%LOCALAPPDATA%\jobfinder-data\model-checks.json`.
