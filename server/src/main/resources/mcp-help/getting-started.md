# Set up JobFinder

JobFinder runs on the user's Windows computer. It opens the user's saved job searches in its own browser (signed in to the job boards), stores every new job, lets an AI filter out the jobs the user doesn't want, and scores the user's resume against the rest (ATS score). The Android app shows the same jobs while the phone is on the computer's Wi-Fi. JobFinder never applies to jobs; the user applies.

## Job lists in the app
- **New**: jobs that passed the AI filter. The user works from here.
- **Applied**, **Archived**: the user moves jobs here.
- **Filtered out**: jobs the AI filter excluded, with its reason. Kept 30 days by default.
- **Processing**: just found; waiting for the description and the AI filter.

## Setup checklist (check with `app_status`)
1. **AI model.** The app's setup installs Ollama and a local model, so the AI runs on this computer. Free online keys and the OpenCode or Antigravity CLIs can take work off it (help: `ai-keys`).
2. **Resume.** Upload it (`upload_resume`, or Settings → Resume). ATS scores, tips and resume fixes use it (help: `resume-ats`).
3. **Job searches.** 10 to 15 search result pages on the boards the user's field uses (help: `job-searches`).
4. **Sign in to the boards.** The user does this in the app: Setup → Open sign-in. Signed-in searches show every job. An agent can't do this.
5. **AI filter.** A short description of the jobs to filter out, and companies to skip (help: `ai-filter`).
6. **Scan schedule.** `set_scan_schedule`, or Settings → Scanning. Every 12 hours is the default and the most often advised.
7. **Optional:** pair the phone (help: `phone`), add the browser extension (help: `browser-extension`), keep backups (help: `backups`).

`app_status` also lists the app's own setup steps (network, firewall, Ollama, model download, sign-ins, phone, extension) with what is done.

## Working with the user
- Ask for facts: target roles, seniority, locations or remote, lowest salary, skills, companies to avoid. Read `get_resume` when there is one.
- Confirm before you delete searches or keys, or replace the AI filter.
- The app's own Help (questions and step-by-step guides) is the resource `jobfinder://help/app`.
