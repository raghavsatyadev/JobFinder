# Edit the AI instructions

Each kind of AI work has editable instructions (`list_ai_prompts`). Only the instructions change: the job, page or resume the app adds, and the answer format, stay fixed around them.

| id | What it does |
|---|---|
| `matching` | Keeps or filters out each new job (with the user's filter description). |
| `parse_list` | Reads the job list on a board without a built-in reader. Reads job boards. |
| `parse_description` | Reads a job's description on such a board. Reads job boards. |
| `ats_review` | Scores and reviews the resume against a job. |
| `ghost_match` | Decides whether a careers-site job is the board's job when titles differ. |
| `negotiation_tips` | Writes salary negotiation tips for a job. |
| `resume_improve` | Writes exact before/after resume edits for a job. |

## Rules
- Keep every word in `mustKeepWords`: they name the answer's fields. `set_ai_prompt` refuses text without them.
- Prompts that read job boards (`parse_list`, `parse_description`) can stop jobs coming in when they go wrong; `set_ai_prompt` needs `confirm=true` after the user agrees.
- Don't add JSON, schema or format talk: the app fixes the format, and extra format words confuse small models.
- Short and concrete beats long: the default model is a small local one.
- `try_ai_prompt` works for `matching` and `ats_review`. `reset_ai_prompt` puts back the app's text.
