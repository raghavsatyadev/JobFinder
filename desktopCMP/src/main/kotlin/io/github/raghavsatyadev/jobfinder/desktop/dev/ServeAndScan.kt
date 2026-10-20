package io.github.raghavsatyadev.jobfinder.desktop.dev

import io.github.raghavsatyadev.jobfinder.desktop.browser.CefBrowserPool
import io.github.raghavsatyadev.jobfinder.desktop.server.EmbeddedServer
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.JobScanner
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform

/** How long `serve` runs by default. */
private const val SERVE_SECONDS = 600L

/**
 * Dev command `serve [seconds]`: the API server without any window, for checking it from a phone or
 * with curl. Prints the address and the pairing token.
 */
fun serve(requested: Long?): Int {
  val seconds = requested ?: SERVE_SECONDS
  val koin = KoinPlatform.getKoin()
  val server = koin.get<EmbeddedServer>()
  server.start()
  try {
    val endpoint = runBlocking {
      withTimeoutOrNull(15.seconds) { koin.get<ApiServerHost>().endpoint.filterNotNull().first() }
    }
    if (endpoint == null) {
      println("No LAN address: the API server is not running.")
      return 1
    }
    val token = runBlocking { koin.get<LocalSettingsRepository>().pairingToken() }
    println("API server: ${endpoint.baseUrl} (for $seconds s)")
    println("Pairing token: $token")
    Thread.sleep(seconds * 1000)
    return 0
  } finally {
    server.close()
  }
}

/**
 * Dev command `scan [linkId]`: without an id, lists the saved links; with one, scans that link end
 * to end in the hidden browsers (list page, then every job description and advanced matching), with
 * no window and no notification, and prints what it stored.
 */
fun scanCommand(linkId: Long?): Int = runBlocking {
  val koin = KoinPlatform.getKoin()
  val db = koin.get<ServerDatabase>()
  if (linkId == null) {
    for (link in db.linksDao().listAll()) {
      println("${link.id}	${link.title}	failures=${link.scrapeFailureCount}	${link.url}")
    }
    return@runBlocking 0
  }
  val scanner = koin.get<JobScanner>()
  val started = TimeSource.Monotonic.markNow()
  try {
    println("Processing jobs before: ${db.jobsDao().listProcessing().size}")
    val inserted = scanner.scanLink(linkId)
    println("Scan took ${started.elapsedNow()}; state ${scanner.state.value}")
    println("New jobs from the page: ${inserted.size}")
    for (id in inserted) {
      val job = db.jobsDao().get(id) ?: continue
      println(
        "  ${job.id} ${job.status} '${job.title}' at ${job.companyName}, " +
          "description ${job.description?.length ?: 0} chars"
      )
    }
    val link = db.linksDao().get(linkId)
    println("Link failures ${link?.scrapeFailureCount}, last scraped ${link?.lastScrapedAt}")
    println("Processing jobs after: ${db.jobsDao().listProcessing().size}")
    0
  } finally {
    scanner.close()
    koin.get<CefBrowserPool>().close()
  }
}
