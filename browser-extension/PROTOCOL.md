# JobFinder native messaging protocol, version 1

The JobFinder Helper extension (Chrome and Edge) talks to the JobFinder native host
`io.github.raghavsatyadev.jobfinder` through Chrome native messaging. There are no network ports.
The browser starts the host process when the extension connects and stops it when the port closes.
The types in `src/shared/protocol.ts` mirror this document.

## Transport

- The browser starts the host from the path in its native host manifest, with stdin and stdout as pipes.
  Arguments: the caller origin (`chrome-extension://<extension-id>/`) and, on Windows,
  `--parent-window=<handle>`. The host may reject origins it does not expect, but the browser
  already enforces `allowed_origins`.
- Each message is a 32-bit unsigned length in native byte order (little-endian on Windows),
  followed by that many bytes of UTF-8 JSON. Both directions use this framing.
- stdout carries messages only. Logs go to stderr or a file; a stray byte on stdout breaks the
  connection. A JVM host must not print banners or warnings to stdout.
- When stdin reaches end-of-file the extension has closed the port (extension turned off, browser
  closed, reconnect). The host must exit then.
- The extension keeps one port open while "Connect to the JobFinder app" is on. That open port also
  keeps the extension's service worker alive.

## Size limits

| Direction | Hard limit (browser) | What the protocol uses |
| --- | --- | --- |
| host → extension | 1 MB per message; a larger message closes the port | Requests are small. `url` ≤ 8,192 chars, `id` ≤ 128 chars, `waitForSelector` ≤ 512 chars. Keep each message under 64 KB. |
| extension → host | 64 MB per message | `pageRead` is the only large message. Its fields are capped as listed under `pageRead`; the worst case is about 8 million UTF-16 characters (≤ 24 MB of UTF-8). |

## Common fields

Every message is a JSON object with:

| Field | Type | Notes |
| --- | --- | --- |
| `v` | number | Always `1`. |
| `type` | string | Message type, below. |
| `id` | string | Required on requests and on their replies. The reply carries the request's `id`. Extension ids start with `e-` followed by a UUID; host ids are any string up to 128 chars. |

Unknown extra fields must be ignored by both sides, so fields can be added without a version bump.

## Messages

| Type | Direction | Kind |
| --- | --- | --- |
| `hello` | extension → host | request |
| `hello` | host → extension | reply |
| `status` | host → extension | event (no `id`) |
| `readPage` | host → extension | request |
| `pageRead` | extension → host | reply to `readPage` |
| `jobForUrl` | extension → host | request |
| `job` | host → extension | reply to `jobForUrl` |
| `saveAnswers` | extension → host | request |
| `answersSaved` | host → extension | reply to `saveAnswers` |
| `answersFor` | extension → host | request |
| `answers` | host → extension | reply to `answersFor` |
| `draftAnswer` | extension → host | request |
| `draft` | host → extension | reply to `draftAnswer` |
| `error` | both | reply to any request, or event without `id` |

### `hello` (extension → host)

Sent right after the port opens.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `extensionVersion` | string | For example `"0.1.0"`. |
| `browser` | `"chrome"` \| `"edge"` | |

```json
{ "v": 1, "type": "hello", "id": "e-6f1c…", "extensionVersion": "0.1.0", "browser": "edge" }
```

### `hello` (host → extension)

Reply to `hello`. The extension shows "Connected" only after this arrives.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | The request's `id`. |
| `appVersion` | string | JobFinder app version. |
| `appRunning` | boolean, optional | Default `true`. Send `false` when the host started but cannot reach the JobFinder app. |

```json
{ "v": 1, "type": "hello", "id": "e-6f1c…", "appVersion": "0.0.25", "appRunning": true }
```

### `status` (host → extension)

Sent whenever the host gains or loses the app (the app starts or quits). No `id`, no reply.

| Field | Type | Notes |
| --- | --- | --- |
| `appRunning` | boolean | |
| `appVersion` | string, optional | |

