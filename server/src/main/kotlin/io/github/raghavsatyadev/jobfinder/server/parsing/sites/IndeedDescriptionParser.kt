package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.HtmlToMarkdown
import io.github.raghavsatyadev.jobfinder.server.parsing.JobDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.JobDescriptionUpdates
import io.github.raghavsatyadev.jobfinder.server.parsing.SelectorDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.domText
import io.github.raghavsatyadev.jobfinder.server.parsing.innerHtml
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Indeed job page (old `parseIndeedJobDescription`).
 *
 * The data Indeed embeds as `window._initialData={...}` is tried first because it also has the
 * salary, tags, posting date and company rating. Without it, the description container is used.
 */
object IndeedDescriptionParser : JobDescriptionParser {
  private val containers =
    SelectorDescriptionParser(
      listOf(
        "#JobDescriptionUpdatesText",
        "#jobDescriptionText",
        ".simple-job-description-html",
        "p.error-description",
      )
    )
  private const val MARKER = "window._initialData"
  private val STAR_GAP = Regex("\\*${JS_S}${JS_S}\\n")
  private val json = Json

  override fun parse(html: String): JobDescriptionUpdates {
    val document = Dom.parse(html)
    document
      .qsa("script")
      .filter { MARKER in it.domText() }
      .forEach { script ->
        fromInitialData(script.domText())?.let {
          return it
        }
      }

    val container = containers.findContainer(document) ?: return JobDescriptionUpdates.NONE
    // `turndown(innerHTML).replace(/\*\s\s\n/gi, '*')`
    val description = HtmlToMarkdown.convert(container.innerHtml()).replace(STAR_GAP, "*")
    return JobDescriptionUpdates(description = description)
  }

  private fun fromInitialData(scriptText: String): JobDescriptionUpdates? {
    // Some of these scripts are JS object literals, not JSON: skip those.
    val text = scriptText.jsTrim()
    val start = (text.indexOf("$MARKER={") + "$MARKER=".length).coerceAtLeast(0)
    val end = text.lastIndexOf('}') + 1
    if (start >= end) return null
    val global =
      runCatching { json.parseToJsonElement(text.substring(start, end)) }.getOrNull() as? JsonObject
        ?: return null

    val jobData =
      global.path("hostQueryExecutionResult", "data", "jobData", "results").index(0).get("job")
        as? JsonObject ?: return null

    var description: String? = null
    val descriptionObj = jobData["description"] as? JsonObject
    val descriptionText = descriptionObj?.string("text")
    val descriptionHtml = descriptionObj?.string("html")
    if (!descriptionText.isNullOrEmpty()) description = descriptionText
    else if (!descriptionHtml.isNullOrEmpty()) description = HtmlToMarkdown.convert(descriptionHtml)

    val salary = (global["salaryInfoModel"] as? JsonObject)?.let { salary(it) }

    val tags =
      (labels(jobData["jobTypes"]) + labels(jobData["benefits"])).distinct().filter {
        it.isNotEmpty()
      }

    if (description != null) description = metadataTable(jobData) + description
    return JobDescriptionUpdates(
      description = description,
      salary = salary,
      tags = tags.ifEmpty { null },
    )
  }

  private fun salary(model: JsonObject): String? {
    model
      .string("salaryText")
      ?.takeIf { it.isNotEmpty() }
      ?.let {
        return it
      }
    val min = model.display("salaryMin")
    val max = model.display("salaryMax")
    if (min == null || max == null) return null
    val currency = if (model.string("salaryCurrency") == "USD") "$" else ""
    val type =
      when (model.string("salaryType")) {
        "HOURLY" -> " per hour"
        "YEARLY" -> " per year"
        else -> ""
      }
    return "$currency$min - $currency$max$type"
  }

  private fun labels(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("label") }

  /** The `**Job Information:**` block put in front of the description. */
  private fun metadataTable(jobData: JsonObject): String {
    val metadata = linkedMapOf<String, String>()
    val published = jobData["datePublished"]
    if (published.isTruthy()) metadata["Date Posted"] = localeDate(published as JsonPrimitive)

    val employer = jobData["employer"]
    val rating = employer.path("ugcStats", "ratings", "overallRating").get("value")
    if (rating.isTruthy()) {
      val count = (employer.path("ugcStats").get("globalReviewCount") as? JsonPrimitive)
      val reviews = count?.takeIf { it.isTruthy() }?.let { jsNumberOf(it) }?.toDoubleOrNull() ?: 0.0
      metadata["Company Rating"] =
        "${jsNumberOf(rating as JsonPrimitive)}/5 (${String.format(Locale.US, "%,d", reviews.toLong())} reviews)"
    }
    if (metadata.isEmpty()) return ""
    return buildString {
      append("**Job Information:**\n\n")
      metadata.forEach { (key, value) -> append("**$key:** $value\n\n") }
      append("---\n\n")
    }
  }

  /**
   * `new Date(x).toLocaleDateString()` used the server's locale (the stored text varied); this uses
   * the unambiguous `yyyy-MM-dd` in the machine's time zone.
   */
  private fun localeDate(value: JsonPrimitive): String {
    val instant =
      if (value.isString) runCatching { Instant.parse(value.content) }.getOrNull()
      else value.content.toDoubleOrNull()?.let { Instant.ofEpochMilli(it.toLong()) }
    instant ?: return "Invalid Date"
    return DateTimeFormatter.ISO_LOCAL_DATE.format(instant.atZone(ZoneId.systemDefault()))
  }

  private fun JsonElement?.isTruthy(): Boolean {
    val p = this as? JsonPrimitive ?: return this is JsonObject || this is JsonArray
    if (p is JsonNull) return false
    if (p.isString) return p.content.isNotEmpty()
    return when (p.content) {
      "false" -> false
      else -> p.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } ?: true
    }
  }

  private fun jsNumberOf(p: JsonPrimitive): String =
    if (p.isString) p.content else p.content.toDoubleOrNull()?.let { jsNumber(it) } ?: p.content

  /** A min/max amount as JS would print it, or `null` when falsy. */
  private fun JsonObject.display(key: String): String? {
    val value = this[key]
    return if (value.isTruthy() && value is JsonPrimitive) jsNumberOf(value) else null
  }

  private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

  private fun JsonElement?.path(vararg keys: String): JsonElement? =
    keys.fold(this) { acc, key -> (acc as? JsonObject)?.get(key) }

  private fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

  private fun JsonElement?.index(i: Int): JsonElement? = (this as? JsonArray)?.getOrNull(i)
}
