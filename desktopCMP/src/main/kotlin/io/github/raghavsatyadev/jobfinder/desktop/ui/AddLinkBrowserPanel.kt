package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.desktop.browser.CefRuntime
import io.github.raghavsatyadev.jobfinder.desktop.browser.EmbeddedBrowser
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.settings_browser_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_browser_read_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_browser_reading
import io.github.raghavsatyadev.jobfinder.resources.settings_browser_use_page
import io.github.raghavsatyadev.jobfinder.server.service.LoadedPage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.slf4j.LoggerFactory

/**
 * The add-search browser: toolbar, "Use this page" and "Cancel" above a JCEF browser on the
 * scanner's profile (so the user's sign-ins apply), embedded in the app window. "Use this page"
 * reads the page as the user sees it (the old overlay browser's `finish`, 15 s limit) and keeps it
 * in [picked], so saving the search parses that page instead of loading it again.
 */
@Composable
internal fun AddLinkBrowserPanel(
  runtime: CefRuntime,
  picked: PickedPages,
  onPicked: (url: String, title: String) -> Unit,
  onCancel: () -> Unit,
) {
  val browser = remember(runtime) { EmbeddedBrowser(runtime, START_URL, popupsInPlace = true) }
  DisposableEffect(browser) { onDispose { browser.close() } }
  val url by browser.url.collectAsState()
  val scope = rememberCoroutineScope()
  var reading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val pickable = !reading && (url.startsWith("http://") || url.startsWith("https://"))

  Column(Modifier.fillMaxSize()) {
    BrowserToolbar(browser) {
      TextButton(onClick = onCancel) { Text(stringResource(Res.string.common_cancel)) }
      Button(
        onClick = {
          reading = true
          failed = false
          scope.launch {
            try {
              val page = browser.snapshot()
              picked.put(page.url, LoadedPage(page.html))
              onPicked(page.url, page.title.ifBlank { page.url })
            } catch (e: TimeoutCancellationException) {
              log.warn("{} did not answer within {}", url, EmbeddedBrowser.SNAPSHOT_TIMEOUT)
              failed = true
            } catch (e: Exception) {
              if (e is CancellationException) throw e
              log.warn("Could not read {}", url, e)
              failed = true
            } finally {
              reading = false
            }
          }
        },
        enabled = pickable,
      ) {
        Text(stringResource(Res.string.settings_browser_use_page))
      }
    }
    Text(
      stringResource(
        when {
          reading -> Res.string.settings_browser_reading
          failed -> Res.string.settings_browser_read_failed
          else -> Res.string.settings_browser_hint
        }
      ),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SwingPanel(factory = { browser.component }, modifier = Modifier.fillMaxWidth().weight(1f))
  }
}

private const val START_URL = "https://www.linkedin.com/jobs/search/"
private val log = LoggerFactory.getLogger("AddLinkBrowserPanel")

/**
 * The page the user picked in the add-search browser, kept until the search is saved. Only the
 * latest pick is kept.
 */
class PickedPages {
  @Volatile private var latest: Pair<String, LoadedPage>? = null

  fun put(url: String, page: LoadedPage) {
    latest = url to page
  }

  /** The page picked for [url], if that was the latest pick. */
  fun get(url: String): LoadedPage? = latest?.takeIf { it.first == url }?.second

  fun clear() {
    latest = null
  }
}
