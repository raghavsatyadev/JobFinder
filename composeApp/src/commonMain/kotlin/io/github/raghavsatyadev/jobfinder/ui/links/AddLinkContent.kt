@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.links_add_another
import io.github.raghavsatyadev.jobfinder.resources.links_add_browse
import io.github.raghavsatyadev.jobfinder.resources.links_add_browse_again
import io.github.raghavsatyadev.jobfinder.resources.links_add_browse_intro
import io.github.raghavsatyadev.jobfinder.resources.links_add_conflict_hint
import io.github.raghavsatyadev.jobfinder.resources.links_add_conflict_title
import io.github.raghavsatyadev.jobfinder.resources.links_add_done
import io.github.raghavsatyadev.jobfinder.resources.links_add_done_jobs
import io.github.raghavsatyadev.jobfinder.resources.links_add_done_no_jobs
import io.github.raghavsatyadev.jobfinder.resources.links_add_done_parse_failed
import io.github.raghavsatyadev.jobfinder.resources.links_add_done_title
import io.github.raghavsatyadev.jobfinder.resources.links_add_intro
import io.github.raghavsatyadev.jobfinder.resources.links_add_save
import io.github.raghavsatyadev.jobfinder.resources.links_add_save_anyway
import io.github.raghavsatyadev.jobfinder.resources.links_add_saving
import io.github.raghavsatyadev.jobfinder.resources.links_add_title
import io.github.raghavsatyadev.jobfinder.resources.links_error_title_required
import io.github.raghavsatyadev.jobfinder.resources.links_field_site_name
import io.github.raghavsatyadev.jobfinder.resources.links_field_site_name_hint
import io.github.raghavsatyadev.jobfinder.resources.links_field_title
import io.github.raghavsatyadev.jobfinder.resources.links_field_title_hint
import io.github.raghavsatyadev.jobfinder.resources.links_field_url
import io.github.raghavsatyadev.jobfinder.resources.links_field_url_hint
import io.github.raghavsatyadev.jobfinder.resources.links_supported_sites
import io.github.raghavsatyadev.jobfinder.resources.nav_back
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.ui.components.ADD_LINK_KEY
import io.github.raghavsatyadev.jobfinder.ui.components.DriftingShape
import io.github.raghavsatyadev.jobfinder.ui.components.FabCorner
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContainer
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The add-search screen for [state]. On desktop ([desktop] set) the user can browse to the search
 * page in the scanner's browser; everywhere the URL can be pasted. Stateless, for previews.
 */
@Composable
fun AddLinkContent(
  state: AddLinkUiState,
  onEvent: (AddLinkEvent) -> Unit,
  onDone: () -> Unit,
  desktop: DesktopFeatures? = null,
) {
  if (state.browsing && desktop != null) {
    desktop.AddLinkBrowser(
      onPicked = { url, title -> onEvent(AddLinkEvent.Picked(url, title)) },
      onCancel = { onEvent(AddLinkEvent.CancelBrowse) },
    )
    return
  }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.links_add_title)) },
        navigationIcon = {
          IconButton(onClick = onDone) {
            Icon(
              Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(Res.string.nav_back),
            )
          }
        },
      )
    },
    contentWindowInsets = screenInsets(),
    modifier = Modifier.sharedContainer(ADD_LINK_KEY, fullScreen = true, corner = FabCorner),
  ) { padding ->
    Box(
      Modifier.fillMaxSize()
        .paddingAbove(padding)
        .verticalScroll(rememberScrollState())
        .padding(bottom = scrollBottom()),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val created = state.created
        if (created != null) {
          Created(created, onDone = onDone, onAddAnother = { onEvent(AddLinkEvent.AddAnother) })
        } else {
          AddLinkForm(state, onEvent, canBrowse = desktop != null)
        }
      }
    }
  }
}

