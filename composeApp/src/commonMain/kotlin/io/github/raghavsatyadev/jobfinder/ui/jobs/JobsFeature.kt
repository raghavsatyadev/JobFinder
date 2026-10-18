package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.ui.ats.ResumeImproveViewModel
import io.github.raghavsatyadev.jobfinder.ui.navigation.JobDetailRoute
import kotlin.time.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin bindings of the jobs feature. */
val jobsUiModule = module {
  single { JobChanges() }
  single<JobsClock> { JobsClock { Clock.System.now().toEpochMilliseconds() } }
  single<JobFilterStore> { SettingsJobFilterStore() }
  viewModel { JobsListViewModel(get(), get(), get(), get(), get(), get()) }
  viewModel { params -> ResumeImproveViewModel(params.get(), get(), get()) }
  viewModel { params ->
    val args = params.get<JobDetailArgs>()
    JobDetailViewModel(
      args.jobId,
      args.preview,
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      get(),
      getOrNull(),
      getOrNull(),
      getOrNull(),
      getOrNull(),
    )
  }
}

/**
 * The job feed: status tabs, filters and the job list. [onOpenJob] opens a job in the detail pane.
 */
@Composable
fun JobsListPane(selectedJobId: Long?, onOpenJob: (Job) -> Unit) {
  JobsListScreen(selectedJobId, onOpenJob)
}

/** One job in the detail pane. [onClose] leaves the detail (single-pane layouts). */
@Composable
fun JobDetailPane(route: JobDetailRoute, onClose: () -> Unit) {
  JobDetailView(route.jobId, route.preview, onClose)
}

/** The detail pane before a job is picked (two-pane layouts). */
@Composable
fun JobDetailPlaceholder() {
  JobDetailPlaceholderContent()
}
