# JobFinder Helper: store listing draft

Text for the Chrome Web Store dashboard and Edge Add-ons (Partner Center). Paste it, don't
rephrase it in the dashboard: the privacy answers must match [PRIVACY.md](PRIVACY.md).

## Name and summary

- **Name** (from the manifest): JobFinder Helper
- **Summary / short description** (manifest `description`, 115 of 132 characters):
  Connects the JobFinder app on your PC to this browser: shows saved jobs and reads pages the app cannot open itself.
- **Category:** Chrome: Productivity → Tools (or "Workflow & Planning"). Edge: Productivity.
- **Language:** English.
- **Homepage / website:** https://github.com/raghavsatyadev/JobFinder
- **Support:** https://github.com/raghavsatyadev/JobFinder/issues

## Long description (≈1,600 characters; Edge needs 250 to 10,000)

```
JobFinder Helper connects your browser to JobFinder, the free job-search app that runs on your own PC. It needs the JobFinder desktop app (Windows, macOS or Linux) installed on the same computer. Without the app, the extension does nothing.

What it does
• Saved-job badge. On job sites you turn on, the toolbar icon shows whether the page you have open is a job saved in JobFinder, with its ATS score. Open the popup to see the job.
• Application forms. On sites you turn on, the extension can remember what you type into a job application form when you send it. Next time, Autofill fills empty fields from those answers, and AI help drafts an answer from your resume and the job for you to edit. Neither ever sends a form for you. Passwords, card and bank details and ID numbers are never saved or filled.
• Fallback reader. Some job sites block the JobFinder app. If you turn on the fallback reader, the app can ask the browser to open such a page in a background tab, read its text and close it again.
• Sign-in sharing (off by default). Copies your job-board sign-in cookies to the JobFinder app's own browser, so you don't sign in twice.

Private by design
• It talks only to the JobFinder app on this PC, through the browser's native messaging. It opens no network ports and has no servers of its own.
• Nothing is granted at install: the browser asks for permission per site when you turn a site on.
• No analytics, no ads, no tracking, no account.

Get the JobFinder app: https://github.com/raghavsatyadev/JobFinder/releases.
```

## Single purpose (Chrome Privacy tab and Edge Privacy step)

```
Connect the browser to the JobFinder desktop job-search app on the same PC, so the app can show its saved jobs on job sites, save and fill job application form answers, and read job pages that the app cannot open by itself.
```

## Permission justifications

| Permission | Justification to paste |
| --- | --- |
| `nativeMessaging` | The extension's only channel to the JobFinder desktop app on the same PC (host `io.github.raghavsatyadev.jobfinder`). All features send and receive data through it; the extension has no server and makes no network requests. |
| `storage` | Saves the user's settings (which sites are on, feature switches) and the connection state shown in the popup, in chrome.storage.local. |
| `scripting` | On sites the user turned on: registers the form content script, collects form fields when the user presses Autofill and fills them. For the fallback reader: reads the text of a page the app asked for in a background tab. Only bundled functions and files run; no code strings. |
| `alarms` | Retries the connection to the desktop app after the service worker has been asleep. |
| `activeTab` | Lets the popup see the URL of the tab the user is on, to offer the switch for that site and show its saved job. |
| `cookies` (optional) | Asked only when the user turns on "Share my job board sign-ins". Reads the cookies of job-board sites that the user turned on and sends them to the JobFinder app on the same PC, so the app's own browser is signed in. Never other sites' cookies. |
| Host permissions (optional, `<all_urls>`) | Nothing is granted at install. The browser asks per site when the user turns a job site on (badge and form features). The fallback reader asks once for all sites, with an explanation, because the app decides which job page needs reading. |

**Remote code:** "No, I am not using remote code." All JavaScript is bundled in the package; the
extension loads no scripts, no `eval`, no WebAssembly from the network.

## Data usage (Privacy tab checkboxes)

Tick these. Every one goes only to the JobFinder app on the user's own PC, and only after the user
turns the feature on.

| Category | Ticked | What and why |
| --- | --- | --- |
| Personally identifiable information | Yes | Answers the user types into job application forms (name, email, phone, address) are saved to the app for Autofill. |
| Health information | No | |
| Financial and payment information | No | Card and bank fields are detected and skipped. |
| Authentication information | Yes | Job-board sign-in cookies, only with "Share my job board sign-ins" on. Passwords are never read. |
| Personal communications | No | |
| Location | No | |
| Web history | Yes | The URL of pages on job sites the user turned on, to look up the saved job for the badge. |
| User activity | No | No clicks, keystrokes or scrolling are recorded; form values are read only when the user sends a form or asks for Autofill. |
| Website content | Yes | Text of job pages read by the fallback reader, and form questions sent for AI help. |

Certifications: tick all three ("I do not sell or transfer user data to third parties, outside of
the approved use cases", "… not use or transfer user data for purposes unrelated to my item's
single purpose", "… not use or transfer user data to determine creditworthiness or for lending
purposes").

**Privacy policy URL:** https://raghavsatyadev.github.io/projects/jobfinder/privacy/ (text in [PRIVACY.md](PRIVACY.md); live once that site is pushed).

## Test instructions (Chrome "Test instructions" tab, Edge "Notes for certification")

```
The extension needs the free JobFinder desktop app on the same computer. Without it, the popup shows "JobFinder helper is not installed" and no feature works.
1. Install JobFinder for Windows from https://github.com/raghavsatyadev/JobFinder/releases (MSI), and start it once. It registers the native messaging host for Chrome and Edge.
2. Open the extension popup: it shows "Connected to JobFinder <version>".
3. In JobFinder, search for jobs and save one. In the extension options, turn on the job site (for example linkedin.com) and open that job: the toolbar icon shows the score.
4. Options → Application forms: open a job application form on a turned-on site, fill it, send it; on the next form press Autofill.
No login or test account is needed.
```

## Images

All in [images/](images/). 1280×800 suits both stores.

| Asset | Chrome Web Store | Edge Add-ons | File |
| --- | --- | --- | --- |
| Icon | 128×128 in the zip (96×96 art, 16 px padding) — required | — | `static/icons/icon128.png` |
| Logo | — | 300×300 (min 128), 1:1 — required | `edge-logo-300.png` |
| Screenshots | 1 to 5, 1280×800 or 640×400 — at least 1 required | up to 6, 1280×800 or 640×480 — optional | `screenshot-0-popup`, `-1-settings`, `-2-privacy-switches` |
| Small promo tile | 440×280 — optional, ranks higher with it | 440×280 — optional | `promo-small-440x280.png` |
| Marquee / large promo tile | 1400×560 — optional, needed to be featured | 1400×560 — optional | `promo-marquee-1400x560.png` |

Still worth adding by hand (needs a saved job and a real job site, so not captured here):
1. The toolbar badge and popup on a saved LinkedIn or Naukri job (score visible).
2. An application form with the Autofill / AI help buttons under a field.
Capture each as a 1280×800 browser window screenshot.
