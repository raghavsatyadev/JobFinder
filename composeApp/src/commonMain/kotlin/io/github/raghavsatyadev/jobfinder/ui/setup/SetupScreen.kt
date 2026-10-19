@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_full
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_keys
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_online
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_pc
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_small
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_split
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_title
import io.github.raghavsatyadev.jobfinder.resources.setup_all_done
import io.github.raghavsatyadev.jobfinder.resources.setup_back
import io.github.raghavsatyadev.jobfinder.resources.setup_check_again
import io.github.raghavsatyadev.jobfinder.resources.setup_close
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_cancel
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_go
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_install
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_model
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_permission
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_start
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_title
import io.github.raghavsatyadev.jobfinder.resources.setup_finish
import io.github.raghavsatyadev.jobfinder.resources.setup_fix_ai
import io.github.raghavsatyadev.jobfinder.resources.setup_fix_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_next
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai_title
import io.github.raghavsatyadev.jobfinder.resources.setup_page_done
import io.github.raghavsatyadev.jobfinder.resources.setup_page_of
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone_title
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches_title
import io.github.raghavsatyadev.jobfinder.resources.setup_title
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelTier
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupPage
import io.github.raghavsatyadev.jobfinder.ui.settings.TierRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val setupUiModule = module {
  viewModel { SetupViewModel(get(), getOrNull(), getOrNull()) }
  viewModel { AssistantHelpViewModel(get(), get(), get(), get()) }
}

/** The setup assistant, full screen over the app. */
@Composable
fun SetupDialog(viewModel: SetupViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  Dialog(
    onDismissRequest = { viewModel.onEvent(SetupUiEvent.Close) },
    // A full-screen page: only the close button and Escape or Back close it.
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    SetupContent(state, viewModel::onEvent, helpers = { AssistantHelpFor(it) })
  }
  if (state.confirming) ConfirmDialog(state, viewModel::onEvent)
}

/** The stateless setup screen, for [SetupDialog] and previews: one page of steps at a time. */
@Composable
fun SetupContent(
  state: SetupUiState,
  onEvent: (SetupUiEvent) -> Unit,
  modifier: Modifier = Modifier,
  /** Extra cards under a page's steps: the AI-assistant helpers. */
  helpers: @Composable (SetupPage) -> Unit = {},
) {
  val pages = SetupPage.entries
  val index = pages.indexOf(state.page)
  Surface(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(
          Modifier.align(Alignment.TopCenter)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(Res.string.setup_title),
              style = MaterialTheme.typography.headlineMedium,
              modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onEvent(SetupUiEvent.Close) }) {
              Icon(Icons.Rounded.Close, stringResource(Res.string.setup_close))
            }
          }
          Text(
            stringResource(Res.string.setup_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          PageTabs(state, onEvent)
          PageHeader(state.page, index, pages.size)
          if (state.page == SetupPage.AI) {
            state.profile?.takeIf { it.tier != ModelTier.UNKNOWN }?.let { AiPathCard(it) }
          }
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(vertical = 8.dp)) {
              state.pageSteps.forEachIndexed { i, step ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                StepRow(step, state, onEvent)
              }
            }
          }
          helpers(state.page)
          val fix = state.page.fixLabel
          if (fix != null && state.plan.isNotEmpty()) {
            FilledTonalButton(
              onClick = { onEvent(SetupUiEvent.FixPage) },
              shapes = ButtonDefaults.shapes(),
            ) {
              Text(stringResource(fix))
            }
          }
          if (state.allDone && index == pages.lastIndex) {
            Text(
              stringResource(Res.string.setup_all_done),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
          }
        }
      }
      HorizontalDivider()
      Row(
        Modifier.align(Alignment.CenterHorizontally)
          .widthIn(max = 640.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (index > 0) {
          TextButton(onClick = { onEvent(SetupUiEvent.GoTo(pages[index - 1])) }) {
            Text(stringResource(Res.string.setup_back))
          }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
          onClick = { onEvent(SetupUiEvent.CheckAgain) },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.setup_check_again))
        }
        Button(
          onClick = {
            if (index < pages.lastIndex) onEvent(SetupUiEvent.GoTo(pages[index + 1]))
            else onEvent(SetupUiEvent.Close)
          },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(
            stringResource(
              if (index < pages.lastIndex) Res.string.setup_next else Res.string.setup_finish
            )
          )
        }
      }
    }
  }
}

