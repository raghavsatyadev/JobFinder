# JobFinder: Microsoft Store listing (first submission)

Product 9NG3TW4878DR, Store name JobFinderApp, language English (United States).
Paste each block into the matching Partner Center field. Limits are from Microsoft's MSIX listing
docs (checked 2026-11-27).

## Privacy policy URL

https://raghavsatyadev.github.io/projects/jobfinder/privacy/

One page covers the Windows app, the Android app and the JobFinder Helper extension; the Chrome Web
Store listing uses the same URL. It goes live only after the site repo
(`raghavsatyadev.github.io`, file `projects/jobfinder/privacy/index.html`) is pushed.

## Other Properties / Support fields

- Website: https://github.com/raghavsatyadev/JobFinder
- Support contact: https://github.com/raghavsatyadev/JobFinder/issues
- Copyright and trademark info: `© 2026 Raghav Satyadev`
  (the repo has no LICENSE file, so the listing claims no open-source licence)
- Developed by: `Raghav Satyadev`

## Category

- Category: **Business** (Microsoft lists "job search resources" as a Business example).
  Subcategory: none.
- Secondary category: **Productivity**.

## Product declarations

- Tick **This product incorporates generative AI features** (ATS reviews, resume edits and form
  answers are written by an AI model).
- Leave "allows purchases outside the Store commerce system" unticked (the app sells nothing).
- Leave the accessibility declaration unticked unless the Narrator/High Contrast checks are done.
- Leave "install to alternate drives" and "OneDrive backups" at their defaults (checked).
  Note: the app keeps its data in `%LOCALAPPDATA%\jobfinder-data`, outside the package, so Windows'
  app backup does not include it; the app's own locked backups do.

## Short description (≤ 270 chars recommended)

Finds new jobs from your saved LinkedIn, Indeed, Glassdoor and other job board searches as soon as they are posted, filters out the ones you don't want with AI, and scores your resume against each job. No account; your data stays on your PC.

## Description (plain text, ≤ 10,000 chars, no URLs)

JobFinder checks your saved job searches on LinkedIn, Indeed, Glassdoor and other job boards, and shows you the new jobs as soon as they are posted, so you can be one of the first to apply. A local AI model reads each new job, filters out the ones you do not want, and scores how well your resume matches the rest.

There is no account and no server. Your searches, jobs, resume and notes stay on your computer.

FIND NEW JOBS FAST
Paste the link of a search results page from a job board. JobFinder checks it on a schedule, from every hour to once a day, and tells you when new jobs show up. It works with LinkedIn, Indeed, Glassdoor, Naukri, Dice, ZipRecruiter, Wellfound, Built In, Hiring Cafe, Remote OK, Remotive, We Work Remotely, USA Jobs and more; any other board works as a custom board. Searches run with your own job site sign-ins, so you see the same jobs you see when you search yourself.

FILTER OUT WHAT YOU DO NOT WANT
Describe the jobs to skip in plain words ("no Java, remote only, at least $60K") and list companies to skip. The AI moves jobs that do not match to Filtered out and says why.

SCORE AND IMPROVE YOUR RESUME FOR EACH JOB
An ATS review gives a match score, the keywords you have and miss, what would get you screened out, and the top fixes. "Improve my resume for this job" suggests exact before-and-after changes that bring in the job's words without inventing skills or numbers. Make a one-column resume PDF that ATS software reads well, for any job.

KNOW MORE ABOUT A JOB
Company ratings and pay, a ghost-job check (how long the job has been up and other warning signs) and negotiation tips.

FREE AI ONLY
The AI runs on your computer by default, with Ollama and a small open model that the setup downloads for you. If your computer is slow, add free keys from NVIDIA, Groq, OpenRouter, Hugging Face or Google Gemini, or turn on the free OpenCode or Antigravity tools. None needs a payment card, and work that reads your resume stays on your computer unless you choose otherwise.

YOUR PHONE AND YOUR BROWSER
The free JobFinder Android app shows your jobs on your phone over your home Wi-Fi and notifies you about new ones; nothing goes through the internet. The optional JobFinder Helper extension for Chrome and Edge shows a job's ATS score on the toolbar, keeps your answers to application forms and fills them in again for you to check, and copies your job board sign-ins to the app when you ask.

