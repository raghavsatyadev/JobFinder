@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_a_apply
import io.github.raghavsatyadev.jobfinder.resources.help_a_away
import io.github.raghavsatyadev.jobfinder.resources.help_a_desktop
import io.github.raghavsatyadev.jobfinder.resources.help_a_filter
import io.github.raghavsatyadev.jobfinder.resources.help_a_free_ai
import io.github.raghavsatyadev.jobfinder.resources.help_a_key_gemini
import io.github.raghavsatyadev.jobfinder.resources.help_a_key_groq
import io.github.raghavsatyadev.jobfinder.resources.help_a_key_nvidia
import io.github.raghavsatyadev.jobfinder.resources.help_a_key_openrouter
import io.github.raghavsatyadev.jobfinder.resources.help_a_keyboard
import io.github.raghavsatyadev.jobfinder.resources.help_a_resume_pdf
import io.github.raghavsatyadev.jobfinder.resources.help_a_sleep
import io.github.raghavsatyadev.jobfinder.resources.help_collapse
import io.github.raghavsatyadev.jobfinder.resources.help_expand
import io.github.raghavsatyadev.jobfinder.resources.help_faq_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_add_computer
import io.github.raghavsatyadev.jobfinder.resources.help_guide_check_connection
import io.github.raghavsatyadev.jobfinder.resources.help_guides_title
import io.github.raghavsatyadev.jobfinder.resources.help_no_results
import io.github.raghavsatyadev.jobfinder.resources.help_q_apply
import io.github.raghavsatyadev.jobfinder.resources.help_q_away
import io.github.raghavsatyadev.jobfinder.resources.help_q_desktop
import io.github.raghavsatyadev.jobfinder.resources.help_q_filter
import io.github.raghavsatyadev.jobfinder.resources.help_q_free_ai
import io.github.raghavsatyadev.jobfinder.resources.help_q_key_gemini
import io.github.raghavsatyadev.jobfinder.resources.help_q_key_groq
import io.github.raghavsatyadev.jobfinder.resources.help_q_key_nvidia
import io.github.raghavsatyadev.jobfinder.resources.help_q_key_openrouter
import io.github.raghavsatyadev.jobfinder.resources.help_q_keyboard
import io.github.raghavsatyadev.jobfinder.resources.help_q_resume_pdf
import io.github.raghavsatyadev.jobfinder.resources.help_q_sleep
import io.github.raghavsatyadev.jobfinder.resources.help_search
import io.github.raghavsatyadev.jobfinder.resources.help_search_clear
import io.github.raghavsatyadev.jobfinder.resources.help_setup_open
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_archive
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_archive_keys
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_delete
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_delete_keys
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_select
import io.github.raghavsatyadev.jobfinder.resources.help_shortcut_select_keys
import io.github.raghavsatyadev.jobfinder.resources.help_shortcuts_title
import io.github.raghavsatyadev.jobfinder.resources.help_title
import io.github.raghavsatyadev.jobfinder.resources.nav_back
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

private data class Faq(val question: StringResource, val answer: StringResource)

private data class Shortcut(val keys: StringResource, val action: StringResource)

private val FAQS: ImmutableList<Faq> =
  persistentListOf(
    Faq(Res.string.help_q_desktop, Res.string.help_a_desktop),
    Faq(Res.string.help_q_apply, Res.string.help_a_apply),
    Faq(Res.string.help_q_filter, Res.string.help_a_filter),
    Faq(Res.string.help_q_sleep, Res.string.help_a_sleep),
    Faq(Res.string.help_q_away, Res.string.help_a_away),
    Faq(Res.string.help_q_keyboard, Res.string.help_a_keyboard),
    Faq(Res.string.help_q_free_ai, Res.string.help_a_free_ai),
    Faq(Res.string.help_q_resume_pdf, Res.string.help_a_resume_pdf),
    Faq(Res.string.help_q_key_nvidia, Res.string.help_a_key_nvidia),
    Faq(Res.string.help_q_key_groq, Res.string.help_a_key_groq),
    Faq(Res.string.help_q_key_openrouter, Res.string.help_a_key_openrouter),
    Faq(Res.string.help_q_key_gemini, Res.string.help_a_key_gemini),
  )

