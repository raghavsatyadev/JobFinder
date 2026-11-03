@file:OptIn(ExperimentalMaterial3AdaptiveApi::class, KoinExperimentalAPI::class)

package io.github.raghavsatyadev.jobfinder.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.raghavsatyadev.jobfinder.ui.components.HELP_KEY
import io.github.raghavsatyadev.jobfinder.ui.components.HelpButtonCorner
import io.github.raghavsatyadev.jobfinder.ui.components.LocalEntryVisibilityScope
import io.github.raghavsatyadev.jobfinder.ui.components.settingsSectionKey
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContainer
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersScreen
import io.github.raghavsatyadev.jobfinder.ui.help.HelpScreen
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailPane
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailPlaceholder
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsListPane
import io.github.raghavsatyadev.jobfinder.ui.links.AddLinkScreen
import io.github.raghavsatyadev.jobfinder.ui.links.LinksScreen
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsPlaceholder
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsScreen
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsSection
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** The back stack of the open top-level destination; [AppShell] provides it. */
val LocalBackStack = staticCompositionLocalOf<NavBackStack<NavKey>> { error("No back stack") }

/** True when list and detail sit side by side; [AppShell] provides it. */
val LocalSideBySide = staticCompositionLocalOf { false }

internal const val CONTAINER_TRANSFORM_KEY = "jobfinder.containerTransform"

/**
 * Marks an inner screen that grows out of what opened it (a job card, a settings row, a button) and
 * shrinks back into it; its screen change waits for that container transform.
 */
private val CONTAINER_TRANSFORM = mapOf<String, Any>(CONTAINER_TRANSFORM_KEY to true)

private fun NavBackStack<NavKey>.pop() {
  removeLastOrNull()
}

/** Gives the entry's content the visibility scope its shared elements animate in. */
@Composable
private fun Entry(content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalEntryVisibilityScope provides LocalNavAnimatedContentScope.current,
    content = content,
  )
}

/** Every screen, one Koin `navigation` entry per route; [AppShell] shows them. */
val navigationModule = module {
  navigation<JobsRoute>(
    metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { JobDetailPlaceholder() })
  ) {
    val backStack = LocalBackStack.current
    val sideBySide = LocalSideBySide.current
    Entry {
      // Only side by side does the open job show as selected: on a phone the card would flash its
      // selection border while it grows into the detail.
      val selected = (backStack.lastOrNull() as? JobDetailRoute)?.jobId?.takeIf { sideBySide }
      JobsListPane(
        selectedJobId = selected,
        onOpenJob = { job ->
          // One detail at a time: replace the open job instead of stacking details.
          if (backStack.lastOrNull() is JobDetailRoute) backStack.pop()
          backStack.add(JobDetailRoute(job.id, preview = job))
        },
      )
    }
  }
  navigation<JobDetailRoute>(
    metadata = ListDetailSceneStrategy.detailPane() + CONTAINER_TRANSFORM
  ) { route ->
    val backStack = LocalBackStack.current
    Entry { JobDetailPane(route, onClose = { backStack.pop() }) }
  }
  navigation<LinksRoute> {
    val backStack = LocalBackStack.current
    Entry { LinksScreen(onAddLink = { backStack.add(AddLinkRoute) }) }
  }
  navigation<AddLinkRoute>(metadata = CONTAINER_TRANSFORM) {
    val backStack = LocalBackStack.current
    Entry { AddLinkScreen(onDone = { backStack.pop() }) }
  }
  navigation<AiFiltersRoute> { Entry { AiFiltersScreen() } }
  navigation<SettingsRoute>(
    metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { SettingsPlaceholder() })
  ) {
    val backStack = LocalBackStack.current
    val sideBySide = LocalSideBySide.current
    Entry {
      val open = (backStack.lastOrNull() as? SettingsSectionRoute)?.section
      SettingsScreen(
        onOpenSection = {
          // Side by side, one page at a time: the new section replaces the open one.
          if (backStack.lastOrNull() is SettingsSectionRoute) backStack.pop()
          backStack.add(SettingsSectionRoute(it.name))
        },
        onOpenHelp = { backStack.add(HelpRoute) },
        selected = SettingsSection.entries.firstOrNull { it.name == open }.takeIf { sideBySide },
      )
    }
  }
  navigation<SettingsSectionRoute>(
    metadata = ListDetailSceneStrategy.detailPane() + CONTAINER_TRANSFORM
  ) { route ->
    val backStack = LocalBackStack.current
    val sideBySide = LocalSideBySide.current
    val section = SettingsSection.entries.firstOrNull { it.name == route.section }
    Entry {
      // The page grows out of its row in the menu, and shrinks back into it.
      Box(Modifier.sharedContainer(settingsSectionKey(route.section), fullScreen = true)) {
        SettingsScreen(section = section, onBack = { backStack.pop() }, backArrow = !sideBySide)
      }
    }
  }
  navigation<HelpRoute>(metadata = CONTAINER_TRANSFORM) {
    val backStack = LocalBackStack.current
    Entry {
      Box(Modifier.sharedContainer(HELP_KEY, fullScreen = true, corner = HelpButtonCorner)) {
        HelpScreen(onBack = { backStack.pop() })
      }
    }
  }
}
