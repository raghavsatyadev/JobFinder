@file:OptIn(
  ExperimentalMaterial3AdaptiveApi::class,
  ExperimentalSharedTransitionApi::class,
  KoinExperimentalAPI::class,
)

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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ic_auto_awesome
import io.github.raghavsatyadev.jobfinder.resources.ic_auto_awesome_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_settings
import io.github.raghavsatyadev.jobfinder.resources.ic_settings_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_travel_explore
import io.github.raghavsatyadev.jobfinder.resources.ic_travel_explore_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_work_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_work_outline
import io.github.raghavsatyadev.jobfinder.resources.nav_ai_filters
import io.github.raghavsatyadev.jobfinder.resources.nav_jobs
import io.github.raghavsatyadev.jobfinder.resources.nav_links
import io.github.raghavsatyadev.jobfinder.resources.nav_settings
import io.github.raghavsatyadev.jobfinder.ui.components.LocalFloatingBarSpace
import io.github.raghavsatyadev.jobfinder.ui.components.LocalScrollBars
import io.github.raghavsatyadev.jobfinder.ui.components.LocalSharedTransitionScope
import io.github.raghavsatyadev.jobfinder.ui.components.ScrollBars
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.getKoin
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI

/** The top-level destinations, in the order the navigation bar or rail shows them. */
enum class TopLevel(
  val root: NavKey,
  val label: StringResource,
  val icon: DrawableResource,
  val selectedIcon: DrawableResource,
) {
  Jobs(
    JobsRoute,
    Res.string.nav_jobs,
    Res.drawable.ic_work_outline,
    Res.drawable.ic_work_filled,
  ),
  Links(
    LinksRoute,
    Res.string.nav_links,
    Res.drawable.ic_travel_explore,
    Res.drawable.ic_travel_explore_filled,
  ),
  AiFilters(
    AiFiltersRoute,
    Res.string.nav_ai_filters,
    Res.drawable.ic_auto_awesome,
    Res.drawable.ic_auto_awesome_filled,
  ),
  Settings(
    SettingsRoute,
    Res.string.nav_settings,
    Res.drawable.ic_settings,
    Res.drawable.ic_settings_filled,
  ),
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
                vectorResource(if (selected) destination.selectedIcon else destination.icon),
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
  val entries = koinEntryProvider<NavKey>()

  SharedTransitionLayout {
    CompositionLocalProvider(
      LocalSharedTransitionScope provides this.takeUnless { instant },
      LocalBackStack provides backStack,
      LocalSideBySide provides (directive.maxHorizontalPartitions > 1),
    ) {
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
        entryProvider = entries,
      )
    }
  }
}

private fun Scene<NavKey>.growsFromContainer() = entries.any {
  it.metadata[CONTAINER_TRANSFORM_KEY] == true
}

/**
 * Into and out of an inner screen the outer one stays behind the container that grows into it or
 * shrinks back: the arriving screen fades in over the transform and the leaving one only fades at
 * the end, so closing reverses opening. Any other change is a quick fade. Side by side (and on the
 * computer) [instant] drops both: the panes just swap.
 */
private fun sceneTransition(
  instant: Boolean,
  from: Scene<NavKey>,
  to: Scene<NavKey>,
): ContentTransform =
  when {
    instant -> EnterTransition.None togetherWith ExitTransition.None
    from.growsFromContainer() || to.growsFromContainer() ->
      fadeIn(tween(500), initialAlpha = 0.99f) togetherWith fadeOut(tween(120, delayMillis = 380))
    else -> fadeIn(tween(150)) togetherWith fadeOut(tween(90))
  }
