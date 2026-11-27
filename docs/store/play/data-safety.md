# JobFinder Android: Data safety answers

Play counts data as "collected" when it leaves the phone for the developer or a third party. The
app sends job data only to the user's own computer on the home network (no developer server, no
account), so that traffic is not collection. The one thing that is: Google's code scanner (ML Kit,
`play-services-code-scanner`), used to scan the pairing code, sends diagnostics to Google.
Source: https://developers.google.com/ml-kit/android-data-disclosure (checked 2026-11-27; it lists
data for the latest SDK only, so check again when the library is updated).

## Overview questions

- Does your app collect or share any of the required user data types? **Yes** (ML Kit diagnostics).
- Is all of the user data collected by your app encrypted in transit? **Yes** (ML Kit uses HTTPS).
- Do you provide a way for users to request that their data is deleted? **No** (the developer holds
  no user data; ML Kit's diagnostics are held by Google).

## Data types

| Category | Type | Collected | Shared | Processed ephemerally | Required | Purpose |
| --- | --- | --- | --- | --- | --- | --- |
| App info and performance | Diagnostics | Yes | No | No | Required | Analytics |
| App info and performance | Other app performance data | Yes | No | No | Required | Analytics |
| Device or other IDs | Device or other IDs | Yes | No | No | Required | Analytics |

Everything else: **not collected** (location, personal info, financial info, health, messages,
photos and videos, audio, files and docs, calendar, contacts, app activity, web browsing).

Notes:

- Camera frames are not collected: the scanner runs inside Google Play services and returns only
  the scanned text; the app has no camera permission.
- "Required" is the cautious answer: pairing by picking the computer from the list does not use the
  scanner, but users cannot turn ML Kit's diagnostics off.
- Sample mode sends nothing anywhere.

## Permissions (for reference)

`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK` (talk to the user's computer on the
LAN), `POST_NOTIFICATIONS` (new-job alerts). No sensitive or restricted permissions, so no
permission declaration forms.
