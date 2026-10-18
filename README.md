# JobFinder

JobFinder checks your saved job searches on LinkedIn, Indeed, Glassdoor and other job boards, and
shows you the new jobs as soon as they are posted, so you can be one of the first to apply. It runs
on your own Windows computer. The Android app shows the same jobs on your phone over your home
Wi-Fi. A local AI model reads each new job, filters out the ones you do not want, and scores how
well your resume matches the rest.

There is no account and no server. Your searches, jobs, resume and notes stay on your computer.

▶ [Watch a two-minute tour of the phone app](docs/video/tour.mp4): jobs, a job’s details, sorting, searches, AI filters and settings.

<p align="center">
  <img src="docs/screenshots/desktop/jobs-dark.png" width="72%" alt="Jobs on the desktop app">
  <img src="docs/screenshots/phone/jobs-dark.png" width="22%" alt="Jobs on the phone app">
</p>

## What it does

- **Finds new jobs fast.** Paste the link of a search results page from a job board. JobFinder
  checks it on a schedule (every hour to once a day) and tells you when new jobs show up.
- **Works with the job boards you use.** LinkedIn, Indeed, Glassdoor, Naukri, Dice, ZipRecruiter,
  Wellfound, Built In, Hiring Cafe, Remote OK, Remotive, We Work Remotely, USA Jobs and more. Any
  other board works as a custom board: give it a name, or JobFinder names it from the web address.
- **Uses your own sign-ins.** Searches run in a browser on your computer with your job site
  sign-ins, so you see the same jobs you see when you search yourself.