When the app is not running, the host should stay connected and send `status` with
`appRunning: false`, then `appRunning: true` once the app is back. Exiting the host instead also
works (the extension shows "app is not running" and retries with backoff), but it reacts slower.

### `readPage` (host → extension)

Asks the extension to read a page through the browser (the fallback reader).

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Up to 128 chars. |
| `url` | string | Absolute `http` or `https` URL, up to 8,192 chars. |
| `waitForSelector` | string, optional | CSS selector, up to 512 chars. After load, the extension waits until it matches. |
| `timeoutMs` | number, optional | Default 30,000. Clamped to 1,000–120,000. Counts from when the read starts, not from when it was queued. |
| `includeHtml` | boolean, optional | Default `false`. Adds `html` to the reply. |

What the extension does:

1. Checks it has host permission for the URL's origin. If not, replies `error` `no_permission`.
2. Opens the URL in a new inactive tab in the user's current window.
3. Waits for the tab to finish loading, then 1.5 seconds more for page scripts to render.
4. If `waitForSelector` is set, checks for it every 250 ms.
5. Extracts the page, closes the tab, replies `pageRead`. The tab is closed on errors too.

Reads run one at a time, in arrival order. At most 20 reads can wait; more get `error` `busy`.

```json
{ "v": 1, "type": "readPage", "id": "r-42", "url": "https://www.glassdoor.co.in/job-listing/…", "waitForSelector": "[data-test=jobDescriptionContent]", "timeoutMs": 45000 }
```

### `pageRead` (extension → host)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | The request's `id`. |
| `finalUrl` | string | `location.href` after redirects. |
| `title` | string | `document.title`. |
| `text` | string | `document.body.innerText`, cut at 1,000,000 chars. |
| `jsonLd` | string[] | Raw contents of every `<script type="application/ld+json">`, trimmed, empty ones skipped. Blocks are kept whole until 1,000,000 chars in total; a block that does not fit is dropped, never cut. |
| `nextData` | string \| null | Raw content of `#__NEXT_DATA__`, or `null` if absent. Dropped (set to `null`) when over 4,000,000 chars, never cut. |
| `html` | string, optional | Only when `includeHtml` was `true`. `document.documentElement.outerHTML`, cut at 2,000,000 chars. |
| `truncated` | string[] | Names of fields that were cut or dropped: any of `"text"`, `"html"`, `"jsonLd"`, `"nextData"`. Empty when complete. |
| `elapsedMs` | number | Time from the start of the read to the reply. |

The JSON strings are passed through unparsed. The host parses them and must expect invalid JSON
from badly built sites.

### `getCookies` (host → extension)

Asks for the cookies of job sites, so the app can copy the user's sign-ins into its own browser.
Sent only when the user chooses "Copy sign-ins from my browser" in the app.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `sites` | string[] | 1–200 host names such as `linkedin.com`; subdomains are included. |

The extension answers `error` `no_permission` when sign-in sharing is off (the optional `cookies`
permission is not granted).

### `cookies` (extension → host)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | The request's `id`. |
| `cookies` | object[] | `name`, `value`, `domain` (leading dot for a domain cookie, bare host for a host-only one), `path`, `secure`, `httpOnly`, `sameSite` (`Strict`, `Lax` or `None`, optional), `expires` (seconds since the epoch, absent for a session cookie), `session`: the DevTools `Network.Cookie` shape. |
| `missing` | string[] | Asked-for sites the extension holds no host permission for; no cookies are read for them. |

### `jobForUrl` (extension → host)

Sent when a tab in an enabled site navigates to a new URL (including in-page navigation on
single-page sites). Only URLs of sites the user turned on are sent. The extension ignores tabs it
opened for `readPage`.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `url` | string | The tab's full URL. |

### `job` (host → extension)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | The request's `id`. |
| `found` | boolean | Whether this URL is a job in the app's database. |
| `jobId` | string, optional | App's job id as a string. |
| `title` | string, optional | |
| `company` | string, optional | |
| `atsScore` | number, optional | 0–100. Omit when the job is not scored yet. |

