package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.links_deleted
import io.github.raghavsatyadev.jobfinder.resources.links_scan_done
import io.github.raghavsatyadev.jobfinder.resources.links_updated
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import org.jetbrains.compose.resources.getString
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin bindings of the searches feature. */
val linksUiModule = module {
  single<LinksViewStore> { SettingsLinksViewStore() }
  viewModel {
    LinksViewModel(
      links = get(),
      sites = get(),
      scan = get(),
      desktop = getOrNull(),
      now = screenClock(getOrNull()),
      viewStore = get(),
    )
  }
  viewModelOf(::AddLinkViewModel)
}

/** Saved searches. [onAddLink] opens the add-search screen. */
@Composable
fun LinksScreen(onAddLink: () -> Unit) {
  val viewModel = koinViewModel<LinksViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      val message =
        when (effect) {
          is LinksEffect.Deleted -> getString(Res.string.links_deleted, effect.title)
          is LinksEffect.Updated -> getString(Res.string.links_updated, effect.title)
          is LinksEffect.ScanFinished ->
            getString(Res.string.links_scan_done, effect.title, effect.newJobs)
          is LinksEffect.Failed -> effect.message ?: getString(Res.string.common_error_generic)
        }
      snackbar.showSnackbar(message)
    }
  }
  LinksContent(
    state = state,
    onEvent = viewModel::onEvent,
    onAddLink = onAddLink,
    snackbarHostState = snackbar,
  )
}

/** Adds a search. [onDone] goes back to the list. */
@Composable
fun AddLinkScreen(onDone: () -> Unit) {
  val viewModel = koinViewModel<AddLinkViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val koin = getKoin()
  val desktop = remember(koin) { koin.getOrNull<DesktopFeatures>() }
  AddLinkContent(state = state, onEvent = viewModel::onEvent, onDone = onDone, desktop = desktop)
}