private val SHORTCUTS: ImmutableList<Shortcut> =
  persistentListOf(
    Shortcut(Res.string.help_shortcut_select_keys, Res.string.help_shortcut_select),
    Shortcut(Res.string.help_shortcut_archive_keys, Res.string.help_shortcut_archive),
    Shortcut(Res.string.help_shortcut_delete_keys, Res.string.help_shortcut_delete),
  )

/**
 * The app's Help as Markdown: the guides, the questions and answers and the shortcuts, in the app's
 * language. AI agents read it through the MCP server.
 */
suspend fun helpMarkdown(): String = buildString {
  append("# ").append(getString(Res.string.help_title)).append("\n\n")
  append("## ").append(getString(Res.string.help_guides_title)).append("\n\n")
  append(guidesMarkdown())
  append("## ").append(getString(Res.string.help_faq_title)).append("\n\n")
  for (faq in FAQS) {
    append("### ").append(getString(faq.question)).append("\n\n")
    append(getString(faq.answer)).append("\n\n")
  }
  append("## ").append(getString(Res.string.help_shortcuts_title)).append("\n\n")
  for (shortcut in SHORTCUTS) {
    append("- ").append(getString(shortcut.keys)).append(": ").append(getString(shortcut.action))
    append('\n')
  }
}

/**
 * How the app works: the illustrated guides, questions and answers, and the keyboard shortcuts. The
 * guide for the device the app runs on comes first, with buttons that work there.
 */
@Composable
fun HelpScreen(onBack: (() -> Unit)? = null) {
  val koin = getKoin()
  val setup = remember(koin) { koin.getOrNull<SetupAssistant>() }
  val pairing = remember(koin) { koin.getOrNull<PhonePairing>() }
  val doctor = remember(koin) { koin.getOrNull<ConnectionDoctor>() }
  val actions =
    remember(setup, pairing, doctor) {
      GuideActions(
        openSetup = setup?.let { GuideAction(Res.string.help_setup_open) { it.show(true) } },
        addComputer =
          pairing?.let {
            GuideAction(Res.string.help_guide_add_computer) { it.showPairingScreen(true) }
          },
        checkConnection =
          doctor?.let { GuideAction(Res.string.help_guide_check_connection) { it.show(true) } },
      )
    }
  HelpContent(
    if (pairing != null) GuideDevice.PHONE else GuideDevice.COMPUTER,
    actions,
    onBack = onBack,
  )
}

/**
 * The help page. A search keeps only the guides, answers and shortcuts that contain every word of
 * it, opened; [onBack] adds a back arrow (Help opens from Settings).
 */
@Composable
fun HelpContent(
  device: GuideDevice,
  actions: GuideActions,
  /** A guide to open at once, when Help opens for it; otherwise everything starts closed. */
  firstOpen: GuideId? = null,
  onBack: (() -> Unit)? = null,
  initialQuery: String = "",
) {
  // Everything starts closed: the headings are the overview.
  var open by rememberSaveable { mutableStateOf(-1) }
  var openGuide by rememberSaveable { mutableStateOf<GuideId?>(firstOpen) }
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  val searching = query.isNotBlank()
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.help_title)) },
        navigationIcon = {
          if (onBack != null) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
              Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.nav_back),
              )
            }
          }
        },
      )
    },
    contentWindowInsets = screenInsets(),
  ) { padding ->
    Box(Modifier.fillMaxSize().paddingAbove(padding).verticalScroll(rememberScrollState())) {
      Column(
        Modifier.align(Alignment.TopCenter)
          .widthIn(max = 760.dp)
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + scrollBottom()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        HelpSearchField(query, onChange = { query = it })
        val guides = matchingGuides(device, query)
        val faqs = FAQS.withIndex().filter { (_, faq) -> faqMatches(query, faq) }
        val shortcuts = SHORTCUTS.filter {
          helpMatches(query, it.keys) || helpMatches(query, it.action)
        }
        if (guides.isNotEmpty()) {
          SectionTitle(stringResource(Res.string.help_guides_title))
          Guides(
            device,
            actions,
            openGuide,
            onToggle = { openGuide = if (openGuide == it) null else it },
            shown = guides,
            expandAll = searching,
          )
        }
        if (faqs.isNotEmpty()) {
          SectionTitle(stringResource(Res.string.help_faq_title))
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            faqs.forEachIndexed { position, (index, faq) ->
              if (position > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
              // While searching every match shows its answer.
              FaqItem(faq, expanded = searching || open == index) {
                open = if (open == index) -1 else index
              }
            }
          }
        }
        if (shortcuts.isNotEmpty()) {
          SectionTitle(stringResource(Res.string.help_shortcuts_title))
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              shortcuts.forEach { ShortcutRow(it) }
            }
          }
        }
        if (guides.isEmpty() && faqs.isEmpty() && shortcuts.isEmpty()) {
          Text(
            stringResource(Res.string.help_no_results, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      }
    }
  }
}

