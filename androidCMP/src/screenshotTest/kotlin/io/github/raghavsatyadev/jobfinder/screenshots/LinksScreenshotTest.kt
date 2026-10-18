package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import io.github.raghavsatyadev.jobfinder.ui.links.AddLinkContent
import io.github.raghavsatyadev.jobfinder.ui.links.AddLinkUiState
import io.github.raghavsatyadev.jobfinder.ui.links.LinkSort
import io.github.raghavsatyadev.jobfinder.ui.links.LinksContent
import io.github.raghavsatyadev.jobfinder.ui.links.LinksPreviewData
import io.github.raghavsatyadev.jobfinder.ui.links.LinksScreen
import io.github.raghavsatyadev.jobfinder.ui.links.LinksUiState
import io.github.raghavsatyadev.jobfinder.ui.links.LinksView
import io.github.raghavsatyadev.jobfinder.ui.links.UrlError
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// The ViewModel loads in a coroutine that a single preview frame does not wait for, so most of
// these render the stateless screen with the state the ViewModel reaches (see LinksViewModelTest).

/** The searches, one of them broken (four failed scans in a row). */
@PreviewTest
@ScreenPreviews
@Composable
fun LinksScreenshot() {
  Links(LinksPreviewData.list())
}

/** Desktop: the pages can be opened in the sign-in browser; one search is being scanned again. */
@PreviewTest
@ScreenPreviews
@Composable
fun LinksDesktopScanningLinkScreenshot() {
  Links(LinksPreviewData.list(scanningLinkIds = setOf(2L), canOpenPages = true))
}

/** Grouped by board, failing searches first. */
@PreviewTest
@ScreenPreviews
@Composable
fun LinksGroupedScreenshot() {
  Links(
    LinksPreviewData.list().copy(view = LinksView(groupByBoard = true, sort = LinkSort.FAILING))
  )
}

/** A search text that hides every search. */
@PreviewTest
@StatePreviews
@Composable
fun LinksNoMatchScreenshot() {
  Links(LinksPreviewData.list().copy(view = LinksView(query = "zebra")))
}

/** A scan of every search is running. */
@PreviewTest
@StatePreviews
@Composable
fun LinksScanAllScreenshot() {
  Links(
    LinksPreviewData.list(
      scan = ScanState(scanning = true, startedAt = SampleData.NOW, linksTotal = 3, linksDone = 1)
    )
  )
}

@PreviewTest
@StatePreviews
@Composable
fun LinksEmptyScreenshot() {
  Links(LinksPreviewData.empty())
}

@PreviewTest
@StatePreviews
@Composable
fun LinksLoadingScreenshot() {
  ScreenshotHost(FakeBackend().apply { latencyMillis = 60_000 }) { LinksScreen(onAddLink = {}) }
}

@PreviewTest
@StatePreviews
@Composable
fun LinksErrorScreenshot() {
  ScreenshotHost(FakeBackend().apply { failWith = IllegalStateException("offline") }) {
    LinksScreen(onAddLink = {})
  }
}

/** The edit dialog, with the URL moved to another board. */
@PreviewTest
@StatePreviews
@Composable
fun LinksEditDialogScreenshot() {
  Links(LinksPreviewData.list(edit = LinksPreviewData.editOtherBoard()))
}

/** Why a search is broken, with retry and (desktop) open page. */
@PreviewTest
@StatePreviews
@Composable
fun LinksBrokenExplainScreenshot() {
  Links(LinksPreviewData.list(explaining = LinksPreviewData.brokenLink, canOpenPages = true))
}

/** Android: paste the search URL; the supported boards are listed. */
@PreviewTest
@ScreenPreviews
@Composable
fun AddLinkScreenshot() {
  AddLink(LinksPreviewData.addLink(title = "", url = ""))
}

/** Desktop: browse to the search page in the scanner's browser. */
@PreviewTest
@StatePreviews
@Composable
fun AddLinkDesktopScreenshot() {
  AddLink(LinksPreviewData.addLink(), desktop = PreviewDesktop)
}

/** A board the app has no reader for: it asks for the board's name. */
@PreviewTest
@StatePreviews
@Composable
fun AddLinkCustomBoardScreenshot() {
  AddLink(
    LinksPreviewData.addLink(
      title = "Android, open to India",
      url = "https://jobgether.com/remote-jobs/android?location=india",
    )
  )
}

/** Inline errors: no title and not a full address. */
@PreviewTest
@StatePreviews
@Composable
fun AddLinkInvalidScreenshot() {
  AddLink(
    LinksPreviewData.addLink(
      title = "",
      url = "linkedin.com/jobs",
      titleMissing = true,
      urlError = UrlError.Invalid,
    )
  )
}

/** The page is being loaded and checked for a job list. */
@PreviewTest
@StatePreviews
@Composable
fun AddLinkSavingScreenshot() {
  AddLink(LinksPreviewData.addLink(saving = true))
}

/** No job list on the page: "save anyway". */
@PreviewTest
@StatePreviews
@Composable
fun AddLinkConflictScreenshot() {
  AddLink(
    LinksPreviewData.addLink(
      url = "https://www.linkedin.com/jobs/view/4012345678",
      conflictMessage =
        "No jobs were found on this LinkedIn page. This usually means it shows a single job " +
          "instead of a list of jobs.",
    )
  )
}

@PreviewTest
@StatePreviews
@Composable
fun AddLinkCreatedScreenshot() {
  AddLink(LinksPreviewData.created(newJobs = 12))
}

@Composable
private fun Links(state: LinksUiState) {
  ScreenshotHost { LinksContent(state = state, onEvent = {}, onAddLink = {}) }
}

@Composable
private fun AddLink(state: AddLinkUiState, desktop: DesktopFeatures? = null) {
  ScreenshotHost { AddLinkContent(state = state, onEvent = {}, onDone = {}, desktop = desktop) }
}

/** Only the add-search screen's check for the desktop is used. */
private object PreviewDesktop : DesktopFeatures {
  override val pairing: StateFlow<PairingInfo?> = MutableStateFlow(null)

  override suspend fun rotatePairingToken() = Unit

  override fun openLoginBrowser(url: String?) = Unit

  override suspend fun copyBrowserSignIns() = SignInCopy.NoExtension

  @Composable
  override fun AddLinkBrowser(
    onPicked: (url: String, title: String) -> Unit,
    onCancel: () -> Unit,
  ) = Unit
}
