# JobFinder Helper privacy policy

_Draft, 2026-11-27. Host it at a public URL before submitting (see the note at the end)._

JobFinder Helper ("the extension") is a browser extension for Chrome and Microsoft Edge. It connects
the browser to the JobFinder desktop app on the same computer. This policy explains what data it
handles and where that data goes.

## Summary

- The extension sends data **only to the JobFinder app on your own computer**, through the
  browser's native messaging. It has no server, opens no network ports and makes no network
  requests of its own.
- The developer receives **no data** from the extension. There are no analytics, ads, trackers or
  accounts.
- Every feature that reads data is **off until you turn it on**, and the browser asks you for
  permission per site.

## What the extension handles, and why

| Data | When | Why | Where it goes |
| --- | --- | --- | --- |
| Address (URL) of the open page | Only on job sites you turned on | To look up whether the page is a job saved in JobFinder and show it on the toolbar icon | The JobFinder app on your computer |
| What you type into job application forms (for example name, email, phone, answers to questions) | Only on sites you turned on, when you send a form; with "Save my answers" on | So Autofill can fill the same answers next time | The JobFinder app, where you can view, edit and delete them (Settings → Form answers) |
| Form questions, for AI help | Only when you press AI help | To draft an answer for you to edit | The JobFinder app (see "AI help" below) |
| Text of a job page | Only with the fallback reader on, when the JobFinder app asks for a page it cannot open itself | To add the job's details to JobFinder | The JobFinder app |
| Sign-in cookies of job boards | Only with "Share my job board sign-ins" on, and only for job sites you turned on | So the JobFinder app's own browser is signed in to those job boards and you do not sign in twice | The JobFinder app |
| Your settings and the connection state | Always | To remember your choices | Stored in the browser (`chrome.storage.local`) |

The extension never saves or fills passwords, card or bank details, or ID numbers (such as
passport, Aadhaar, PAN or social security numbers). It never sends a form for you. It never reads
cookies of sites you have not turned on.

## AI help

AI help is a feature of the JobFinder app. When you press it, the extension sends the form question
to the app. The app drafts an answer with the AI service you chose in the app's AI settings: a
local model on your computer (Ollama), an online provider you added a key for (for example NVIDIA,
Groq, OpenRouter, Hugging Face or Google Gemini), or a coding agent you set up yourself (OpenCode or
Antigravity), which may use online models. For an online service, the app sends the question, the
job description and your resume to it, under that service's terms. The extension itself never
contacts an AI service.

## Sharing and selling

The developer does not collect, sell, rent or share your data with anyone. The use of data received
from the extension complies with the
[Chrome Web Store User Data Policy](https://developer.chrome.com/docs/webstore/program-policies/),
including the Limited Use requirements: data is used only to provide the features described above,
is not used for advertising, is not transferred to third parties except as you direct (the AI
provider you choose in the app), and is not read by people.

## Keeping and deleting data

- Settings stay in the browser until you remove the extension.
- Data sent to the JobFinder app stays on your computer, in the app's data folder. Delete form
  answers in the app under Settings → Form answers, or uninstall the app to remove everything.
- Turn any feature off in the extension options at any time. Turning a site off also removes the
  browser permission for that site, unless the fallback reader (all sites) is on.

## Children

The extension is for job seekers and is not directed at children under 13.

## Changes

Changes to this policy are published at this address with a new date at the top.

## Contact

Open an issue at https://github.com/raghavsatyadev/JobFinder/issues.

---

**Hosting note (remove before publishing):** published for both stores at
https://raghavsatyadev.github.io/projects/jobfinder/privacy/ (one page for the desktop app, Android
app and this extension; drafted in the `raghavsatyadev.github.io` repo, not pushed yet). Keep its
extension section in line with this file.