LET YOUR AI AGENT SET IT UP
Claude Code, Gemini CLI, Codex, Cursor and other MCP agents on your computer can craft your searches, AI filter and instructions, and set up free AI keys.

KEEP EVERYTHING TIDY AND SAFE
Sort by date or ATS score, filter by board, search, label and work mode, star, archive or delete with one click or a keyboard shortcut. Back up everything to one locked file, and export your jobs to CSV.

JobFinder does not apply to jobs for you. It finds them early; you apply yourself.

## Product features (≤ 20, each ≤ 200 chars, no bullets)

1. Checks your saved searches on LinkedIn, Indeed, Glassdoor, Naukri, Dice and more, every hour to once a day
2. Shows new jobs as soon as they are posted, with a notification
3. AI filter: describe the jobs to skip in plain words, and see why each job was filtered out
4. ATS review for every job: match score, found and missing keywords, and the top fixes
5. Improve your resume for a job with exact before-and-after edits that never invent skills
6. ATS-friendly resume PDFs, tailored to a job
7. Company ratings, pay and a ghost-job check
8. Free AI only: a local model by default, or free online keys with no payment card
9. Android app on your home Wi-Fi, with new-job notifications
10. Optional Chrome and Edge extension that remembers and refills application form answers
11. Works with Claude Code, Gemini CLI, Codex and other MCP agents
12. No account, no telemetry: your data stays on your PC, with locked backups

## Keywords (≤ 7, each ≤ 40 chars, ≤ 21 words in total)

job search · job alerts · ATS resume checker · resume match · LinkedIn jobs · job tracker · AI job filter
(7 terms, 16 words)

## What's new in this version

Leave blank for the first submission.

## Screenshots (desktop, PNG, 1680 × 1050, sample data)

Folder `docs/store/screenshots/`, in this order, with these captions (≤ 200 chars):

1. `01-jobs.png`: New jobs from your saved searches, with an ATS score on each
2. `02-job.png`: A job's details, notes and labels
3. `03-ats.png`: The ATS review: match score, keywords found and missing, and the top fixes
4. `04-improve.png`: Improve your resume for this job with exact before-and-after edits
5. `05-searches.png`: Your saved searches on any job board
6. `06-ai-filters.png`: Describe the jobs to skip in plain words
7. `07-free-ai.png`: Free online AI keys, used in the order you choose
8. `08-agents.png`: Let Claude Code, Gemini CLI or Codex set up JobFinder for you

They were rendered with the app's own `demoTour` on its built-in sample data (no personal data),
from the same code as the 0.0.60 package. Regenerate with
`.\gradlew.bat -q :desktopCMP:demoTour -PtourScript=docs/video/scripts/desktop.txt -PtourOut=<dir>`.

## Store logos

- 1:1 app tile icon 300 × 300: `docs/store/logos/app-tile-icon-300x300.png` (strongly recommended).
- Box art and poster art: games only, skip.
- 16:9 super hero art (1920 × 1080, no text): optional, not made.
- The package's own logos are in `desktopCMP/msix/Assets` (StoreLogo 50 × 50, Square150x150,
  Square44x44 with target sizes 16–256).

## Age rating (IARC questionnaire)

Expected result: IARC 3+ / PEGI 3 / ESRB Everyone, with the interactive element
"Unrestricted Internet". Suggested answers:

- Category: **Utility, Productivity, Communication or Other** (not a game, not a store, not social
  networking).
- Violence, fear, sexual content, nudity, crude humour, bad language, drugs, alcohol, tobacco,
  gambling or simulated gambling: **No** to each.
- Can users interact or exchange content with other users (chat, sharing)? **No.** The phone app
  talks only to the user's own computer.
- Does the app share the user's current physical location with other users? **No.**
- Does the app allow purchases of digital goods? **No.**
- Does the app give unrestricted access to the internet (for example a web browser or search
  engine)? **Yes**: it has a built-in browser that opens job pages and job sites.
- Does the app collect or share personal information with third parties? Answer **No** for sharing
  with us; if asked about third parties, note that the user may opt in to send job text to online
  AI providers. If the form has no nuance for this, answer Yes and rely on the privacy policy.
