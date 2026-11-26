# JobFinder: restricted capabilities for Store certification

The package (`desktopCMP/msix/AppxManifest.xml`) declares two restricted capabilities. After the
`.msix` is uploaded, Partner Center asks why each is needed (Submission options → Restricted
capabilities). Paste the text below; it also fits in "Notes for certification".

## runFullTrust

Paste:

> JobFinder is a desktop app (Kotlin/JVM with Compose for Desktop) packaged with the Desktop
> Bridge. runFullTrust is needed for its Windows.FullTrustApplication entry point: it runs a Java
> runtime, an embedded Chromium browser for reading job board pages with the user's own sign-ins,
> and a local web server that its own Android app and browser extension connect to.

Low risk: runFullTrust is the standard capability for Win32 apps in MSIX and is approved routinely.

## unvirtualizedResources

Paste:

> JobFinder turns off file and registry write virtualization (desktop6:FileSystemWriteVirtualization
> and RegistryWriteVirtualization set to disabled) for two reasons:
> 1. Existing users installed JobFinder with our MSI and keep their database, encrypted keys and
> backups in %LOCALAPPDATA%\jobfinder-data. The Store version must open that same folder so a user
> who moves to the Store version keeps their saved jobs, resume and settings, and can go back to
> the MSI without losing data.
> 2. Our companion browser extension (JobFinder Helper for Chrome and Edge) talks to the app through
> browser native messaging. Chrome and Edge find the host through the per-user registry keys
> HKCU\Software\Google\Chrome\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder and
> HKCU\Software\Microsoft\Edge\NativeMessagingHosts\io.github.raghavsatyadev.jobfinder, and a manifest file in the data folder.
> With virtualization on, these writes go to the package's private hive and folder, which the
> browsers cannot see, so the extension cannot connect. The manifest points the browser at the
> package's app execution alias (jobfinder.exe).
> The app writes only these keys and its own data folder; it does not change system settings,
> other apps' data or HKLM. Uninstalling leaves the user's data folder in place so it is not lost.

The host name matches `HOST_NAME` in `server/.../nativehost/NativeMessages.kt`.

## If certification rejects unvirtualizedResources

Microsoft documents this capability as mainly for games, so a rejection is possible. Fallback,
in order:

1. **Reply first.** Partner Center lets you answer the certification report. Restate the native
   messaging reason; reviewers sometimes approve on a clearer explanation.
2. **Ship virtualized** (remove `unvirtualizedResources` and the two `desktop6` lines, bump the
   version, resubmit). Then:
   - **Data.** Files written to `%LOCALAPPDATA%\jobfinder-data` land in the package's private
     `LocalCache\Local\jobfinder-data`. Reads fall back to the real folder, but writes are
     copy-on-write, so the MSI and the Store app would drift apart. Add a one-time migration on
     first run: if the real folder exists and the private copy does not, copy it (database,
     protected keys, backups) and mark it done. DPAPI-protected keys stay readable because the
     user account is the same. Tell users the MSI and the Store app no longer share data.
   - **Browser extension: breaks as is.** `NativeHostRegistration.kt` writes the HKCU keys by
     running `reg.exe`, which inherits the package identity, so the keys land in the private hive
     and Chrome/Edge never see them. Options, none tested yet:
     a. Write a `.reg` file (and the host manifest) to a real, non-virtualized location such as the
        user's Downloads or Documents folder, and open it through the shell (ShellExecute via
        explorer), so Registry Editor runs outside the package; the user confirms one prompt.
     b. Show the two `reg add` commands in Settings → Browser extension, for the user to copy into
        a terminal once.
     c. Ship the extension without native messaging and talk to the app's local Ktor server on
        localhost instead (needs extension and server changes; review the extension's host
        permissions for the Chrome Web Store).
     The manifest file itself must also live outside the virtualized folder, since browsers read
     its path from the registry.
3. **Keep the MSI** on GitHub Releases as the full-featured download either way.
