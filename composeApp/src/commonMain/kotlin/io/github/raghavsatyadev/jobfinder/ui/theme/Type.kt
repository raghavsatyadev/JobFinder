package io.github.raghavsatyadev.jobfinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle

/**
 * The Material type scale with tabular figures on the display and headline styles, so a changing
 * count does not shift sideways.
 */
@Composable
fun appTypography(): Typography = remember {
  val base = Typography()
  fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
  base.copy(
    displayLarge = base.displayLarge.tabular(),
    displayMedium = base.displayMedium.tabular(),
    displaySmall = base.displaySmall.tabular(),
    headlineLarge = base.headlineLarge.tabular(),
    headlineMedium = base.headlineMedium.tabular(),
    headlineSmall = base.headlineSmall.tabular(),
  )
}
