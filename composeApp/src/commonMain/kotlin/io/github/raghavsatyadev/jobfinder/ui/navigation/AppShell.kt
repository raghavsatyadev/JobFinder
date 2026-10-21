@file:OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)

package io.github.raghavsatyadev.jobfinder.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.nav_ai_filters
import io.github.raghavsatyadev.jobfinder.resources.nav_jobs
import io.github.raghavsatyadev.jobfinder.resources.nav_links
import io.github.raghavsatyadev.jobfinder.resources.nav_settings
import io.github.raghavsatyadev.jobfinder.ui.components.LocalEntryVisibilityScope
import io.github.raghavsatyadev.jobfinder.ui.components.LocalFloatingBarSpace
import io.github.raghavsatyadev.jobfinder.ui.components.LocalScrollBars
import io.github.raghavsatyadev.jobfinder.ui.components.LocalSharedTransitionScope
import io.github.raghavsatyadev.jobfinder.ui.components.ScrollBars
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersScreen
import io.github.raghavsatyadev.jobfinder.ui.help.HelpScreen
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailPane
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailPlaceholder
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsListPane
import io.github.raghavsatyadev.jobfinder.ui.links.AddLinkScreen
import io.github.raghavsatyadev.jobfinder.ui.links.LinksScreen
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsPlaceholder
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsScreen
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsSection
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/** The top-level destinations, in the order the navigation bar or rail shows them. */
enum class TopLevel(
  val root: NavKey,
  val label: StringResource,
  val icon: ImageVector,
  val selectedIcon: ImageVector,
) {
  Jobs(JobsRoute, Res.string.nav_jobs, Icons.Outlined.WorkOutline, Icons.Rounded.Work),
  Links(
    LinksRoute,
    Res.string.nav_links,
    Icons.Outlined.TravelExplore,
    Icons.Rounded.TravelExplore,
  ),
  AiFilters(
    AiFiltersRoute,
    Res.string.nav_ai_filters,
    Icons.Outlined.AutoAwesome,
    Icons.Rounded.AutoAwesome,
  ),
  Settings(SettingsRoute, Res.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

private val TABS = TopLevel.entries.toImmutableList()

/**
 * The app frame: a floating tab bar on phones and a rail on wider windows, one back stack per
 * top-level destination, and a list-detail layout for jobs (list and detail side by side when the
 * window is wide enough). A job card grows into its detail through the shared transition scope.
 * [navigation] switches the destination from outside the UI.
 */
@Composable
fun AppShell(initial: TopLevel = TopLevel.Jobs, navigation: Flow<TopLevel> = emptyFlow()) {
  var current by rememberSaveable { mutableStateOf(initial) }
  LaunchedEffect(navigation) { navigation.collect { current = it } }
  val bars = remember { ScrollBars() }
  // A list scrolled down hides the bars; another destination starts with them back.
  LaunchedEffect(current) { bars.visible = true }
  val stacks: Map<TopLevel, NavBackStack<NavKey>> =
    TopLevel.entries.associateWith { rememberNavBackStack(NavSavedStateConfiguration, it.root) }

  val select: (TopLevel) -> Unit = { destination ->
    if (destination == current) {
      // Tapping the current destination again goes back to its root.
      val stack = stacks.getValue(destination)
      while (stack.size > 1) stack.removeLastOrNull()
    } else {
      current = destination
    }
  }
  val floating =
    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()) ==
      NavigationSuiteType.NavigationBar
  CompositionLocalProvider(LocalScrollBars provides bars) {
    NavigationSuiteScaffold(
      layoutType = if (floating) NavigationSuiteType.None else NavigationSuiteType.NavigationRail,
      navigationSuiteItems = {
        TopLevel.entries.forEach { destination ->
          val selected = destination == current
          item(
            selected = selected,
            onClick = { select(destination) },
            icon = {
              Icon(
                if (selected) destination.selectedIcon else destination.icon,
                contentDescription = null,
              )
            },
            label = {
              // One line at any font scale: shrink the label rather than break a word.
              Text(
                stringResource(destination.label),
                maxLines = 1,
                autoSize =
                  TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = LocalTextStyle.current.fontSize,
                  ),
              )
            },
          )
        }
      },
    ) {
      if (floating) {
        FloatingFrame(current, select) { Destinations(stacks.getValue(current)) }
      } else {
        Destinations(stacks.getValue(current))
      }
    }
  }
}

/**
 * The screens with a floating tab bar over them. They run on under the bar; [LocalFloatingBarSpace]
 * tells them how much room to leave at the end and keeps their own buttons above it.
 */
@Composable
private fun FloatingFrame(
  current: TopLevel,
  onSelect: (TopLevel) -> Unit,
  content: @Composable () -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(
      LocalFloatingBarSpace provides FloatingTabBarHeight + FLOATING_BAR_GAP * 2,
      content = content,
    )
    // Slides away while a list scrolls down, and back up when it scrolls up.
    val animate = animationsEnabled()
    AnimatedVisibility(
      visible = LocalScrollBars.current?.visible != false,
      enter = if (animate) slideInVertically { it } + fadeIn() else EnterTransition.None,
      exit = if (animate) slideOutVertically { it } + fadeOut() else ExitTransition.None,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = FLOATING_BAR_GAP),
    ) {
      FloatingTabBar(TABS, current, onSelect)
    }
  }
}