/** A pill search field with a clear button. */
@Composable
private fun HelpSearchField(query: String, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = query,
    onValueChange = onChange,
    placeholder = { Text(stringResource(Res.string.help_search)) },
    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onChange("") }, shapes = IconButtonDefaults.shapes()) {
          Icon(Icons.Rounded.Close, stringResource(Res.string.help_search_clear))
        }
      }
    },
    singleLine = true,
    shape = CircleShape,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = Modifier.fillMaxWidth(),
  )
}

/** Lower case without punctuation, so "wifi" finds "Wi-Fi" and "set up" finds "set-up". */
private fun plain(text: String): String =
  text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }

private fun words(query: String): List<String> =
  query.lowercase().split(' ').map { plain(it) }.filter { it.isNotEmpty() }

private fun containsAll(text: String, words: List<String>): Boolean {
  val value = plain(text.replace('-', ' '))
  val joined = value.replace(" ", "")
  return words.all { value.contains(it) || joined.contains(it) }
}

/** Whether [text] contains every word of [query], ignoring case; any text for a blank one. */
@Composable
internal fun helpMatches(query: String, text: StringResource): Boolean {
  val words = words(query)
  if (words.isEmpty()) return true
  return containsAll(stringResource(text), words)
}

/** Question and answer together, so a word from each still matches. */
@Composable
private fun faqMatches(query: String, faq: Faq): Boolean {
  val words = words(query)
  if (words.isEmpty()) return true
  val text = stringResource(faq.question) + " " + stringResource(faq.answer)
  return containsAll(text, words)
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
  )
}

@Composable
private fun FaqItem(faq: Faq, expanded: Boolean, onToggle: () -> Unit) {
  val animate = animationsEnabled()
  val motion = MaterialTheme.motionScheme
  val rotation by
    animateFloatAsState(
      if (expanded) 180f else 0f,
      if (animate) motion.fastSpatialSpec() else snap(),
      label = "faq-chevron",
    )
  val state = stringResource(if (expanded) Res.string.help_collapse else Res.string.help_expand)
  Column {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = state }
        .padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        stringResource(faq.question),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      Icon(
        Icons.Rounded.ExpandMore,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = rotation },
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter =
        if (animate)
          expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        else expandVertically(snap()),
      exit =
        if (animate) shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        else shrinkVertically(snap()),
    ) {
      Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(faq.answer),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (faq.question == Res.string.help_q_keyboard) {
          SHORTCUTS.forEach { ShortcutRow(it) }
        }
      }
    }
  }
}

@Composable
private fun ShortcutRow(shortcut: Shortcut) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      shape = MaterialTheme.shapes.small,
    ) {
      Text(
        stringResource(shortcut.keys),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      )
    }
    Text(stringResource(shortcut.action), style = MaterialTheme.typography.bodyMedium)
  }
}
