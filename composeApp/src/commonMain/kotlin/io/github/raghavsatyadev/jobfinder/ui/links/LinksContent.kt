@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_close
import io.github.raghavsatyadev.jobfinder.resources.common_delete
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.common_save
import io.github.raghavsatyadev.jobfinder.resources.common_stale
import io.github.raghavsatyadev.jobfinder.resources.links_add
import io.github.raghavsatyadev.jobfinder.resources.links_added
import io.github.raghavsatyadev.jobfinder.resources.links_broken
import io.github.raghavsatyadev.jobfinder.resources.links_broken_explain
import io.github.raghavsatyadev.jobfinder.resources.links_broken_explain_title
import io.github.raghavsatyadev.jobfinder.resources.links_broken_summary
import io.github.raghavsatyadev.jobfinder.resources.links_delete
import io.github.raghavsatyadev.jobfinder.resources.links_delete_text
import io.github.raghavsatyadev.jobfinder.resources.links_delete_title
import io.github.raghavsatyadev.jobfinder.resources.links_edit
import io.github.raghavsatyadev.jobfinder.resources.links_edit_text
import io.github.raghavsatyadev.jobfinder.resources.links_edit_title
import io.github.raghavsatyadev.jobfinder.resources.links_empty_hint
import io.github.raghavsatyadev.jobfinder.resources.links_empty_title
import io.github.raghavsatyadev.jobfinder.resources.links_error_title_required
import io.github.raghavsatyadev.jobfinder.resources.links_field_title
import io.github.raghavsatyadev.jobfinder.resources.links_field_title_hint
import io.github.raghavsatyadev.jobfinder.resources.links_field_url
import io.github.raghavsatyadev.jobfinder.resources.links_last_checked
import io.github.raghavsatyadev.jobfinder.resources.links_open_in_browser
import io.github.raghavsatyadev.jobfinder.resources.links_scan_now
import io.github.raghavsatyadev.jobfinder.resources.links_scanning_all
import io.github.raghavsatyadev.jobfinder.resources.links_scanning_link
import io.github.raghavsatyadev.jobfinder.resources.links_scanning_progress
import io.github.raghavsatyadev.jobfinder.resources.links_supported_sites
import io.github.raghavsatyadev.jobfinder.resources.links_title
import io.github.raghavsatyadev.jobfinder.resources.links_unknown_site
import io.github.raghavsatyadev.jobfinder.resources.links_view_count
import io.github.raghavsatyadev.jobfinder.resources.links_why
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.ui.components.ADD_LINK_KEY
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.EmptyState
import io.github.raghavsatyadev.jobfinder.ui.components.ErrorState
import io.github.raghavsatyadev.jobfinder.ui.components.FabCorner
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.StaggeredEntrance
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContainer
import io.github.raghavsatyadev.jobfinder.ui.components.stale
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** The Searches screen for [state]; stateless, so previews and screenshots can show any state. */
@Composable
fun LinksContent(
  state: LinksUiState,
  onEvent: (LinksEvent) -> Unit,
  onAddLink: () -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val offline = LocalComputerOffline.current
  Scaffold(
    contentWindowInsets = screenInsets(),
    topBar = { TopAppBar(title = { Text(stringResource(Res.string.links_title)) }) },
    floatingActionButton = {
      if (!state.links.isNullOrEmpty() && !offline) {
        SmallExtendedFloatingActionButton(
          onClick = onAddLink,
          icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
          text = { Text(stringResource(Res.string.links_add)) },
          modifier = Modifier.sharedContainer(ADD_LINK_KEY, corner = FabCorner),
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    val links = state.links
    Column(Modifier.fillMaxSize().paddingAbove(padding)) {
      ScanBanner(state.scan)
      if (offline) OfflineNote()
      when {
        links == null && state.loadFailed ->
          ErrorState(
            message = state.loadErrorMessage ?: stringResource(Res.string.common_error_generic),
            onRetry = { onEvent(LinksEvent.Retry) },
          )
        links == null -> LoadingState()
        links.isEmpty() -> EmptyLinks(state.supportedSites, onAddLink)
        else -> {
          LinksToolbar(
            view = state.view,
            boards = state.boards,
            onChange = { onEvent(LinksEvent.ChangeView(it)) },
            modifier = Modifier.padding(top = 8.dp),
          )
          val groups = state.groups.orEmpty()
          if (groups.all { it.links.isEmpty() }) {
            NoMatchingLinks(onClear = { onEvent(LinksEvent.ChangeView(state.view.cleared())) })
          } else {
            LinksGrid(
              groups = groups,
              state = state,
              onEvent = onEvent,
              modifier = Modifier.stale(state.stale, stringResource(Res.string.common_stale)),
            )
          }
        }
      }
    }
  }
  Dialogs(state, onEvent)
}

/** The scan of every search, while it runs: a wavy bar with its progress. */
@Composable
private fun ScanBanner(scan: ScanState) {
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(
    visible = scan.scanning,
    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
    exit = fadeOut(motion.fastEffectsSpec()),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer,
      shape = MaterialTheme.shapes.large,
      modifier =
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics {
          liveRegion = LiveRegionMode.Polite
        },
    ) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(Res.string.links_scanning_all),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        val total = scan.linksTotal
        if (total > 0) {
          Text(
            stringResource(Res.string.links_scanning_progress, scan.linksDone, total),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        WavyProgress(progress = if (total > 0) scan.linksDone.toFloat() / total else null)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyLinks(sites: ImmutableList<Site>, onAddLink: () -> Unit) {
  // The bottom padding lets the last boards scroll out from under the floating tab bar.
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = scrollBottom())
  ) {
    EmptyState(
      icon = Icons.Rounded.TravelExplore,
      title = stringResource(Res.string.links_empty_title),
      hint = stringResource(Res.string.links_empty_hint),
      action = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.widthIn(max = 560.dp),
        ) {
          Button(
            onClick = onAddLink,
            enabled = !LocalComputerOffline.current,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.sharedContainer(ADD_LINK_KEY, corner = FabCorner),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.links_add))
          }
          if (sites.isNotEmpty()) {
            Text(
              stringResource(Res.string.links_supported_sites),
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.semantics { heading() },
            )
            SiteChips(sites)
          }
        }
      },
    )
  }
}