/** One chip per page, with a check mark once its steps are done. */
@Composable
private fun PageTabs(state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SetupPage.entries.forEach { page ->
      val done = state.pageDone(page)
      val name = stringResource(page.label)
      val described = stringResource(Res.string.setup_page_done, name)
      FilterChip(
        selected = page == state.page,
        onClick = { onEvent(SetupUiEvent.GoTo(page)) },
        label = { Text(name) },
        leadingIcon = {
          Icon(
            if (done) Icons.Rounded.CheckCircle else page.icon,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
          )
        },
        modifier = if (done) Modifier.semantics { contentDescription = described } else Modifier,
      )
    }
  }
}

@Composable
private fun PageHeader(page: SetupPage, index: Int, count: Int) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      stringResource(Res.string.setup_page_of, index + 1, count),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      stringResource(page.title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.semantics { heading() },
    )
    Text(
      stringResource(page.intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val SetupPage.label: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai
      SetupPage.SEARCHES -> Res.string.setup_page_searches
      SetupPage.PHONE -> Res.string.setup_page_phone
    }

private val SetupPage.title: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai_title
      SetupPage.SEARCHES -> Res.string.setup_page_searches_title
      SetupPage.PHONE -> Res.string.setup_page_phone_title
    }

private val SetupPage.intro: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai_intro
      SetupPage.SEARCHES -> Res.string.setup_page_searches_intro
      SetupPage.PHONE -> Res.string.setup_page_phone_intro
    }

private val SetupPage.icon: ImageVector
  get() =
    when (this) {
      SetupPage.AI -> Icons.Rounded.AutoAwesome
      SetupPage.SEARCHES -> Icons.Rounded.TravelExplore
      SetupPage.PHONE -> Icons.Rounded.PhoneAndroid
    }

/** The page's button for its automatic steps; `null` when it has none. */
private val SetupPage.fixLabel: StringResource?
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_fix_ai
      SetupPage.PHONE -> Res.string.setup_fix_phone
      SetupPage.SEARCHES -> null
    }

@Composable
private fun ConfirmDialog(state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(SetupUiEvent.Cancel) },
    title = { Text(stringResource(Res.string.setup_confirm_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.setup_confirm_intro))
        state.plan.forEach { item ->
          val text =
            when (item) {
              SetupPlanItem.PERMISSION -> stringResource(Res.string.setup_confirm_permission)
              SetupPlanItem.INSTALL_OLLAMA -> stringResource(Res.string.setup_confirm_install)
              SetupPlanItem.START_OLLAMA -> stringResource(Res.string.setup_confirm_start)
              SetupPlanItem.DOWNLOAD_MODEL ->
                stringResource(Res.string.setup_confirm_model, gb(state.modelBytes))
            }
          Text("•  $text")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onEvent(SetupUiEvent.Confirm) }) {
        Text(stringResource(Res.string.setup_confirm_go))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(SetupUiEvent.Cancel) }) {
        Text(stringResource(Res.string.setup_confirm_cancel))
      }
    },
  )
}

/** Offline or online first, from this computer's memory and graphics card. */
@Composable
private fun AiPathCard(profile: PcProfile) {
  val tier = profile.tier
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(Res.string.setup_ai_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        stringResource(
          Res.string.setup_ai_pc,
          wholeGb(profile.ramGb),
          wholeGb(profile.vramGb),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        stringResource(
          when (tier) {
            ModelTier.FULL -> Res.string.setup_ai_full
            ModelTier.SPLIT -> Res.string.setup_ai_split
            ModelTier.MEDIUM,
            ModelTier.SMALL -> Res.string.setup_ai_small
            ModelTier.ONLINE,
            ModelTier.UNKNOWN -> Res.string.setup_ai_online
          }
        ),
        style = MaterialTheme.typography.bodyLarge,
      )
      if (tier != ModelTier.FULL) {
        Text(
          stringResource(Res.string.setup_ai_keys),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FreeAiCatalog.TIERS.forEach { TierRow(it) }
      }
    }
  }
}

private fun wholeGb(gb: Double?): String =
  gb?.let { kotlin.math.round(it).toLong().toString() } ?: "?"
