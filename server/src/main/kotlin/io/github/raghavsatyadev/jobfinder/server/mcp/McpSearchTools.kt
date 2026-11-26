package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Tools for the saved job searches and the AI filter. */
internal object McpSearchTools {
  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "list_job_boards",
        "The job boards the app reads best (built-in readers). Searches on other boards work too: the AI reads them.",
        readOnly = true,
      ) {
        buildJsonArray {
          d.sites
            .listSites()
            .filter { !it.deprecated }
            .forEach { s ->
              add(
                buildJsonObject {
                  put("name", s.name)
                  putJsonArray("addresses") { s.urls.forEach { add(JsonPrimitive(it)) } }
                  putJsonArray("notSearchPaths") {
                    s.blacklistedPaths.forEach { add(JsonPrimitive(it)) }
                  }
                }
              )
            }
        }
      }
    )
    add(
      McpTool(
        "list_job_searches",
        "The saved job searches (search result page addresses the app checks for new jobs), with failed scans in a row.",
        readOnly = true,
      ) {
        val sites = d.sites.listSites().associateBy { it.id }
        buildJsonArray {
          d.links.listLinks().forEach { l ->
            add(
              buildJsonObject {
                put("id", l.id)
                put("title", l.title)
                put("url", l.url)
                put("board", sites[l.siteId]?.name)
                put("failedScansInARow", l.scrapeFailureCount)
                put("broken", l.scrapeFailureCount >= 3)
                put("lastScanned", iso(l.lastScrapedAt))
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "add_job_search",
        "Saves a job search. The app opens the address in its browser (signed in to the boards) and checks it shows a job list, so this can take up to a minute. Use the full address of a search RESULTS page with the filters set (role, location or remote, newest first, recent days). Read help topic job-searches first.",
        McpSchema.obj(
          "title" to
            McpSchema.string("Short title, for example \"LinkedIn: Senior Android, Remote\"."),
          "url" to McpSchema.string("Full http(s) address of the search results page."),
          "force" to
            McpSchema.boolean(
              "Save even when no job list is recognised (a board the app reads later with AI). Only when the user agrees."
            ),
          "board_name" to McpSchema.string("Name for a board the app doesn't know. Optional."),
          required = listOf("title", "url"),
        ),
      ) {
        val created =
          d.linkService.createLink(
            it.str("title"),
            it.str("url"),
            it.optBool("force") ?: false,
            siteName = it.optStr("board_name")?.takeIf(String::isNotBlank),
          )
        buildJsonObject {
          put("saved", mcpJson(created.link))
          put("jobsFoundNow", created.newJobIds.size)
          put("noJobListRecognised", created.parseFailed)
        }
      }
    )
    add(
      McpTool(
        "update_job_search",
        "Changes a saved search's title or address.",
        McpSchema.obj(
          "id" to McpSchema.integer("Search id."),
          "title" to McpSchema.string("New title; leave out to keep."),
          "url" to McpSchema.string("New address; leave out to keep."),
          required = listOf("id"),
        ),
      ) {
        val id = it.long("id")
        val link = d.links.getLink(id) ?: throw McpToolError("No search $id.")
        val title = it.optStr("title")?.trim()?.ifEmpty { null } ?: link.title
        val url = it.optStr("url")?.trim()?.ifEmpty { null } ?: link.url
        mcpJson(d.links.updateLink(id, title, url) ?: throw McpToolError("No search $id."))
      }
    )
    add(
      McpTool(
        "delete_job_search",
        "Deletes a saved search. Ask the user first. Jobs it found stay.",
        McpSchema.obj("id" to McpSchema.integer("Search id."), required = listOf("id")),
        destructive = true,
      ) {
        val id = it.long("id")
        d.links.getLink(id) ?: throw McpToolError("No search $id.")
        d.links.deleteLink(id)
        "Deleted search $id."
      }
    )

    // AI filter
    add(
      McpTool(
        "get_ai_filter",
        "The AI filter: the user's description of jobs to filter out, and companies always skipped.",
        readOnly = true,
      ) {
        mcpJson(d.matching.getConfig())
      }
    )
    add(
      McpTool(
        "set_ai_filter",
        "Saves the AI filter. New jobs are checked with it, and jobs already in New are checked again in the background. Read help topic ai-filter first and confirm the text with the user.",
        McpSchema.obj(
          "description" to
            McpSchema.string(
              "Plain sentences saying which jobs to filter OUT, at most ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH} characters. Leave out to keep."
            ),
          "skip_companies" to
            McpSchema.strings("The full list of companies to always skip. Leave out to keep."),
          "add_skip_companies" to McpSchema.strings("Companies to add to the list."),
        ),
      ) {
        val current = d.matching.getConfig()
        val description = it.optStr("description")?.trim() ?: current.prompt
        if (description.length > AdvancedMatchingConfig.MAX_PROMPT_LENGTH) {
          throw McpToolError(
            "The description is ${description.length} characters; the most is ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH}."
          )
        }
        val base = it.optStrings("skip_companies") ?: current.blacklistedCompanies
        val companies =
          (base + it.optStrings("add_skip_companies").orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
        mcpJson(d.matching.updateConfig(AdvancedMatchingConfig(description, companies)))
      }
    )
  }
}
