# AI models and free keys

By default the AI runs on this computer with Ollama and a local model: nothing leaves the computer, but it is slow on weak hardware. Free sources can take work off it. None needs a payment card.

## Free online keys
`list_free_ai_providers` gives each provider's key page, model, free limits and whether the free plan may keep prompts. In short:
- **NVIDIA** (build.nvidia.com → API Keys; key starts `nvapi-`): about 40 a minute, no daily limit.
- **Groq** (console.groq.com → API Keys; `gsk_`): 30 a minute, 1,000 a day.
- **OpenRouter** (openrouter.ai → Keys; `sk-or-`): free models, 50 a day. Free models may keep prompts, and free model names change: check the key's model still answers.
- **Hugging Face** (huggingface.co → Access Tokens; `hf_`): small monthly free credits.
- **Google Gemini** (aistudio.google.com → Get API key; `AIza`): the free plan may use prompts to train.

Advise the user to set the key to expire in 90 days on the site, so a leaked key stops working by itself.

The user adds a key in Settings → Free online AI (best: the key doesn't pass through a chat), or you add it with `add_ai_key` when they ask. The app checks the key with the provider and keeps it in the computer's protected store; `list_ai_keys` never shows the key itself.

## No-key sources on this computer
- **OpenCode CLI:** `npm install -g @opencode/cli`; free models (DeepSeek and GLM keep nothing; Beam keeps 30 days). Turn on in Settings → Free online AI.
- **Antigravity CLI (`agy`):** Gemini Flash on the user's Google sign-in; run `agy` once to sign in. About 15 seconds an answer.

## Privacy default
Public job text (filter, page reading, company insights, careers checks) goes online first. Work that sends the resume (ATS, tips, form answers) stays on this computer unless the user routes it online. See `ai-routing`.
