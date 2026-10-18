package io.github.raghavsatyadev.jobfinder.server.parsing

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import org.jsoup.select.QueryParser

/**
 * Browser-DOM semantics on top of jsoup, so the ported parsers read like the old deno-dom code and
 * behave the same.
 *
 * jsoup differs from the DOM in ways that matter to the parsers:
 * - `Element.select` includes the element itself and matches ancestors only up to that element;
 *   `querySelector` never returns the element itself and matches ancestors anywhere in the
 *   document. [qs] and [qsa] follow the DOM.
 * - `Element.text()` normalises whitespace and skips `<script>`/`<style>` data; DOM `textContent`
 *   does neither. [domText] follows the DOM.
 * - Kotlin `trim()` and JS `trim()` strip different characters. [jsTrim] follows JS.
 *
 * `:scope` deliberately follows deno-dom 0.1.43 (the old backend), not the CSS spec: its nwsapi
 * engine replaces every `:scope` with `localName` + `#id` (when the element has an id) + `.` + its
 * first class (when it has a class attribute), then matches normally. So for a class-less, id-less
 * `<div>`, `:scope > div > div` means `div > div > div` among its descendants and can also match
 * deeper elements. The site selectors were tuned against that behaviour on real pages, and the
 * goldens from the old parsers depend on it. See [scopeSelector].
 */
object Dom {
  /** Parses a full HTML page, like `new DOMParser().parseFromString(html, 'text/html')`. */
  fun parse(html: String): Document =
    Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }
}

private val SCOPE = Regex(":scope", RegexOption.IGNORE_CASE)

/**
 * nwsapi's `makeref`: what `:scope` becomes for [this] element. Values are not escaped, like the
 * original; a class such as `md:flex` makes the query invalid (deno-dom threw a SyntaxError, jsoup
 * throws `Selector.SelectorParseException`).
 */
internal fun Element.scopeSelector(): String = buildString {
  append(normalName())
  attrOrNull("id")?.takeIf { it.isNotEmpty() }?.let { append('#').append(it) }
  attrOrNull("class")
    ?.takeIf { it.isNotEmpty() }
    ?.let { cls ->
      append('.')
        .append(
          cls.split(' ', '\t', '\n', '\r', '\u000C').firstOrNull { it.isNotEmpty() } ?: "undefined"
        )
    }
}

/** A matcher for [query] evaluated from the document, as `querySelector` does. */
private fun Element.matcher(query: String): (Element) -> Boolean {
  val q = if (SCOPE.containsMatchIn(query)) SCOPE.replace(query, scopeSelector()) else query
  val predicate = QueryParser.parse(q.trim()).asPredicate(root())
  return predicate::test
}

/**
 * `element.querySelectorAll(query)`: descendants only (never the element itself), in document
 * order, with ancestors anywhere in the document allowed to satisfy the selector.
 */
fun Element.qsa(query: String): List<Element> {
  val matches = matcher(query)
  return allElements.filter { it !== this && matches(it) }
}

/** `element.querySelector(query)`. See [qsa]. */
fun Element.qs(query: String): Element? {
  val matches = matcher(query)
  return allElements.firstOrNull { it !== this && matches(it) }
}

/** `document.querySelector(query)`. */
fun Document.qs(query: String): Element? = selectFirst(query)

/** `document.querySelectorAll(query)`. */
fun Document.qsa(query: String): List<Element> = select(query)

/** `element.getAttribute(name)`: `null` when the attribute is missing (jsoup returns ""). */
fun Element.attrOrNull(name: String): String? = if (hasAttr(name)) attr(name) else null

/** DOM `parentElement`: `null` above `<html>` (jsoup returns the document node). */
fun Element.parentElement(): Element? = parent()?.takeUnless { it is Document }

/** DOM `textContent`: every descendant text node, raw, including script and style data. */
fun Element.domText(): String {
  val sb = StringBuilder()
  NodeTraversor.traverse(
    NodeVisitor { node, _ ->
      when (node) {
        is TextNode -> sb.append(node.wholeText)
        is DataNode -> sb.append(node.wholeData)
      }
    },
    this,
  )
  return sb.toString()
}

/** DOM `innerHTML`, serialised without pretty printing. */
fun Element.innerHtml(): String = html()

/** JS `String.prototype.trim`: strips WhiteSpace and LineTerminator code points. */
fun String.jsTrim(): String = trim { it.isJsWhitespace() }

/** JS `trim()` then "empty is missing", the `x?.trim() || undefined` idiom. */
fun String?.jsTrimOrNull(): String? = this?.jsTrim()?.ifEmpty { null }

private fun Char.isJsWhitespace(): Boolean =
  when (this) {
    '\t',
    '\u000B',
    '\u000C',
    ' ',
    ' ',
    '﻿',
    '\n',
    '\r',
    ' ',
    ' ' -> true
    else -> Character.getType(this) == Character.SPACE_SEPARATOR.toInt()
  }
