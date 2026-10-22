# Shared code first

The computer app runs on Windows, macOS and Linux; the phone app on Android and iOS. So:

- **All UI** lives in `:composeApp` `commonMain`: screens, dialogs, components, ViewModels,
  strings. `desktopCMP`, `androidCMP` and `iosCMP` keep only what cannot be shared: the app entry point,
  the window frame and title bar, the embedded CEF browser, the tray, Windows APIs (DPAPI, sleep
  blocker), Android activities, iOS UIKit sheets, notifications, Bonjour/NSD discovery and the QR scanner. Phone
  wiring is shared in `clientModule()`; a phone platform only implements `ClientPlatform`.
- **Business logic** lives in `:shared` / `:composeApp` `commonMain` (clients) or `:server`
  (computer, plain JVM so Linux and macOS reuse it). A platform module only implements an
  interface the shared code defines, and binds it in Koin.
- Before adding a file to `desktopCMP` or `androidCMP`, check it could not go in common code. If a
  Windows-only or Android-only API is needed, put an interface in common code and a small
  implementation in the platform module.