When `found` is `true`, the toolbar icon shows the rounded score as a badge (or a check mark
without a score) and the popup shows the job. The host decides how URLs match (canonical job URL,
tracking parameters stripped, and so on). Replies to an older request for the same tab are ignored.

### Form fields

The form messages describe each field with a `FieldInfo` object. Strings are trimmed, with runs of
white space made one space, and cut at 2,000 characters (`value` at 10,000).

| Field | Type | Notes |
| --- | --- | --- |
| `ref` | string, optional | Names the field in `answersFor`; unique within the request, up to 128 chars. |
| `label` | string, optional | The `<label>`, `aria-labelledby` text, a radio group's `<legend>`, or the text just before the field. |
| `name` | string, optional | The `name` attribute. |
| `elementId` | string, optional | The `id` attribute. |
| `placeholder` | string, optional | |
| `ariaLabel` | string, optional | The `aria-label` attribute. |
| `inputType` | string | The input `type` (`text`, `email`, `tel`, `number`, `date`, `checkbox`, `file`, …), or `select`, `textarea`, `radio` (one field per radio group). |
| `autocomplete` | string, optional | The `autocomplete` attribute. |
| `options` | string[], optional | The visible choices of a `select` or radio group, at most 200 of up to 300 chars. |
| `value` | string, optional | Only in `saveAnswers`: the chosen option's text for a select or radio group, `Yes`/`No` for a checkbox. |

**Never sent:** password fields, hidden inputs, file contents, and any field the extension finds
sensitive: `autocomplete` `cc-*`, `current-password`, `new-password` or `one-time-code`; label,
name, id or placeholder words for passwords, card numbers, CVV, expiry, IBAN, SWIFT, IFSC, routing
or account numbers, bank, SSN, Aadhaar, PAN, passport, national ID, tax ID, driving licence, OTP and
PIN (but not "PIN code", the Indian postal code); or a value that looks like a card number (13–19
digits passing the Luhn check). The host runs the same check again and drops what it finds.

### `saveAnswers` (extension → host)

Sent when the user sends a form (a `submit` event, or a click on a submit button or on a button that
says submit, apply, send, next, continue, finish or review) in a frame of a site the user turned
on, while "Save my answers" is on in the options. Empty fields and file fields are left out.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `url` | string | The tab's URL (the job page), up to 8,192 chars. The host links the answers to the job `jobForUrl` would find. |
| `fields` | FieldInfo[] | At most 300, each with `value`. |

The host stores each answer under a question key: `first_name`, `expected_ctc` and so on when a
synonym names the question, else the cleaned question text. An answer to the same question on the
same job (or on the same page, without a job) is updated, not added again.

### `answersSaved` (host → extension)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `saved` | number | Answers stored or updated. |
| `jobId` | string, optional | The job they were linked to. |

### `answersFor` (extension → host)

Sent only when the user presses Autofill (in the popup or under a field). The extension collects
the fields of every frame of the tab it can reach (same-origin iframes through their parent),
without values.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `url` | string | The tab's URL. |
| `fields` | FieldInfo[] | At most 300, each with a `ref`. File fields are not sent. |
| `ai` | boolean, optional | Default `false`. Lets the host ask the AI which saved question an unclear label asks. |

The host matches each field by its question key (synonyms first), then by questions worded alike
(fuzzy), and only then, with `ai`, by asking the AI (one call for all unclear fields, given up
after 60 seconds). Answers from the same site win over others; newer win over older.

### `answers` (host → extension)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `answers` | object[] | One per field that has a value: `ref`, `value`, `questionKey`, `match` (`exact`, `synonym`, `fuzzy` or `ai`) and `score` (0–1). Values total at most 400,000 chars. |
| `jobId` | string, optional | |
| `resumeFileName` | string, optional | The resume's file name in the app, so the extension can say which file to attach. |