/** The supported boards, each with its logo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SiteChips(sites: ImmutableList<Site>, modifier: Modifier = Modifier) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    sites.forEach { site ->
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Row(
          Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SiteLogo(site, size = 24.dp)
          Text(site.name, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun LinksGrid(
  groups: List<LinkGroup>,
  state: LinksUiState,
  onEvent: (LinksEvent) -> Unit,
  modifier: Modifier = Modifier,
) {
  // The cascade plays for the cards of the first open only, never on scroll; previews and
  // screenshots show a single frame, so there the cards are simply there.
  val inspection = LocalInspectionMode.current
  val firstOpen = remember {
    if (inspection) emptySet() else groups.flatMap { g -> g.links.map { it.id } }.toSet()
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 340.dp),
    contentPadding =
      PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + scrollBottom()),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    groups.forEach { group ->
      if (group.siteId != null) {
        item(
          key = "board-${group.siteId}",
          span = { GridItemSpan(maxLineSpan) },
          contentType = "board",
        ) {
          BoardHeader(group, Modifier.animateItem())
        }
      }
      itemsIndexed(group.links, key = { _, link -> link.id }, contentType = { _, _ -> "link" }) {
        index,
        link ->
        StaggeredEntrance(
          index = index,
          play = link.id in firstOpen,
          modifier = Modifier.animateItem(),
        ) {
          LinkCard(
            link = link,
            site = state.sites[link.siteId],
            now = state.now,
            scanning = link.id in state.scanningLinkIds,
            canOpenPage = state.canOpenPages,
            onEvent = onEvent,
          )
        }
      }
    }
  }
}

/** A board's name and logo above its searches, when the list is grouped. */
@Composable
private fun BoardHeader(group: LinkGroup, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SiteLogo(group.site, size = 24.dp)
    Text(
      group.site?.name ?: stringResource(Res.string.links_unknown_site),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      stringResource(Res.string.links_view_count, group.links.size),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCard(
  link: Link,
  site: Site?,
  now: Long,
  scanning: Boolean,
  canOpenPage: Boolean,
  onEvent: (LinksEvent) -> Unit,
) {
  val broken = link.isBroken
  val colors = MaterialTheme.colorScheme
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    border = if (broken) BorderStroke(2.dp, colors.error) else null,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SiteLogo(site)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            site?.name ?: stringResource(Res.string.links_unknown_site),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
          )
          Text(
            link.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            // Always two lines tall, so every card in the grid is the same height.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (broken) {
          Spacer(Modifier.width(8.dp))
          StatusPill(stringResource(Res.string.links_broken), StatusTone.Negative)
        }
      }
      Text(
        LinkUrls.host(link.url)?.let(LinkUrls::siteHost) ?: link.url,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          stringResource(Res.string.links_last_checked, relativeTime(link.lastScrapedAt, now)),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
        Text(
          stringResource(Res.string.links_added, relativeTime(link.createdAt, now)),
          style = MaterialTheme.typography.bodySmall,
          color = colors.onSurfaceVariant,
        )
      }
      if (scanning) {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
          Text(
            stringResource(Res.string.links_scanning_link),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
          )
          WavyProgress(progress = null)
        }
      }
      if (broken) BrokenWarning(link, scanning, canOpenPage, onEvent)
      CardActions(link, scanning, canOpenPage && !broken, onEvent)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrokenWarning(
  link: Link,
  scanning: Boolean,
  canOpenPage: Boolean,
  onEvent: (LinksEvent) -> Unit,
) {
  // Text buttons on the warning use its own content colour: primary is too faint on it.
  val onWarning =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
  Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          stringResource(Res.string.links_broken_summary, link.scrapeFailureCount),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.weight(1f),
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        TextButton(onClick = { onEvent(LinksEvent.Explain(link)) }, colors = onWarning) {
          Text(stringResource(Res.string.links_why))
        }
        if (canOpenPage) {
          TextButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }, colors = onWarning) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.links_open_in_browser))
          }
        }
        FilledTonalButton(
          onClick = { onEvent(LinksEvent.ScanLink(link)) },
          enabled = !scanning && !LocalComputerOffline.current,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.links_scan_now))
        }
      }
    }
  }
}

