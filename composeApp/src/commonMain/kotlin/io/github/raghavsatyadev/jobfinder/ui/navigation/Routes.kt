package io.github.raghavsatyadev.jobfinder.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** The job feed with its status tabs. The list pane of the jobs list-detail layout. */
@Serializable data object JobsRoute : NavKey

/**
 * One job, in the detail pane. [preview] is the list's copy of the job, shown at once so the
 * container transform completes on its first frame; the screen then loads the full job.
 */
@Serializable data class JobDetailRoute(val jobId: Long, val preview: Job? = null) : NavKey

/** Saved searches (the old "links"). */
@Serializable data object LinksRoute : NavKey

/** The add-search screen, opened from the Searches FAB. */
@Serializable data object AddLinkRoute : NavKey

/** The advanced-matching prompt and company blacklist. */
@Serializable data object AiFiltersRoute : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object HelpRoute : NavKey

/** Saves the back stacks on every platform: Nav3 needs the [NavKey] subclasses registered. */
val NavSavedStateConfiguration = SavedStateConfiguration {
  serializersModule = SerializersModule {
    polymorphic(NavKey::class) {
      subclass(JobsRoute::class, JobsRoute.serializer())
      subclass(JobDetailRoute::class, JobDetailRoute.serializer())
      subclass(LinksRoute::class, LinksRoute.serializer())
      subclass(AddLinkRoute::class, AddLinkRoute.serializer())
      subclass(AiFiltersRoute::class, AiFiltersRoute.serializer())
      subclass(SettingsRoute::class, SettingsRoute.serializer())
      subclass(HelpRoute::class, HelpRoute.serializer())
    }
  }
}
