package io.github.raghavsatyadev.jobfinder.desktop.dev

import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabaseFactory
import io.github.raghavsatyadev.jobfinder.server.nativehost.JobUrlLookup
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.server.nativehost.PageReadResult
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Developer check of the browser extension's `readPage`, end to end: opens the app's end of the
 * native host link in `%LOCALAPPDATA%\jobfinder` (point `LOCALAPPDATA` at a scratch folder, and
 * close the app), waits for a browser with the extension to connect, then asks it to read the URL.
 * Run as `./gradlew :desktopCMP:readPageProbe --args="<url> [waitSeconds]"`.
 */
fun main(args: Array<String>) {
  val url = args.getOrNull(0) ?: "https://example.com/"
  val waitSeconds = args.getOrNull(1)?.toLongOrNull() ?: DEFAULT_WAIT_S
  val data = File(System.getenv("LOCALAPPDATA"), "jobfinder").apply { mkdirs() }
  val scratch = Files.createTempDirectory("f2a-readpage").toFile()
  val db = ServerDatabaseFactory.create(File(scratch, "probe.db").toPath())
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  val link =
    NativeHostLink(
      connectionFile = data.resolve(NativeHostLink.CONNECTION_FILE).toPath(),
      appVersion = "probe",
      jobs = JobUrlLookup(db.jobsDao()) { null },
    )
  val code = runBlocking {
    link.start(scope)
    println("Waiting up to $waitSeconds s for a browser to connect")
    val connected =
      withTimeoutOrNull(waitSeconds * 1_000) {
        while (!link.isConnected) delay(POLL_MS)
        true
      } == true
    if (!connected) {
      println("FAIL no browser connected")
      return@runBlocking 1
    }
    when (val result = link.readPage(url, includeHtml = true)) {
      is PageReadResult.Read -> {
        val page = result.page
        println(
          "OK finalUrl=${page.finalUrl} title='${page.title}' text=${page.text.length} " +
            "html=${page.html?.length ?: 0} jsonLd=${page.jsonLd.size} ms=${page.elapsedMs}"
        )
        0
      }
      is PageReadResult.Failed -> {
        println("FAIL ${result.code}: ${result.message}")
        1
      }
    }
  }
  link.close()
  scope.cancel()
  db.close()
  scratch.deleteRecursively()
  exitProcess(code)
}

private const val DEFAULT_WAIT_S = 120L
private const val POLL_MS = 250L
