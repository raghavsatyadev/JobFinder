package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.domText
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import org.jsoup.nodes.Element

/** `el?.textContent?.trim()`. */
internal fun Element?.trimmedText(): String? = this?.domText()?.jsTrim()

/** `el?.getAttribute(name)?.trim()`. */
internal fun Element?.trimmedAttr(name: String): String? = this?.attrOrNull(name)?.jsTrim()

/** The `x || undefined` idiom: an empty string counts as missing. */
internal fun String?.nullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

/** A JS `\s` regex fragment, for patterns that must not use Kotlin's ASCII-only `\s`. */
internal const val JS_S =
  "[${io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers.JS_SPACE_CLASS}]"

/** Builds a case-insensitive regex. */
internal fun ciRegex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

/** JS `String.replace(/x/, "")` with a non-global regex: only the first match. */
internal fun String.replaceFirstMatch(regex: Regex, replacement: String = ""): String =
  regex.replaceFirst(this, replacement)

private val INT_PREFIX = Regex("""^[+-]?\d+""")
private val FLOAT_PREFIX = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/** JS `parseInt(s)` (radix 10): the leading integer, or `null` for NaN. */
internal fun jsParseInt(s: String): Long? = INT_PREFIX.find(s.jsTrim())?.value?.toLongOrNull()

/** JS `parseFloat(s)`: the leading decimal number, or `null` for NaN. */
internal fun jsParseFloat(s: String): Double? =
  FLOAT_PREFIX.find(s.jsTrim())?.value?.toDoubleOrNull()

/** JS `String(number)` for the values the parsers print: `85` not `85.0`, `NaN`. */
internal fun jsNumber(d: Double?): String =
  when {
    d == null || d.isNaN() -> "NaN"
    d == Math.floor(d) && Math.abs(d) < 1e15 -> d.toLong().toString()
    else -> d.toString()
  }