@Composable
private fun AddLinkForm(
  state: AddLinkUiState,
  onEvent: (AddLinkEvent) -> Unit,
  canBrowse: Boolean,
) {
  Text(
    stringResource(
      if (canBrowse) Res.string.links_add_browse_intro else Res.string.links_add_intro
    ),
    style = MaterialTheme.typography.bodyLarge,
  )
  if (canBrowse) {
    FilledTonalButton(
      onClick = { onEvent(AddLinkEvent.Browse) },
      enabled = !state.saving,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Rounded.TravelExplore, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(
        stringResource(
          if (state.url.isBlank()) Res.string.links_add_browse
          else Res.string.links_add_browse_again
        )
      )
    }
  } else if (state.sites.isNotEmpty()) {
    Text(
      stringResource(Res.string.links_supported_sites),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.semantics { heading() },
    )
    SiteChips(state.sites)
  }
  OutlinedTextField(
    value = state.url,
    onValueChange = { onEvent(AddLinkEvent.UrlChanged(it)) },
    label = { Text(stringResource(Res.string.links_field_url)) },
    placeholder = { Text(stringResource(Res.string.links_field_url_hint)) },
    isError = state.urlError != null,
    supportingText = state.urlError?.let { error -> { Text(urlErrorText(error)) } },
    enabled = !state.saving,
    minLines = 1,
    maxLines = 4,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
    modifier = Modifier.fillMaxWidth(),
  )
  val customBoard = state.customBoard
  AnimatedVisibility(
    visible = customBoard != null,
    enter =
      expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
  ) {
    OutlinedTextField(
      value = state.siteName,
      onValueChange = { onEvent(AddLinkEvent.SiteNameChanged(it)) },
      label = { Text(stringResource(Res.string.links_field_site_name)) },
      placeholder = { Text(customBoard.orEmpty()) },
      supportingText = {
        Text(stringResource(Res.string.links_field_site_name_hint, customBoard.orEmpty()))
      },
      enabled = !state.saving,
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth(),
    )
  }
  OutlinedTextField(
    value = state.title,
    onValueChange = { onEvent(AddLinkEvent.TitleChanged(it)) },
    label = { Text(stringResource(Res.string.links_field_title)) },
    placeholder = { Text(stringResource(Res.string.links_field_title_hint)) },
    isError = state.titleMissing,
    supportingText =
      if (state.titleMissing) {
        { Text(stringResource(Res.string.links_error_title_required)) }
      } else null,
    enabled = !state.saving,
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { onEvent(AddLinkEvent.Save) }),
    modifier = Modifier.fillMaxWidth(),
  )
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(
    visible = state.conflictMessage != null,
    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
    exit = fadeOut(motion.fastEffectsSpec()),
  ) {
    Notice(
      title = stringResource(Res.string.links_add_conflict_title),
      text =
        listOfNotNull(
            state.conflictMessage?.takeIf { it.isNotBlank() },
            stringResource(Res.string.links_add_conflict_hint),
          )
          .joinToString("\n\n"),
    )
  }
  if (state.saveFailed) {
    Text(
      state.saveErrorMessage ?: stringResource(Res.string.common_error_generic),
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
  }
  SaveButton(
    saving = state.saving,
    anyway = state.conflictMessage != null,
    onClick = { onEvent(AddLinkEvent.Save) },
  )
}

/** A warning block: the page showed no job list. */
@Composable
private fun Notice(title: String, text: String) {
  Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
  ) {
    Row(Modifier.padding(16.dp)) {
      Icon(Icons.Rounded.WarningAmber, contentDescription = null)
      Spacer(Modifier.width(12.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

/** The screen's one action: full width and medium height. */
@Composable
private fun SaveButton(saving: Boolean, anyway: Boolean, onClick: () -> Unit) {
  val height = ButtonDefaults.MediumContainerHeight
  Button(
    onClick = onClick,
    enabled = !saving,
    shapes = ButtonDefaults.shapesFor(height),
    contentPadding = ButtonDefaults.contentPaddingFor(height),
    modifier = Modifier.fillMaxWidth().heightIn(min = height),
  ) {
    if (saving) {
      LoadingIndicator(Modifier.size(24.dp))
      Spacer(Modifier.width(12.dp))
    }
    Text(
      stringResource(
        when {
          saving -> Res.string.links_add_saving
          anyway -> Res.string.links_add_save_anyway
          else -> Res.string.links_add_save
        }
      ),
      style = ButtonDefaults.textStyleFor(height),
    )
  }
}

/** The hero moment of the screen: the search is saved, with the jobs found on its page. */
@Composable
private fun Created(created: CreateLinkResponse, onDone: () -> Unit, onAddAnother: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(top = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      DriftingShape(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialShapes.Sunny,
        size = 160.dp,
      )
      Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(56.dp),
      )
    }
    Text(
      stringResource(Res.string.links_add_done_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      created.link.title,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
    )
    val count = created.newJobIds.size
    Text(
      when {
        created.parseFailed -> stringResource(Res.string.links_add_done_parse_failed)
        count == 0 -> stringResource(Res.string.links_add_done_no_jobs)
        else -> pluralStringResource(Res.plurals.links_add_done_jobs, count, count)
      },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick = onAddAnother, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.links_add_another))
      }
      Button(onClick = onDone, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.links_add_done))
      }
    }
  }
}
