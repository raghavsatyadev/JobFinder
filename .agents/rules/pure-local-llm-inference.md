---
trigger: model_decision
description: Changing any AI/LLM code (advanced matching, custom LLM job parser, ATS, prompts, LlmClient, AI settings). Local Ollama jobfinder-qwen is the default; online providers only with the user's own key kept in DPAPI; never nimble; matching fails open.
---

# RULE: LOCAL FIRST, USER'S OWN KEY FOR ONLINE

**Context:** all AI in JobFinder (advanced job matching, the custom LLM list parser, ATS reviews)
goes through one `server/ai/LlmClient`. The default is the user's local Ollama through its
OpenAI-compatible API. Since Phase 13 the user may choose, in desktop Settings → AI model, an online
provider (OpenAI, Anthropic, Gemini, OpenRouter, any OpenAI-compatible server) with their own API key.

**Strict constraints:**

1. **Default.** `http://localhost:11434/v1`, model `jobfinder-qwen:latest`. A fresh install and
   every test default to it. Never call an online provider unless the user chose it in Settings.
2. **Never `nimble`.** `nimble*` is the agent kit's decision model (`lm-ask`, `jgl`, hooks).
   `LlmConfig` and `SettingsEntity.withAi` reject any model name starting with `nimble`, for every
   provider. Never force-unload `nimble` from app code.
3. **API keys.** Kept only in `SecretStore` (DPAPI files in `%LOCALAPPDATA%\jobfinder\secrets`,
   one per provider). Never in SQLite, logs, error messages (`LlmClient` masks them), exports,
   backups or anything sent to the phone. The AI settings exist only on the desktop (`AiChoice`).
4. **Model swaps.** Local models share one GPU, so Ollama may swap before answering: connect
   timeout 10 s, request timeout 15 min (online 3 min), exactly one retry on timeout, 429, 5xx or
   connection error. One LLM semaphore is shared by matching, the custom parser and ATS.
5. **Fail open.** If the matching call fails (error, timeout, bad JSON, unfinished answer such as
   `finish_reason != stop` or Anthropic `max_tokens`), keep the job. Company blacklist (exact
   lower-case match) is checked before the LLM.
6. **Usage log.** After a successful parse, record token counts only (no cost) in `ai_usage_daily`.
7. **Tests** use `ktor-client-mock`; they never call a real LLM or a real provider.
