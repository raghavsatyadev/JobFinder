# Shared code first

The user plans Linux, macOS and iOS apps (2026-10-17). So:

- **All UI** lives in `:composeApp` `commonMain`: screens, dialogs, components, ViewModels,
  strings. `desktopCMP` and `androidCMP` keep only what cannot be shared: the app entry point,
  the window frame and title bar, the embedded CEF browser, the tray, Windows APIs (DPAPI, sleep
  blocker), Android activities, notifications and the QR scanner.
- **Business logic** lives in `:shared` / `:composeApp` `commonMain` (clients) or `:server`
  (computer, plain JVM so Linux and macOS reuse it). A platform module only implements an
  interface the shared code defines, and binds it in Koin.
- Before adding a file to `desktopCMP` or `androidCMP`, check it could not go in common code. If a
  Windows-only or Android-only API is needed, put an interface in common code and a small
  implementation in the platform module.
