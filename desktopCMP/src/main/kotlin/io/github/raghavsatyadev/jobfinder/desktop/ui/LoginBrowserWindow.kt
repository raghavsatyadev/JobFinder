package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.raghavsatyadev.jobfinder.desktop.browser.CefRuntime
import io.github.raghavsatyadev.jobfinder.desktop.browser.EmbeddedBrowser
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.login_window_done
import io.github.raghavsatyadev.jobfinder.resources.login_window_hint
import io.github.raghavsatyadev.jobfinder.resources.login_window_title
import io.github.raghavsatyadev.jobfinder.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource

/** A request to show the sign-in window at [url]; [id] tells two requests for one URL apart. */
data class LoginRequest(val url: String, val id: Long)

/** Whether the sign-in window is open, and on which page. One window at a time. */
class LoginBrowserState {
  private val _request = MutableStateFlow<LoginRequest?>(null)
  private var next = 0L

  val request: StateFlow<LoginRequest?> = _request.asStateFlow()

  /** Opens the window, or brings the open one to the front and loads [url] in it. */
  @Synchronized
  fun open(url: String) {
    _request.value = LoginRequest(url, next++)
  }

  fun close() {
    _request.value = null
  }
}

/**
 * The sign-in browser: its own normal window (never always-on-top) with back, forward, reload, an
 * address field and "Done" above a JCEF browser on the persistent profile, so a sign-in here
 * carries over to every scan. Popups get their own windows so "Sign in with Google" and the like
 * can report back to the page.
 */
@Composable
fun LoginBrowserWindow(
  runtime: CefRuntime,
  request: LoginRequest,
  onDone: () -> Unit,
) {
  val browser = remember(runtime) { EmbeddedBrowser(runtime, request.url, popupsInPlace = false) }
  DisposableEffect(browser) {
    onDispose {
      browser.close()
      // Write the new sign-in cookies now, off the UI thread (the flush calls back on it).
      Thread(runtime::flushCookies, "flush-cookies").start()
    }
  }
  val pageTitle by browser.title.collectAsState()
  val baseTitle = stringResource(Res.string.login_window_title)
  val state =
    rememberWindowState(
      size = DpSize(1280.dp, 900.dp),
      position = WindowPosition(Alignment.Center),
    )
  val title = if (pageTitle.isBlank()) baseTitle else "$baseTitle - $pageTitle"
  Window(onCloseRequest = onDone, state = state, title = title) {
    AppWindowIcon()
    LaunchedEffect(request) {
      if (browser.url.value != request.url) browser.load(request.url)
      window.toFront()
    }
    AppWindowFrame(title) {
      AppTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
          Column(Modifier.fillMaxSize()) {
            BrowserToolbar(browser) {
              Button(onClick = onDone) { Text(stringResource(Res.string.login_window_done)) }
            }
            Text(
              stringResource(Res.string.login_window_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SwingPanel(
              factory = { browser.component },
              modifier = Modifier.fillMaxWidth().weight(1f),
            )
          }
        }
      }
    }
  }
}