The extension fills only fields that are still empty (a radio group with nothing checked, a select
on its first choice, a checkbox it may tick but never untick), the way typing does: the prototype's
`value` setter, then `input` and `change` events, so React and similar frameworks see the change.
Radios and checkboxes are clicked. Selects and radio groups take the choice whose text or value
matches. File fields are never set; the extension tells the user to attach the resume. Nothing is
ever submitted.

### `draftAnswer` (extension → host)

Sent when the user presses "AI help" under a field. The draft appears in an editable box and goes
into the field only when the user presses Insert.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `url` | string | The tab's URL; the host uses its job. |
| `field` | FieldInfo | Without `value` or `ref`. |
| `current` | string, optional | What the field holds now, up to 10,000 chars. |

The host writes the draft with the AI's `answers` task from the resume, the job and up to six
similar earlier answers. For a select or radio group the draft is one of `options`. The local model
may first have to load, so the extension waits up to 5 minutes.

### `draft` (host → extension)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | |
| `text` | string | The draft, up to 10,000 chars. |

When no draft can be written (no AI set up, the model failed or gave no usable answer) the host
replies `error` with code `ai_failed`.

### `error` (both directions)

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string, optional | The failed request's `id`. Absent when the bad message had no usable `id`. |
| `code` | string | One of the codes below. |
| `message` | string | English, for logs. Not shown to the user as is. |

A side never answers an `error` with another `error`. The host may answer `jobForUrl` with an
`error`; the extension then shows nothing for that tab.

| Code | Sent by | Meaning |
| --- | --- | --- |
| `bad_request` | both | Not a JSON object, no string `type`, or a field is missing, of the wrong type, or too long. |
| `unsupported_version` | both | `v` is not `1`. |
| `unknown_type` | both | `type` is not known to the receiver. |
| `no_permission` | extension | No host permission for the URL's origin, or the page redirected to a site without permission (for example a login page on another domain). |
| `timeout` | extension | The page did not load, or `waitForSelector` did not match, within `timeoutMs`. |
| `tab_failed` | extension | The tab could not be opened, or was closed by the user before it loaded. |
| `extract_failed` | extension | The page could not be read (for example a browser error page). |
| `busy` | extension | 20 reads are already waiting. Retry later. |
| `ai_failed` | host | The AI could not write a `draft`. |
| `internal` | both | Anything else. |

## Connection states shown to the user

| State | Cause |
| --- | --- |
| Connecting | Port opened, no `hello` reply yet. |
| Connected | `hello` reply with `appRunning` not `false`, or `status` with `appRunning: true`. |
| App is not running | `hello`/`status` with `appRunning: false`, or the host exited ("Native host has exited."). |
| Helper is not installed | "Specified native messaging host not found." or "Failed to start native messaging host." |
| Blocked this extension | "Access to the specified native messaging host is forbidden." (the extension id is not in `allowed_origins`). |
| Turned off | The user turned the connection off in the options. |

After a disconnect the extension retries after 1 s, doubling up to 60 s, and also checks once a
minute through an alarm. A `hello` reply resets the backoff.

## Permissions and privacy

- The extension has no host permissions at install. Each site is turned on separately from the
  popup or options page, and the browser asks the user for that site (`*://site/*` and
  `*://*.site/*`).
- `readPage` needs permission for the target origin. In practice the user turns on the fallback
  reader in the options page once, which grants `<all_urls>` after an explanation. Without it,
  `readPage` works only for sites the user turned on, and replies `no_permission` for the rest.
- Pages read by `readPage` open with the user's normal browser session (cookies, logins).
- `getCookies` needs the optional `cookies` permission, which the "Share my job board sign-ins"
  switch in the options requests together with the known job boards' origins. Cookies are read
  only for the sites in the request that the extension holds a host permission for.
- Page data goes only to the native host. The extension makes no network requests of its own.
- On sites that are on, a content script (registered with `chrome.scripting.registerContentScripts`
  for exactly those sites, in all frames) saves form answers on send and shows the Autofill and AI
  help buttons. Both can be turned off in the options. Saved answers stay in the app on the PC and
  never go to the phone.
