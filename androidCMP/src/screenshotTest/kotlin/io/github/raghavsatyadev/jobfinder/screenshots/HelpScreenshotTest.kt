package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_guide_add_computer
import io.github.raghavsatyadev.jobfinder.resources.help_guide_check_connection
import io.github.raghavsatyadev.jobfinder.ui.help.GuideAction
import io.github.raghavsatyadev.jobfinder.ui.help.GuideActions
import io.github.raghavsatyadev.jobfinder.ui.help.GuideDevice
import io.github.raghavsatyadev.jobfinder.ui.help.GuideId
import io.github.raghavsatyadev.jobfinder.ui.help.HelpContent
import io.github.raghavsatyadev.jobfinder.ui.help.HelpScreen

@PreviewTest
@ScreenPreviews
@Composable
fun HelpScreenshot() {
  ScreenshotHost { HelpScreen() }
}

/** On the phone: its own guide first and open, with the buttons that work there. */
@PreviewTest
@ScreenPreviews
@Composable
fun HelpPhoneScreenshot() {
  ScreenshotHost {
    HelpContent(
      GuideDevice.PHONE,
      GuideActions(
        addComputer = GuideAction(Res.string.help_guide_add_computer) {},
        checkConnection = GuideAction(Res.string.help_guide_check_connection) {},
      ),
    )
  }
}

/** The backup and move guide, open on the phone. */
@PreviewTest
@ScreenPreviews
@Composable
fun HelpBackupGuideScreenshot() {
  ScreenshotHost {
    HelpContent(
      GuideDevice.PHONE,
      GuideActions(addComputer = GuideAction(Res.string.help_guide_add_computer) {}),
      firstOpen = GuideId.BACKUP,
    )
  }
}

/** Opened from Settings with a search: only the matches, opened, and a back arrow. */
@PreviewTest
@ScreenPreviews
@Composable
fun HelpSearchScreenshot() {
  ScreenshotHost {
    HelpContent(GuideDevice.PHONE, GuideActions(), onBack = {}, initialQuery = "wifi")
  }
}

/** A search with no match says so. */
@PreviewTest
@ScreenPreviews
@Composable
fun HelpSearchEmptyScreenshot() {
  ScreenshotHost {
    HelpContent(GuideDevice.PHONE, GuideActions(), onBack = {}, initialQuery = "zebra")
  }
}
