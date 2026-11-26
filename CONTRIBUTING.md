# Building and contributing

You need JDK 21, Android SDK 36 (phone app) and Node.js 20 (extension).

```bash
./gradlew :desktopCMP:run                 # run the desktop app
./gradlew :desktopCMP:packageMsi          # Windows installer, in desktopCMP/build/compose/binaries
./gradlew :desktopCMP:packageDeb          # Linux .deb (on Linux), in the same folder
./gradlew :androidCMP:assembleDebug       # phone app, in androidCMP/build/outputs/apk
cd browser-extension && npm install && npm run build   # extension, in browser-extension/dist
```

| Module | What it is |
|---|---|
| `composeApp` | Screens and view models, shared by desktop and Android |
| `shared` | Models and the API between the phone and the computer |
| `server` | The engine in the desktop app: Ktor server, Room database, scanning, AI |
| `desktopCMP` | The Windows app and installer |
| `androidCMP` | The Android app |
| `browser-extension` | JobFinder Helper for Chrome and Edge (TypeScript) |

The videos and screenshots are made off screen on sample data:
`./gradlew :desktopCMP:demoTour -PtourScript=docs/video/scripts/desktop.txt -PtourOut=<folder>`
(needs `ffmpeg`; the script format is in `DemoTour.kt`).

Run `scripts/ci-local.sh` (formatting and a compile) before pushing. Agent and contributor rules
are in [AGENTS.md](AGENTS.md).