- **Filters out what you do not want.** Describe the jobs to skip in plain words ("no Java, remote
  only, at least $60K") and list companies to skip. The AI moves jobs that do not match to
  Filtered out and says why.
- **Scores your resume against each job.** An ATS review gives a match score, the keywords you
  have and miss, what would get you screened out, and the top fixes.
- **Tells you more about a job.** Company ratings and pay, a ghost-job check (how long the job has
  been up and other warning signs) and negotiation tips.
- **Helps you fill in applications.** With the browser extension, JobFinder keeps what you type in
  application forms and can fill the same questions next time, or draft an answer from your
  resume and the job. It never sends a form for you.
- **Keeps your jobs tidy.** Sort by date, ATS score or work mode; filter by board, search, label
  and work mode; archive or delete with one click or a keyboard shortcut. Jobs whose posting was
  removed are deleted, and filtered-out jobs are deleted after 30 days (you choose the days, or
  keep them).
- **Backs up everything.** One file with your searches, jobs, notes, ATS reviews and settings,
  always locked: with a password it opens on any computer or phone, without one only on the device
  that saved it. The computer also makes a locked backup each day. A CSV export of your jobs opens
  in any spreadsheet app.

JobFinder does not apply to jobs for you. It finds them early; you apply yourself.

## Your computer and your phone

The desktop app does the work: it runs the searches, the AI and the database. Keep it open while
you look for a job. It can keep the computer awake so searches keep running.

The Android app is a window into the desktop app. Pair it once (both screens show the same code;
you click Allow on the computer). After that the phone shows your jobs, lets you archive and apply,
and notifies you about new jobs while it is on the same Wi-Fi as the computer. Away from home it
shows what it saw last.

Nothing goes through the internet between the two. The phone talks to the computer on your home
network only, with a key made when you paired.

## The AI

By default all AI runs on your computer with [Ollama](https://ollama.com) and a small open model
that the setup downloads for you. Nothing you send to it leaves the computer.

If your computer is slow, you can add free API keys from NVIDIA, Groq, OpenRouter or Google Gemini
(no payment card needed). Job pages then go to the online keys first and your computer takes over
when they run out for the day. Work that reads your resume stays on your computer unless you choose
otherwise. Keys are kept with Windows data protection and never go into logs, the database or the
phone; only locked backups made on the computer carry them.

## The browser extension

JobFinder Helper is an optional extension for Chrome and Edge. It comes with the desktop app; the
setup opens its folder so you can load it.

- Shows the ATS score on the toolbar when the job you are looking at is in JobFinder.
- Keeps your answers to application forms (never passwords, card or bank details, or ID numbers)
  and fills them in again, or drafts new ones with the AI, for you to check.
- Reads pages that block the app's own requests, in a background tab.
- Copies your job board sign-ins from your browser to the app, when you ask, so you sign in only
  once (only job board cookies, never other sites).

It talks only to the desktop app on the same computer, through the browser's native messaging. It
opens no ports and sends page data nowhere else. See
[browser-extension/README.md](browser-extension/README.md).

## Privacy

- No account, no sign-up, no telemetry, no server of ours.
- Your data is in one folder on your computer (`%LOCALAPPDATA%\jobfinder`, or `~/.jobfinder` on Linux).
- Job boards see the same requests your own browser makes when you search.
- The phone connects only on your local network, and only after you allow it on the computer.
- Online AI is off until you add a key. The app shows which provider handles each kind of work.

## Install

Download the latest release from the [Releases](../../releases) page.

**Windows (10 or 11).** Run `JobFinder-<version>.msi`. Windows may warn that the publisher is
unknown: choose More info, then Run anyway. Open JobFinder from the Start menu.

**Linux (Ubuntu, Debian and others with .deb packages; preview).** Run
`sudo apt install ./jobfinder_<version>_amd64.deb` and open JobFinder from the app menu. The setup
installs Ollama with Ollama's own install script and asks for your password once. Phone pairing
and the browser extension work as on Windows; the network and firewall are left to you.

**Android (8.0 or newer).** Install `JobFinder-<version>.apk` on the phone (allow installs from your
browser or file manager when asked).

## Set up

The desktop app opens a short setup the first time: three pages, each step checks itself, and
you can keep using the app while things download.

1. **The AI.** One button installs Ollama and downloads the model in the background (or add
   free online AI keys instead). An AI assistant you already use (Claude, Gemini, ChatGPT) can
   write your AI filter with you, and rewrite your resume to be ATS-friendly from what
   JobFinder's ATS check found: copy the request, attach your resume there, paste the answer
   back.
2. **Searches and your browser.** Add a search: copy the link of a job board's results page into
   Searches, or let an AI assistant suggest search pages for your skills and paste its answer
   back to add them all. Then sign in to the job sites: JobFinder opens them in Chrome or Edge;
   sign in, then close that browser. Already signed in to them in your own browser? With the
   extension, copy those sign-ins instead. The browser extension is optional; the setup opens its
   folder (in Chrome or Edge, open Extensions, turn on Developer mode, choose Load unpacked and
   pick that folder).
3. **Your phone (optional).** One button sets your Wi-Fi to Private and opens the firewall to your
   own network only (Windows asks once), then pair your phone.

Then open the Android app on your phone, tap your computer in the list (or scan the code in the
desktop app's Settings) and click Allow on the computer.

Help in the app (the question mark) has step-by-step guides and answers to common questions.

## What you can customise

- **Searches:** any number, on any board; name each one; edit, pause by deleting, or check one now.
  Search, sort, group by board and filter the list.
- **How often to search:** from every hour to once a day, and whether to keep the computer awake.
- **AI filters:** your own description of jobs to skip, and a list of companies to skip (or let
  an AI assistant write them with you during setup).
- **AI instructions:** the text the app gives the AI for each kind of work (filtering, ATS review,
  tips, form answers), with a reset to the default.
- **AI model:** which local model to use, more local models to download, and free online keys
  with the order they are used in and which work each may do.
- **Resume:** the file used for ATS reviews, tips and form answers.
- **Job details:** which sections show in a job (ATS review, company, ghost-job check, tips) and
  in what order.
- **Jobs list:** sort order, filters (board, search, label, work mode) and labels on each job; these
  are kept between app starts.
- **Clean-up:** how many days to keep filtered-out jobs, or keep them forever.
- **Notifications:** on or off, and the sound.
- **Look:** light, dark or system theme.
- **In-app browser:** open jobs in the app or in your default browser.
- **Phone:** pair more phones, reset the pairing code, and the port the phone connects on.
- **Backups:** export everything, locked with a password or with this device's own key, import
  (replace everything or add what is missing), and a CSV of your jobs.
- **Form answers:** see, edit, search and delete the answers the extension keeps; choose the sites
  it works on.

## Screenshots

### Desktop

| | Light | Dark |
|---|---|---|
| Jobs | ![](docs/screenshots/desktop/jobs-light.png) | ![](docs/screenshots/desktop/jobs-dark.png) |
| Job details and ATS review | ![](docs/screenshots/desktop/job-light.png) | ![](docs/screenshots/desktop/job-dark.png) |
| Filter jobs | ![](docs/screenshots/desktop/filters-light.png) | ![](docs/screenshots/desktop/filters-dark.png) |
| Searches | ![](docs/screenshots/desktop/searches-light.png) | ![](docs/screenshots/desktop/searches-dark.png) |
| AI filters | ![](docs/screenshots/desktop/ai-filters-light.png) | ![](docs/screenshots/desktop/ai-filters-dark.png) |
| Settings | ![](docs/screenshots/desktop/settings-light.png) | ![](docs/screenshots/desktop/settings-dark.png) |
| Form answers | ![](docs/screenshots/desktop/form-answers-light.png) | ![](docs/screenshots/desktop/form-answers-dark.png) |
| Setup | ![](docs/screenshots/desktop/setup-light.png) | ![](docs/screenshots/desktop/setup-dark.png) |
| Allow a phone | ![](docs/screenshots/desktop/pairing-light.png) | ![](docs/screenshots/desktop/pairing-dark.png) |

### Phone

| Jobs | Job details | Searches | AI filters | Settings | Pairing |
|---|---|---|---|---|---|
| ![](docs/screenshots/phone/jobs-light.png) | ![](docs/screenshots/phone/job-light.png) | ![](docs/screenshots/phone/searches-light.png) | ![](docs/screenshots/phone/ai-filters-light.png) | ![](docs/screenshots/phone/settings-light.png) | ![](docs/screenshots/phone/pairing-light.png) |
| ![](docs/screenshots/phone/jobs-dark.png) | ![](docs/screenshots/phone/job-dark.png) | ![](docs/screenshots/phone/searches-dark.png) | ![](docs/screenshots/phone/ai-filters-dark.png) | ![](docs/screenshots/phone/settings-dark.png) | ![](docs/screenshots/phone/pairing-dark.png) |

## Build from source

You need JDK 21, Android SDK 36 (for the phone app) and Node.js 20 (for the extension).

```bash
./gradlew :desktopCMP:run                 # run the desktop app
./gradlew :desktopCMP:packageMsi          # Windows installer, in desktopCMP/build/compose/binaries
./gradlew :desktopCMP:packageDeb          # Linux .deb (on Linux), in the same folder
./gradlew :androidCMP:assembleDebug       # phone app, in androidCMP/build/outputs/apk
cd browser-extension && npm install && npm run build   # extension, in browser-extension/dist
```

The code is Kotlin with Compose Multiplatform. One shared UI serves both apps:

| Module | What it is |
|---|---|
| `composeApp` | The screens and view models, shared by desktop and Android |
| `shared` | Models and the API between the phone and the computer |
| `server` | The engine in the desktop app: Ktor server, Room database, scanning, AI |
| `desktopCMP` | The Windows app and installer |
| `androidCMP` | The Android app |
| `browser-extension` | JobFinder Helper for Chrome and Edge (TypeScript) |

Before pushing, run `scripts/ci-local.sh` (formatting and a compile). Contributor notes are in
[AGENTS.md](AGENTS.md).

Linux and macOS desktop apps and an iOS app are planned; the code is shared so that they need only
small platform parts.

## Credits

JobFinder started as a rewrite of [First 2 Apply](https://github.com/beastx-ro/first2apply)
([first2apply.com](https://first2apply.com)) by BeastX, an open-source job board aggregator
(MIT licence). Its idea, its job board list and its page parsing were the starting point. Thank
you to its authors.
