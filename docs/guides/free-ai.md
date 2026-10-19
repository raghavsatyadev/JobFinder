# Free AI

By default all AI runs on your computer with Ollama and a small open model: nothing you send to it
leaves the computer, but a slow computer takes a while for each job. Free online keys and free
command-line tools can take work off it. **None of them needs a payment card.**

## Free online keys

Add them in **Settings → Free online AI**. The app checks each key with the provider and keeps it
with Windows data protection; keys never go into logs, the database or the phone.

| Provider | Where to get a key | Free use |
|---|---|---|
| NVIDIA | build.nvidia.com → API Keys (`nvapi-…`) | About 40 a minute, no daily limit |
| Groq | console.groq.com → API Keys (`gsk_…`) | 30 a minute, 1,000 a day |
| OpenRouter | openrouter.ai → Keys (`sk-or-…`) | Free models, 50 a day; free models may keep prompts |
| Hugging Face | huggingface.co → Access Tokens (`hf_…`) | Small monthly credits |
| Google Gemini | aistudio.google.com → Get API key (`AIza…`) | The free plan may use prompts for training |

Set each key to expire in about 90 days on the provider's site, so a leaked key stops working by
itself.

## Free tools on your computer, no key

- **OpenCode CLI** with its free models. Install it, then turn it on in Settings → Free online AI.
- **Antigravity CLI (`agy`)**: Gemini Flash on your Google sign-in. Run `agy` once to sign in.

## Which AI does which work

Each kind of work (AI filter, reading job pages, company details, ghost-job check, ATS review,
tips, form answers) has an ordered list. The first one with quota left answers; one that fails or
runs out hands the work to the next, and your local model is always the last.

- Public job text goes online first.
- **Work that sends your resume (ATS review, tips, form answers) stays on your computer** unless
  you choose otherwise.
- Settings shows which provider handles each kind of work, and lets you reorder the list, set the
  model and limits of each key, and pause all AI work.
