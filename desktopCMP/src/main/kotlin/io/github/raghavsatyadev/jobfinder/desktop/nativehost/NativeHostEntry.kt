package io.github.raghavsatyadev.jobfinder.desktop.nativehost

import io.github.raghavsatyadev.jobfinder.desktop.browser.AppPaths
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostRelay
import java.io.BufferedInputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The app's executable doubles as the browser extension's native host: Chrome and Edge start it
 * with the caller origin (`chrome-extension://<id>/`) as the first argument. `native-host` does the
 * same by hand. Runs before anything else in `main`, without the single-instance lock, the JCEF
 * runtime or Koin.
 */
object NativeHostEntry {
  fun handles(args: Array<String>): Boolean =
    args.firstOrNull()?.let { it.startsWith(ORIGIN_PREFIX) || it == MANUAL_ARG } == true

  /** Relays until the browser closes the port; returns the exit code. */
  fun run(): Int {
    // stdout is the message channel: keep everything else (loggers, JVM notes) off it.
    val frames = FileOutputStream(FileDescriptor.out)
    System.setOut(System.err)
    val relay =
      NativeHostRelay(
        connectionFile = AppPaths.default().root.toPath().resolve(NativeHostLink.CONNECTION_FILE),
        hostVersion = System.getProperty(APP_VERSION_PROPERTY)?.ifBlank { null } ?: DEV_VERSION,
        stdin = BufferedInputStream(FileInputStream(FileDescriptor.`in`)),
        stdout = frames,
        logError = { System.err.println("native host: $it") },
      )
    relay.run()
    return 0
  }

  private const val ORIGIN_PREFIX = "chrome-extension://"
  private const val MANUAL_ARG = "native-host"
  /** Set by the installer's launcher; empty when run from the build. */
  private const val APP_VERSION_PROPERTY = "jpackage.app-version"
  private const val DEV_VERSION = "dev"
}