@Composable
private fun CardActions(
  link: Link,
  scanning: Boolean,
  showOpenPage: Boolean,
  onEvent: (LinksEvent) -> Unit,
) {
  // Scans and changes need the computer.
  val online = !LocalComputerOffline.current
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showOpenPage) {
      IconButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }) {
        Icon(
          Icons.Rounded.OpenInBrowser,
          contentDescription = stringResource(Res.string.links_open_in_browser),
        )
      }
    }
    if (!link.isBroken) {
      IconButton(onClick = { onEvent(LinksEvent.ScanLink(link)) }, enabled = !scanning && online) {
        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(Res.string.links_scan_now))
      }
    }
    IconButton(onClick = { onEvent(LinksEvent.StartEdit(link)) }, enabled = online) {
      Icon(Icons.Rounded.Edit, contentDescription = stringResource(Res.string.links_edit))
    }
    IconButton(
      onClick = { onEvent(LinksEvent.AskDelete(link)) },
      enabled = online,
      colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Icon(
        Icons.Rounded.DeleteOutline,
        contentDescription = stringResource(Res.string.links_delete),
      )
    }
  }
}

@Composable
private fun Dialogs(state: LinksUiState, onEvent: (LinksEvent) -> Unit) {
  state.edit?.let { EditLinkDialog(it, onEvent) }
  state.pendingDelete?.let { link ->
    ConfirmDialog(
      icon = Icons.Rounded.DeleteOutline,
      title = stringResource(Res.string.links_delete_title),
      text = stringResource(Res.string.links_delete_text, link.title),
      confirmLabel = stringResource(Res.string.common_delete),
      onConfirm = { onEvent(LinksEvent.ConfirmDelete) },
      onDismiss = { onEvent(LinksEvent.CancelDelete) },
    )
  }
  state.explaining?.let { link ->
    AlertDialog(
      onDismissRequest = { onEvent(LinksEvent.CloseExplain) },
      icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
      title = { Text(stringResource(Res.string.links_broken_explain_title)) },
      text = {
        Text(stringResource(Res.string.links_broken_explain, ServerEvent.BROKEN_LINK_THRESHOLD))
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (state.canOpenPages) {
            TextButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }) {
              Text(stringResource(Res.string.links_open_in_browser))
            }
          }
          TextButton(onClick = { onEvent(LinksEvent.ScanLink(link)) }) {
            Text(stringResource(Res.string.links_scan_now))
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { onEvent(LinksEvent.CloseExplain) }) {
          Text(stringResource(Res.string.common_close))
        }
      },
    )
  }
}

@Composable
private fun EditLinkDialog(edit: LinkEditState, onEvent: (LinksEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { if (!edit.saving) onEvent(LinksEvent.CancelEdit) },
    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
    title = { Text(stringResource(Res.string.links_edit_title)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(Res.string.links_edit_text),
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = edit.title,
          onValueChange = { onEvent(LinksEvent.EditTitle(it)) },
          label = { Text(stringResource(Res.string.links_field_title)) },
          placeholder = { Text(stringResource(Res.string.links_field_title_hint)) },
          isError = edit.titleMissing,
          supportingText =
            if (edit.titleMissing) {
              { Text(stringResource(Res.string.links_error_title_required)) }
            } else null,
          singleLine = true,
          enabled = !edit.saving,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = edit.url,
          onValueChange = { onEvent(LinksEvent.EditUrl(it)) },
          label = { Text(stringResource(Res.string.links_field_url)) },
          isError = edit.urlError != null,
          supportingText = edit.urlError?.let { error -> { Text(urlErrorText(error)) } },
          singleLine = true,
          enabled = !edit.saving,
          keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
          modifier = Modifier.fillMaxWidth(),
        )
        if (edit.saveFailed) {
          Text(
            edit.saveErrorMessage ?: stringResource(Res.string.common_error_generic),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onEvent(LinksEvent.SaveEdit) },
        enabled = !edit.saving,
        shapes = ButtonDefaults.shapes(),
      ) {
        if (edit.saving) {
          LoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
          Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(Res.string.common_save))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(LinksEvent.CancelEdit) }, enabled = !edit.saving) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}