private val FLOATING_BAR_GAP = 12.dp

@Composable
private fun Destinations(backStack: NavBackStack<NavKey>) {
  val windowInfo = currentWindowAdaptiveInfo()
  val directive =
    remember(windowInfo) {
      calculatePaneScaffoldDirective(windowInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }
  val listDetail = rememberListDetailSceneStrategy<NavKey>(directive = directive)
  // On the computer, and whenever list and detail sit side by side, a job opens at once: the
  // card-to-detail transform there moved every piece across the window.
  val koin = getKoin()
  val desktop = remember { koin.getOrNull<DesktopFeatures>() != null }
  val instant = desktop || directive.maxHorizontalPartitions > 1

  SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this.takeUnless { instant }) {
      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetail),
        sharedTransitionScope = this@SharedTransitionLayout.takeUnless { instant },
        entryDecorators =
          listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
          ),
        transitionSpec = { sceneTransition(instant, initialState, targetState) },
        popTransitionSpec = { sceneTransition(instant, initialState, targetState) },
        // The system back button and gesture too, not the library's default slide and scale.
        predictivePopTransitionSpec = { sceneTransition(instant, initialState, targetState) },
        entryProvider =
          entryProvider {
            entry<JobsRoute>(
              metadata =
                ListDetailSceneStrategy.listPane(detailPlaceholder = { JobDetailPlaceholder() })
            ) {
              Entry {
                // Only side by side does the open job show as selected: on a phone the card
                // would flash its selection border while it grows into the detail.
                val selected =
                  (backStack.lastOrNull() as? JobDetailRoute)?.jobId?.takeIf {
                    directive.maxHorizontalPartitions > 1
                  }
                JobsListPane(
                  selectedJobId = selected,
                  onOpenJob = { job ->
                    // One detail at a time: replace the open job instead of stacking details.
                    if (backStack.lastOrNull() is JobDetailRoute) backStack.removeLastOrNull()
                    backStack.add(JobDetailRoute(job.id, preview = job))
                  },
                )
              }
            }
            entry<JobDetailRoute>(
              metadata = ListDetailSceneStrategy.detailPane() + CARD_TRANSFORM
            ) { route ->
              Entry { JobDetailPane(route, onClose = { backStack.removeLastOrNull() }) }
            }
            entry<LinksRoute> {
              Entry { LinksScreen(onAddLink = { backStack.add(AddLinkRoute) }) }
            }
            entry<AddLinkRoute> {
              Entry { AddLinkScreen(onDone = { backStack.removeLastOrNull() }) }
            }
            entry<AiFiltersRoute> { Entry { AiFiltersScreen() } }
            entry<SettingsRoute>(
              metadata =
                ListDetailSceneStrategy.listPane(detailPlaceholder = { SettingsPlaceholder() })
            ) {
              Entry {
                val open = (backStack.lastOrNull() as? SettingsSectionRoute)?.section
                SettingsScreen(
                  onOpenSection = {
                    // Side by side, one page at a time: the new section replaces the open one.
                    if (backStack.lastOrNull() is SettingsSectionRoute) backStack.removeLastOrNull()
                    backStack.add(SettingsSectionRoute(it.name))
                  },
                  onOpenHelp = { backStack.add(HelpRoute) },
                  selected =
                    SettingsSection.entries
                      .firstOrNull { it.name == open }
                      .takeIf { directive.maxHorizontalPartitions > 1 },
                )
              }
            }
            entry<SettingsSectionRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
              val section = SettingsSection.entries.firstOrNull { it.name == route.section }
              Entry {
                SettingsScreen(
                  section = section,
                  onBack = { backStack.removeLastOrNull() },
                  backArrow = directive.maxHorizontalPartitions <= 1,
                )
              }
            }
            entry<HelpRoute> { Entry { HelpScreen(onBack = { backStack.removeLastOrNull() }) } }
          },
      )
    }
  }
}

/** Marks the entry a job card grows into, whose screen change waits for that transform. */
private val CARD_TRANSFORM = mapOf("jobfinder.cardTransform" to true)

private fun Scene<NavKey>.growsFromCard() = entries.any {
  it.metadata["jobfinder.cardTransform"] == true
}

/**
 * Between a job card and its detail the list stays behind the card that grows into the detail: the
 * arriving screen fades in over the transform and the leaving one only fades at the end. Any other
 * change (a settings page, Help) is a quick fade, so back feels immediate.
 */
private fun sceneTransition(
  instant: Boolean,
  from: Scene<NavKey>,
  to: Scene<NavKey>,
): ContentTransform =
  when {
    instant -> EnterTransition.None togetherWith ExitTransition.None
    from.growsFromCard() || to.growsFromCard() ->
      fadeIn(tween(500), initialAlpha = 0.99f) togetherWith fadeOut(tween(120, delayMillis = 380))
    else -> fadeIn(tween(150)) togetherWith fadeOut(tween(90))
  }

/** Gives the entry's content the visibility scope its shared elements animate in. */
@Composable
private fun Entry(content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalEntryVisibilityScope provides LocalNavAnimatedContentScope.current,
    content = content,
  )
}
