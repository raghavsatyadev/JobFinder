package io.github.raghavsatyadev.jobfinder.server.parsing.custom

import io.github.raghavsatyadev.jobfinder.server.parsing.HtmlToMarkdown
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Title, description and favicon read from `<head>` before it is stripped. */
data class HeaderInfo(val title: String, val metaDescription: String, val faviconUrl: String?)

/** HTML cleanup for the LLM, ported from the old `customJobsParser.ts`. */
object CustomPageCleaner {
  /** The old code sent the whole page; a local model has a small context, so cap it. */
  const val MAX_HTML_CHARS = 60_000

  private val LIST_NODES_TO_REMOVE =
    listOf("head", "script", "style", "nav", "header", "footer", "aside", "iframe")
  private val DESCRIPTION_NODES_TO_REMOVE =
    listOf("head", "script", "style", "nav", "header", "footer", "aside", "img", "form")
  private val DROP_ATTRS = Regex("^(class|style|aria-.*|role)$")

  fun headerInfo(html: String): HeaderInfo {
    val doc = Jsoup.parse(html)
    val favicon =
      doc
        .select("link[rel~=(?i)icon], link[rel~=(?i)shortcut icon]")
        .map { it.attr("href") to it.attr("sizes") }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { (_, sizes) -> sizes.substringBefore('x').toIntOrNull() ?: 0 }
        .firstOrNull()
        ?.first
    return HeaderInfo(
      title = doc.selectFirst("title")?.text().orEmpty(),
      metaDescription = doc.selectFirst("meta[name=description]")?.attr("content").orEmpty(),
      faviconUrl = favicon,
    )
  }

  /** Cleaned `<html>` outerHTML of a list page, truncated to [MAX_HTML_CHARS]. */
  fun listHtml(html: String): String = clean(html, LIST_NODES_TO_REMOVE).take(MAX_HTML_CHARS)

  /** A job page as markdown (the old turndown step), truncated to [MAX_HTML_CHARS]. */
  fun descriptionMarkdown(html: String): String =
    HtmlToMarkdown.convert(clean(html, DESCRIPTION_NODES_TO_REMOVE)).take(MAX_HTML_CHARS)

  private fun clean(html: String, remove: List<String>): String {
    val doc: Document = Jsoup.parse(html)
    doc.outputSettings().prettyPrint(false)
    doc.select(remove.joinToString(",")).remove()
    for (el in doc.allElements) {
      el
        .attributes()
        .asList()
        .map { it.key }
        .filter { DROP_ATTRS.matches(it) }
        .forEach {
          el.removeAttr(it)
        }
    }
    return doc.child(0).outerHtml()
  }
}

/** Prompts and JSON schemas, ported from the old `customJobsParser.ts`. */
object CustomPrompts {
  fun listUserPrompt(url: String, header: HeaderInfo, htmlContent: String): String {
    val headerJson = buildJsonObject {
      put("title", header.title)
      put("metaDescription", header.metaDescription)
      header.faviconUrl?.let { put("faviconUrl", it) }
    }
    return """Extract the jobs listing from the HTML page below. Return the result as a JSON object matching the provided schema. If no jobs are found, return an empty array for the jobs field.
Here are some rules for the required output:
- The externalUrl field should be the direct URL to the job listing. It should be a fully qualified URL. If only a relative URL is available, prepend the domain name from the page URL: $url. Should never be an email address.
- The title field should be the job title.
- The companyName field should be the name of the company offering the job.
- The companyLogo field should be a URL to the company's logo, if available. If not available, try to use the site favicon URL: ${header.faviconUrl.orEmpty()}. If the logo URL is relative, prepend the domain name from the page URL: $url.
- The jobType field should indicate if the job is remote, hybrid, or onsite. If not specified, leave it empty.
- The location field should specify the job's location, if available.
    Add the full location as provided including street, city, state, country if available. If only "remote" is mentioned, leave the location empty and set jobType to "remote".
- The salary field should specify the offered salary or salary range, if available. Always try to extract it if present. If there are other benefits mentioned (e.g. stock options, bonuses), do not include them in the salary field, but put them as tags.
- The tags field should include relevant tags or keywords associated with the job, if available. If you see "easy apply" on a job add it as a tag. Or if the job is sponsored.

Limit the number of jobs extracted to a maximum of 30. If more jobs are present, prioritize the most recent ones.
Try to extract all or as many jobs from the page as possible. And preserve the order of the jobs as they appear on the page.

Here is the page header info:
$headerJson

Here is the HTML page:
${"\"\"\""}
$htmlContent
${"\"\"\""}"""
  }

  fun descriptionUserPrompt(markdown: String, preferences: String?): String =
    """Extract the job description from the HTML page below. Return the result as a JSON object matching the provided schema.
Here is the HTML page turned into markdown:
${"\"\"\""}
$markdown
${"\"\"\""}

Here are my job search preferences: ${preferences ?: "nothing specific for the moment"}.
"""

