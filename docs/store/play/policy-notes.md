# JobFinder Android: Play policy and release notes

Checked 2026-11-27.

## Generative AI content (answer/13985936, overview answer/14094294)

The phone app does not run or call any AI model. It shows ATS reviews and filter reasons that the
user's own desktop app wrote, for the user's own resume. Play's AI-generated content policy does not
cover apps that "merely host AI-generated content and are unable to create content using AI", so the
in-app AI reporting feature is not required. If a reviewer asks: the phone app has no prompt box and
starts no AI task; AI work happens only in the desktop app, which the user runs themselves.

Revisit this if the phone app ever gets a button that starts an AI task (for example "Run ATS
review" or "Improve my resume"). Then it needs an in-app "Report this result" action that reaches
the developer without leaving the app.

## Release format

- Upload an Android App Bundle: `scripts/gradle-agent.sh :androidCMP:bundleRelease`, not the APK.
- Enrol in Play App Signing; the release key in the build becomes the upload key.
- `targetSdk` 37 meets Play's target API requirement.

## Build toolchain

The app builds with AGP `9.6.0-alpha03` because compileSdk 37.1 (needed by the Compose 1.13
alphas) is not supported by a stable AGP yet; the newest stable is 9.5.3 (Google Maven, 2026-11-27).
Play does not care which AGP built a bundle, so this does not block publishing. Move to the first
stable 9.6 when it ships.

## New personal developer account: closed test first

Personal Play developer accounts made after 13 Nov 2023 must run a closed test with at least 12
testers who stay opted in for 14 days in a row before production access can be requested. Start it
as early as possible; it is the longest wait in the release.

1. Create the closed-test track, add a Google Group or email list of 12+ testers.
2. Upload the bundle, share the opt-in link, and keep 12+ testers opted in for 14 days.
3. Apply for production access in Play Console (it asks about the test and the testers' feedback).

## Store screenshots

Take them in sample mode so no real jobs show. The release build opens in sample mode only from the
pairing screen of an unpaired phone ("Try with sample jobs").
