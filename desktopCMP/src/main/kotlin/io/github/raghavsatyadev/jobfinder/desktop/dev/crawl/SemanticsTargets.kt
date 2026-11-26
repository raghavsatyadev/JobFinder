package io.github.raghavsatyadev.jobfinder.desktop.dev.crawl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/** What the crawler does with a target. */
enum class Kind {
  /** A button, chip, card or anything else with a click action. */
  CLICK,
  /** A text field: click into it, then type. */
  TYPE,
  /** Plain text: a press on it must not break anything (text selection lives here). */
  TAP,
  /** A list or page that can scroll further down. */
  SCROLL,
}

/** One thing on screen; [key] finds it again after a replay ("label#n" for repeats). */
data class Target(val key: String, val kind: Kind, val center: Offset)

/** The targets of the top layer (a dialog or menu when one is open) and a fingerprint of them. */
data class Screen(val layers: Int, val targets: List<Target>) {
  /** Same screen, same state: the layer count and what can be clicked or typed into. */
  val fingerprint: String =
    "$layers|" +
      targets.filter { it.kind == Kind.CLICK || it.kind == Kind.TYPE }.joinToString(";") { it.key }

  fun find(key: String): Target? = targets.firstOrNull { it.key == key }

  companion object {
    val EMPTY = Screen(0, emptyList())
  }
}

/** Reads targets from the semantics trees of a scene. */
object SemanticsTargets {
  /** Items read from each list: the rest of a list repeats the same buttons. */
  private const val LIST_ITEMS = 3
  private const val MAX_TAPS = 25
  private const val MAX_LABEL = 60

  fun read(owners: Collection<SemanticsOwner>, width: Int, height: Int): Screen {
    val screen = Rect(0f, 0f, width.toFloat(), height.toFloat())
    // Later owners are layers above earlier ones: dialogs, menus, tooltips. The top one that can be
    // acted on is where the user's next click lands; the scrim keeps clicks off the rest.
    val layers = owners.map { owner ->
      val found = mutableListOf<Pair<String, Pair<Kind, Offset>>>()
      walk(owner.rootSemanticsNode, screen, found)
      found
    }
    val top =
      layers.lastOrNull { found -> found.any { it.second.first != Kind.TAP } }
        ?: return Screen.EMPTY
    val seen = mutableMapOf<String, Int>()
    var taps = 0
    val targets = top.mapNotNull { (label, kindAt) ->
      val (kind, at) = kindAt
      if (kind == Kind.TAP && ++taps > MAX_TAPS) return@mapNotNull null
      val base = "${kind.name.lowercase()}:$label"
      val n = seen.merge(base, 1, Int::plus)!!
      Target(if (n == 1) base else "$base#$n", kind, at)
    }
    return Screen(owners.size, targets)
  }

  private fun walk(
    node: SemanticsNode,
    clip: Rect,
    out: MutableList<Pair<String, Pair<Kind, Offset>>>,
  ) {
    val config = node.config
    val bounds = runCatching { node.boundsInWindow }.getOrNull() ?: return
    val visible = bounds.intersect(clip)
    val shown = visible.width >= 2f && visible.height >= 2f
    val disabled = config.contains(SemanticsProperties.Disabled)
    val label = label(node)
    if (shown && !disabled) {
      val kind =
        when {
          config.contains(SemanticsActions.SetText) -> Kind.TYPE
          config.contains(SemanticsActions.OnClick) -> Kind.CLICK
          config.contains(SemanticsProperties.Text) && label.isNotBlank() -> Kind.TAP
          else -> null
        }
      if (kind != null) out += label to (kind to visible.center)
      val range = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
      if (range != null && range.value() < range.maxValue() - 1f && visible.height > 80f) {
        out += "scroll:$label" to (Kind.SCROLL to visible.center)
      }
    }
    val scrolls =
      config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
        config.contains(SemanticsProperties.HorizontalScrollAxisRange)
    val childClip = if (scrolls) clip.intersect(bounds) else clip
    val children =
      if (config.contains(SemanticsProperties.CollectionInfo)) node.children.take(LIST_ITEMS)
      else node.children
    for (child in children) walk(child, childClip, out)
  }

  private fun label(node: SemanticsNode): String {
    val c = node.config
    val parts = buildList {
      c.getOrNull(SemanticsProperties.TestTag)?.let { add("#$it") }
      c.getOrNull(SemanticsProperties.Role)?.let { add(it.toString()) }
      c.getOrNull(SemanticsProperties.ContentDescription)?.let { addAll(it) }
      // A text field's own text changes as the crawler types; its label does not.
      if (!c.contains(SemanticsActions.SetText)) {
        c.getOrNull(SemanticsProperties.Text)?.let { t -> addAll(t.map { it.text }) }
      }
    }
    return parts
      .joinToString(" ")
      .replace(Regex("\\s+"), " ")
      .replace(Regex("\\d+"), "0")
      .take(MAX_LABEL)
  }
}
