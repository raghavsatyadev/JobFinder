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
| Host permissions (optional, `<all_urls>`) | Nothing is granted at install. The browser asks per site when the user turns a job site on (badge and form features). Job sites are not a fixed list: the JobFinder app finds jobs on many boards, company career pages and applicant-tracking systems, and the user can turn on any of them from the popup. The fallback reader asks once for all sites, with an explanation, because the app decides which job page needs reading (a job's own page on its company site) and it runs in the background, where the browser cannot show a per-site prompt. |

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
| Authentication information | No | The store version has no sign-in sharing and no `cookies` permission. Password fields are detected and skipped. |
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
The extension needs the free JobFinder desktop app on the same computer. Without it, the popup shows "The JobFinder desktop app is not installed" with a download link, and no feature works.
1. Install JobFinder for Windows from https://github.com/raghavsatyadev/JobFinder/releases (MSI), and start it once. It registers the native messaging host for Chrome and Edge.
2. Open the extension popup: it shows "Connected to JobFinder <version>".
3. In JobFinder, search for jobs and save one. In the extension options, turn on the job site (for example linkedin.com) and open that job: the toolbar icon shows the score.
4. Options → Application forms: open a job application form on a turned-on site (for example a job-boards.greenhouse.io form), click a field and press Autofill. Check the fields; the extension never sends the form.
No login or test account is needed.
```

## Images

All in [images/](images/). 1280×800 suits both stores.

| Asset | Chrome Web Store | Edge Add-ons | File |
| --- | --- | --- | --- |
| Icon | 128×128 in the zip (96×96 art, 16 px padding) — required | — | `static/icons/icon128.png` |
| Logo | — | 300×300 (min 128), 1:1 — required | `edge-logo-300.png` |
| Screenshots | 1 to 5, 1280×800 or 640×400 — at least 1 required | up to 6, 1280×800 or 640×480 — optional | `screenshot-0-popup`, `-1-settings`, `-2-privacy-switches`, `-3-saved-job-badge`, `-4-autofill` |
| Small promo tile | 440×280 — optional, ranks higher with it | 440×280 — optional | `promo-small-440x280.png` |
| Marquee / large promo tile | 1400×560 — optional, needed to be featured | 1400×560 — optional | `promo-marquee-1400x560.png` |

Screenshots 3 and 4 use a real job (Deliveroo, Senior Software Engineer - Android): the LinkedIn job
page with the app's saved data, and the same job's Greenhouse application form filled with sample
answers (Ada Lovelace). The form was never sent.

## Uploading

Build with `npm run package:first-upload` (store build: no sign-in sharing, no `cookies`, manifest
`key` removed). It writes two zips to `dist-zip/`:

- **Chrome Web Store, first upload only:** `jobfinder-helper-<version>-first-upload.zip`. It holds
  `key.pem`, which makes the store keep the id `cnleajglbkmfgfkfihheoacmojpklpop` that the
  JobFinder app's native host already allows. Upload any other zip first and the item gets a new
  id that the app does not allow. Later updates use the plain zip; `key.pem` is ignored after the
  first upload anyway.
- **Edge Add-ons:** the plain `jobfinder-helper-<version>.zip`. Edge assigns its own id and has no
  `key.pem` route.

The key stays out of git. A copy is in the user's Drive folder (JobFinder → ChromeWebStore).

## Edge id: after the first Edge upload

Edge gave the extension its own id: `bhlgbhbkomplehemejkaipddkjcjjdol` (CRX ID in Partner Center,
Store ID 0RDCKFZ3SFJR). Until the app allows that id, the Edge store build gets "forbidden" from the
native host. Then:

1. Done: `chrome-extension://bhlgbhbkomplehemejkaipddkjcjjdol/` is in `ALLOWED_ORIGINS` in
   `desktopCMP/src/main/kotlin/.../desktop/nativehost/NativeHostRegistration.kt` (next to the
   Chrome id).
2. Ship a JobFinder app release; the app rewrites the host manifest on start.
3. Note in the Edge certification notes that it needs that app version or later.

If Partner Center shows the id before you submit for review, ship the app release first. If not,
expect one review pass that sees "forbidden"; ship the app release, then resubmit.