  val LIST_SYSTEM_PROMPT =
    """You are an expert web scraper specialized in extracting job listings from HTML pages.
Your task is to analyze the provided HTML content and identify job listings, extracting relevant details for each job.
If you cannot extract the information due to the HTML being a login page, CAPTCHA, or any other access restriction, respond with an empty result and an appropriate errorMessage.

The externalUrl ideally should point to a dedicated page, not the same listing page and should be unique per job. It's the most important field to extract.
If the job description mentions a another URL where to apply, use that as externalUrl.

When composing the externalUrl or companyLogo with relative URLs, ensure to make it relative to the URL of the scraped page, not just the domain.
Here are some common examples of externalUrls from different popular job sites:
- talent.com: https://www.talent.com/view?id=1234567890abcdef
- linkedin.com: https://www.linkedin.com/jobs/view/1234567890/
- indeed.com: https://www.indeed.com/viewjob?jk=abcdef1234567890
- glassdoor.com: https://www.glassdoor.com/job-listing/software-engineer-google-JV_IC1234567_KO0,17_KE18,24.htm?jl=1234567890
- monster.com: https://www.monster.com/jobs/search/?q=Software-Engineer&where=Remote&jobid=1234567890
- google.com: https://www.google.com/about/careers/applications/jobs/results/132525933222339270-software-engineer-iii-aiml

If the user is trying to scrape a page that is just a single job description, return an empty jobs array and an appropriate errorMessage.

IMPORTANT: if the page is a job results page, but there are no jobs matching the filters, DON'T return an error, just return an empty jobs array and no errorMessage.
This is also valid for Google search results where the user might be looking for jobs from ATS sites (using the last 24h filter in Google search might return no results, but it's not an error, just that there are no jobs matching the criteria).

Here are some unsupported website:
- hiringcafe.com. - their html pages don't allow scraping.

Here are some other site specific notes:
- hnhiring.com
  - this site is a forum, so the job posts are in forum post format.
  - VERY IMPORTANT: extract the description as the post content exactly as is, the original post body.
  - some posts are marked as "Multiple Roles", in that case extract the description as is, do not try to split into multiple jobs, each with their unique url (ATS sites preferred)
"""

  val DESCRIPTION_SYSTEM_PROMPT =
    """You are an expert web scraper specialized in extracting the job description from an HTML page.
You must return two things: an interpretive "summary", and a faithful, verbatim "description". Interpretation belongs ONLY in the summary.

If you cannot extract the information because the HTML is a login page, CAPTCHA, or any other access restriction, respond with an empty result and an appropriate errorMessage.

## summary field
- Write a concise summary (maximum 1000 characters) so a user can quickly understand the role. This is the ONLY place where you may rephrase things in your own words.
- If the user provided job search preferences, highlight how well the job matches them.
- You may use markdown (bold, italics, lists) to improve readability.

## description field
- Reproduce the job description exactly as it appears on the page. This is a transcription, NOT a rewrite.
- Do NOT paraphrase, reword, summarize, shorten, or "improve" the text. Keep the original sentences and phrasing exactly as written on the page.
- Do NOT add, infer, or invent anything that is not on the page, and do NOT drop any sections.
- Preserve the original structure: keep the page's section headings as markdown headings, and keep bullet or numbered lists as real markdown lists with ONE item per line. Never collapse a list into a running paragraph.
- Your only job for this field is to faithfully translate the page's HTML into equivalent markdown (headings, bold, lists, links) — nothing else.
- Include the complete description: responsibilities, requirements, benefits, pay/salary, education and experience, location and remote/on-site conditions, and any other sections present on the page.

## tags field
- Include up to 10 relevant tags or keywords for the job, if available.
- If the salary mentions other benefits (e.g. stock options, bonuses), do not put them in the salary field — put them in tags instead.
- Do not include the location, salary, or job type as tags.
- Add the seniority level as a tag if available (e.g. junior, mid-level, senior, lead, principal).
"""

  private fun str(nullable: Boolean = true): JsonObject = buildJsonObject {
    put("type", if (nullable) typeOrNull("string") else JsonPrimitive("string"))
  }

  private fun typeOrNull(type: String): JsonArray = buildJsonArray {
    add(JsonPrimitive(type))
    add(JsonPrimitive("null"))
  }

  private fun obj(props: Map<String, kotlinx.serialization.json.JsonElement>): JsonObject =
    buildJsonObject {
      put("type", "object")
      put("properties", JsonObject(props))
      put("required", JsonArray(props.keys.map { JsonPrimitive(it) }))
      put("additionalProperties", false)
    }

  private val stringArrayOrNull: JsonObject = buildJsonObject {
    put("type", typeOrNull("array"))
    put("items", buildJsonObject { put("type", "string") })
  }

  /** The old `PARSE_JOBS_PAGE_SCHEMA` (zod) as JSON schema. */
  val LIST_SCHEMA: JsonObject =
    obj(
      mapOf(
        "jobs" to
          buildJsonObject {
            put("type", "array")
            put(
              "items",
              obj(
                mapOf(
                  "externalUrl" to str(nullable = false),
                  "title" to str(nullable = false),
                  "companyName" to str(nullable = false),
                  "companyLogo" to str(),
                  "jobType" to
                    buildJsonObject {
                      put("type", typeOrNull("string"))
                      put(
                        "enum",
                        buildJsonArray {
                          add(JsonPrimitive("remote"))
                          add(JsonPrimitive("hybrid"))
                          add(JsonPrimitive("onsite"))
                          add(kotlinx.serialization.json.JsonNull)
                        },
                      )
                    },
                  "location" to str(),
                  "salary" to str(),
                  "tags" to stringArrayOrNull,
                  "description" to str(),
                )
              ),
            )
          },
        "errorMessage" to str(),
      )
    )

  /** The old `PARSE_JOB_DESCRIPTION_SCHEMA`. */
  val DESCRIPTION_SCHEMA: JsonObject =
    obj(
      mapOf(
        "description" to str(),
        "summary" to str(),
        "salary" to str(),
        "tags" to stringArrayOrNull,
        "errorMessage" to str(),
      )
    )
}
