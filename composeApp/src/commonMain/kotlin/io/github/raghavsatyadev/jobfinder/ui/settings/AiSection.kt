package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_ai_part_main
import io.github.raghavsatyadev.jobfinder.resources.settings_section_keys
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Settings → AI: the main model, then the free online keys used before it. */
@Composable
internal fun AiSection(main: (@Composable () -> Unit)?, keys: (@Composable () -> Unit)?) {
  if (main != null) {
    if (keys != null) PartTitle(Res.string.settings_ai_part_main)
    main()
  }
  if (keys != null) {
    if (main != null) {
      HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      PartTitle(Res.string.settings_section_keys)
    }
    keys()
  }
}

@Composable
private fun PartTitle(title: StringResource) {
  Text(
    stringResource(title),
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).semantics { heading() },
  )
}
