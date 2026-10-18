package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.desktop.browser.EmbeddedBrowser
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.browser_back
import io.github.raghavsatyadev.jobfinder.resources.browser_forward
import io.github.raghavsatyadev.jobfinder.resources.browser_reload
import io.github.raghavsatyadev.jobfinder.resources.settings_browser_address
import org.jetbrains.compose.resources.stringResource

/**
 * Back, forward, reload and the address field of an [EmbeddedBrowser], then [actions] at the end.
 * The field follows the page's address unless the user is typing.
 */
@Composable
internal fun BrowserToolbar(
  browser: EmbeddedBrowser,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit,
) {
  val url by browser.url.collectAsState()
  val canGoBack by browser.canGoBack.collectAsState()
  val canGoForward by browser.canGoForward.collectAsState()
  var address by remember { mutableStateOf(url) }
  LaunchedEffect(url) { address = url }

  Row(
    modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconButton(onClick = browser::goBack, enabled = canGoBack) {
      Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.browser_back))
    }
    IconButton(onClick = browser::goForward, enabled = canGoForward) {
      Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(Res.string.browser_forward))
    }
    IconButton(onClick = browser::reload) {
      Icon(Icons.Rounded.Refresh, stringResource(Res.string.browser_reload))
    }
    OutlinedTextField(
      value = address,
      onValueChange = { address = it },
      singleLine = true,
      label = { Text(stringResource(Res.string.settings_browser_address)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
      keyboardActions = KeyboardActions(onGo = { browser.load(address) }),
      modifier = Modifier.weight(1f),
    )
    actions()
  }
}
