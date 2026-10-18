package io.github.raghavsatyadev.jobfinder.server.parsing

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * The common description parser (old `extractCommonDomElements` + `turndown(innerHTML)`): the first
 * of [selectors] that matches an element with non-blank text is the description container; its
 * inner HTML, after [rewriteHtml], is converted to Markdown.
 *
 * Most boards need only their selectors (old `SITE_PROVIDER_QUERY_SELECTORS`):
 * ```
 * SelectorDescriptionParser(listOf("#jobDescription", ".job-description"))
 * ```
 */
open class SelectorDescriptionParser(
  private val selectors: List<String>,
  private val rewriteHtml: (String) -> String = { it },
) : JobDescriptionParser {

  override fun parse(html: String): JobDescriptionUpdates {
    val container = findContainer(Dom.parse(html)) ?: return JobDescriptionUpdates.NONE
    return JobDescriptionUpdates(
      description = HtmlToMarkdown.convert(rewriteHtml(container.innerHtml()))
    )
  }

  /** The description container, or `null`. Subclasses that need the whole page can call this. */
  fun findContainer(document: Document): Element? = selectors.firstNotNullOfOrNull { selector ->
    document.qs(selector)?.takeIf { it.domText().jsTrim().isNotEmpty() }
  }
}
