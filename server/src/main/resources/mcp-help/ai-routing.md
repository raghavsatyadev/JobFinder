# Which model does which work

Each kind of AI work (task) has an ordered list of models. The first one with quota left answers; a model that fails or runs out hands the work to the next. The main AI model (usually the local one) is the last resort.

Tasks: `matching` (AI filter), `parsing` (reading job pages), `insights` (company ratings and salary), `ghost` (careers-site check), `ats`, `tips`, `answers` (form answers). The last three send the resume.

## Shape (`get_ai_routing` → `routing`, and `set_ai_routing`)
```json
{
  "routes": {
    "matching": [
      {"keyId": "<key id from list_ai_keys>", "model": "openai/gpt-oss-120b"},
      {"keyId": "opencode-cli", "model": ""},
      {"keyId": null, "model": ""}
    ]
  },
  "openCode": {"enabled": true, "model": "deepseek-v4.1-flash-free"},
  "agy": {"enabled": false, "model": "gemini-3.8-flash-low"}
}
```
- `keyId` null: the main AI model. `"opencode-cli"` / `"agy-cli"`: those CLIs, only while `enabled`.
- A blank `model` uses the key's own model or the CLI's chosen one.
- A task missing from `routes` uses the default: online keys (most free quota first), then the CLIs that are on, then the main model; resume tasks only the main model.
- `effective` in `get_ai_routing` shows what each task really uses now.

## Tips
- Put fast, generous keys first for `matching` and `parsing`: they run on every new job.
- A provider that often hangs slows every task behind it; move it later.
- Keep the main model last in every list as the fallback.
