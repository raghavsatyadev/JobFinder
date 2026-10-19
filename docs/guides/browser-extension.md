# Browser extension

**JobFinder Helper** is an optional extension for Chrome and Edge that comes with the desktop app.

- Shows the ATS score on the toolbar when the job you are looking at is in JobFinder.
- Keeps your answers to application forms and fills them in again when you press Autofill, or
  drafts new ones with the AI for you to check. It never sends a form, and never keeps passwords,
  card or bank details, or ID numbers.
- Reads pages that block the app's own requests, in a background tab.
- Copies your job board sign-ins to the app when you ask, so you sign in only once (job board
  cookies only).

See and edit the answers it keeps in **Settings → Form answers**, and choose the sites it works on.

## Install

1. In the app's setup, press **Open the extension folder**.
2. In Chrome open `chrome://extensions` (in Edge, `edge://extensions`).
3. Turn on **Developer mode**, choose **Load unpacked** and pick that folder.

The extension talks only to the desktop app on the same computer, through the browser's native
messaging. It opens no ports and sends page data nowhere else. More in
[browser-extension/README.md](../../browser-extension/README.md).
