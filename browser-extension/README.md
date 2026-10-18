# JobFinder Helper (browser extension)

A Manifest V3 extension for Chrome and Edge that connects the browser to the JobFinder desktop app
on the same PC. It does two things:

- **Job badge.** On job sites the user turns on, it asks the app whether the open page is a saved job
  and shows the ATS score on the toolbar icon. The popup shows the job.
- **Application forms.** On sites the user turns on, it saves what the user types into a job
  application form when the form is sent (never passwords, card or bank details, or ID numbers).
  **Autofill** (in the popup, or the small button under a field in focus) fills empty fields from
  those answers; **AI help** drafts an answer from the resume, the job and earlier answers for the
  user to edit before it goes in. Neither ever sends a form. The app lists the answers under
  Settings → Form answers.
- **Fallback reader.** Some sites (Glassdoor, LinkedIn pages, AmbitionBox) block the app's own
  requests. The app can ask the extension to open such a page in a background tab, read its text,
  JSON-LD and Next.js data, and close the tab.

It talks to the app only through Chrome native messaging, via the native host
`io.github.raghavsatyadev.jobfinder` that the JobFinder MSI installs. It opens no ports and sends
page data nowhere else. The message format is in [PROTOCOL.md](PROTOCOL.md).

## Build

Needs Node.js 20 or newer.

```sh
npm install
npm run build       # bundles into dist/
npm run watch       # rebuilds on change
npm run typecheck
npm run test:e2e    # builds dist-e2e/, then runs the Playwright checks (popup, autofill, saving, AI help)
```

The first `test:e2e` on a machine needs the browser: `npx playwright install chromium`.

`test:e2e` builds a separate copy into `dist-e2e/`, with host permission for `127.0.0.1` and a fake
app that answers the form messages from `chrome.storage.local` (`src/background/e2eFakeHost.ts`).
Never load `dist-e2e/` as your real extension; `npm run build` leaves the fake out of `dist/`.

## Load unpacked

- **Chrome:** open `chrome://extensions`, turn on Developer mode, click **Load unpacked**, pick `dist/`.
- **Edge:** open `edge://extensions`, turn on Developer mode, click **Load unpacked**, pick `dist/`.

After a rebuild, click the reload icon on the extension card.

## Development extension id

`static/manifest.json` has a `key` field (a public key) so every unpacked copy gets the same id:

```
cnleajglbkmfgfkfihheoacmojpklpop
```

The native host manifest allows this id. The matching private key is `dev-key.pem`, kept out of git
by `.gitignore`. It is only needed to pack a `.crx`; loading unpacked needs just the public key.

To make a new key pair (for example on a fresh clone where you want your own id):

```sh
npm run dev-key   # creates dev-key.pem if missing, prints the manifest key and the id
```

Put the printed key in `static/manifest.json` and add the printed id to the host manifest. The id is
the first 32 hex digits of the SHA-256 of the public key (DER), with digits 0–f mapped to a–p.

The Chrome Web Store and Edge Add-ons assign their own ids and ignore `key`. Remove `key` before
uploading, and add the store ids to `allowed_origins`.

## Native host registration (done by the MSI)

The host manifest is a JSON file next to the host executable, for example
`%LOCALAPPDATA%\JobFinder\native-host\io.github.raghavsatyadev.jobfinder.json`:

```json
{
  "name": "io.github.raghavsatyadev.jobfinder",
  "description": "JobFinder native messaging host",
  "path": "jobfinder-native-host.exe",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://cnleajglbkmfgfkfihheoacmojpklpop/"]
}
```

- `name` must equal the host name exactly (lowercase, dots).
- `path` is absolute, or relative to the manifest's folder. On Windows it should be an `.exe`
  (a jpackage launcher works) or a `.bat`.
- `allowed_origins` lists every extension id allowed to connect, each as
  `chrome-extension://<id>/` with the trailing slash. Edge uses the same scheme. Wildcards are not
  allowed.

Register the manifest for each browser with a registry key whose default value is the full path
of the JSON file:

```
HKCU\Software\Google\Chrome\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder
HKCU\Software\Microsoft\Edge\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder
```

For example:

```bat
reg add "HKCU\Software\Google\Chrome\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder" /ve /t REG_SZ /d "%LOCALAPPDATA%\JobFinder\native-host\io.github.raghavsatyadev.jobfinder.json" /f
reg add "HKCU\Software\Microsoft\Edge\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder" /ve /t REG_SZ /d "%LOCALAPPDATA%\JobFinder\native-host\io.github.raghavsatyadev.jobfinder.json" /f
```

The browser reads the registry when the extension connects, so no browser restart is needed after
install. If the popup says "JobFinder helper is not installed", the key or the manifest path is
wrong. If it says "JobFinder blocked this extension", the id is missing from `allowed_origins`.

## Permissions

| Permission | Why |
| --- | --- |
| `nativeMessaging` | Talk to the JobFinder app. |
| `storage` | Settings and the connection state shown in the popup. |
| `alarms` | Retry the connection when the service worker has been asleep. |
| `scripting` | Read the page text in fallback reader tabs; register the form content script for the sites that are on; collect and fill fields on Autofill. |
| `activeTab` | Let the popup see which site is open, to offer its switch. |
| `optional_host_permissions: <all_urls>` | Asked per site when the user turns a site on, or once for all sites when the user turns on the fallback reader. Nothing is granted at install. |

No content scripts are declared in the manifest. The job badge needs only the tab's URL, which the
browser reports only for sites the user turned on. The form content script is registered at run
time for exactly the sites that are on, so it never runs anywhere else.

### How the fallback reader gets permission

A `readPage` needs host permission for the page's origin. The options page has a "Let JobFinder
read pages through this browser" switch, with an explanation, that asks for `<all_urls>` once,
because the app decides which pages to read. Without it, `readPage` works only on sites the user
turned on and replies `error` with code `no_permission` everywhere else. The JobFinder app should
then tell the user to turn on the fallback reader.

## Layout

| Path | What |
| --- | --- |
| `src/background/` | Service worker: native port (`nativeLink.ts`), fallback reader (`pageReader.ts`, `pageScripts.ts`), job badge (`jobLookup.ts`). |
| `src/background/forms.ts`, `hostRequests.ts` | Form content script registration, autofill, saving answers, AI help drafts; request/reply matching. |
| `src/content/` | Content script: field discovery and filling (`fields.ts`), the in-page buttons (`chip.ts`), form send detection (`content.ts`). |
| `src/popup/`, `src/options/` | Popup and options page scripts. |
| `src/shared/` | Protocol types, constants (host name, limits), settings, site switches, UI text. |
| `static/` | Manifest, HTML and CSS, copied into `dist/`. |
| `tests/popup.spec.ts` | Playwright check: the popup explains that the app cannot be reached. |
| `tests/forms.spec.ts`, `tests/fixtures/apply.html` | Playwright checks on a local form: autofill (React input, select, radio, checkbox, same-origin iframe, file and sensitive fields), saving on send, AI help. |
| `scripts/dev-key.mjs` | Makes the dev key pair and prints the extension id. |
