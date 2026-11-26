package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.model.PdfPageSize
import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tools for the resume, its PDF preferences and the LinkedIn data the PDFs draw on. */
internal object McpResumeTools {
  private val SECTIONS = PdfSection.entries.map { wire(it) }

  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "get_resume",
        "The resume the app scores jobs against: file name, pages, words, problems found, and its text as the app read it.",
        readOnly = true,
      ) {
        val info =
          d.ats?.getResume()
            ?: return@McpTool "No resume yet. Upload one with upload_resume or in Settings → Resume."
        buildJsonObject {
          put("info", mcpJson(info))
          put("text", d.resumeText().orEmpty())
        }
      }
    )
    add(
      McpTool(
        "upload_resume",
        "Replaces the resume with a file on this computer (PDF, DOCX or TXT). ATS scores are made again for it.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of the file."),
          required = listOf("path"),
        ),
      ) {
        val ats = d.ats ?: throw McpToolError("The resume isn't available in this app.")
        val file = readable(it.str("path"), setOf("pdf", "docx", "txt"), MAX_RESUME_BYTES)
        mcpJson(ats.uploadResume(file.name, file.readBytes()))
      }
    )
    add(
      McpTool(
        "get_resume_pdf_settings",
        "How the resume PDFs the app makes look (page size, most pages, section order, hidden sections, whether a job's PDF may use LinkedIn data), and the LinkedIn data imported.",
        readOnly = true,
      ) {
        mcpJson(pdfs(d).status())
      }
    )
    add(
      McpTool(
        "set_resume_pdf_settings",
        "Changes how the resume PDFs look. Leave out what should stay.",
        McpSchema.obj(
          "page_size" to McpSchema.string("Page size.", PdfPageSize.entries.map { wire(it) }),
          "max_pages" to McpSchema.integer("1 or 2: the least related bullets go until it fits."),
          "order" to McpSchema.strings("Sections in order, from $SECTIONS; missing ones go last."),
          "hidden" to McpSchema.strings("Sections to leave out, from $SECTIONS."),
          "use_linkedin" to
            McpSchema.boolean("A job's PDF may add LinkedIn skills and bullets that fit the job."),
        ),
      ) {
        val repo = pdfs(d)
        val current = repo.status().prefs
        val maxPages = it.optInt("max_pages") ?: current.maxPages
        if (maxPages !in 1..2) throw McpToolError("max_pages must be 1 or 2.")
        val prefs =
          ResumePdfPrefs(
            pageSize =
              it.optStr("page_size")?.let { s -> fromMcpJson(JsonPrimitive(s), "page size") }
                ?: current.pageSize,
            maxPages = maxPages,
            order = it.optStrings("order")?.map(::section)?.distinct() ?: current.order,
            hidden = it.optStrings("hidden")?.map(::section)?.toSet() ?: current.hidden,
            useLinkedIn = it.optBool("use_linkedin") ?: current.useLinkedIn,
          )
        mcpJson(repo.savePrefs(prefs))
      }
    )
    add(
      McpTool(
        "import_linkedin_data",
        "Imports the user's LinkedIn data export (the ZIP from LinkedIn → Settings → Data privacy → Get a copy of your data) from a file on this computer. The resume PDFs and fix plans draw on it.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of the ZIP."),
          required = listOf("path"),
        ),
      ) {
        val file =
          readable(it.str("path"), setOf("zip"), ResumePdfRepository.MAX_EXPORT_BYTES.toLong())
        mcpJson(pdfs(d).importLinkedIn(file.name, file.readBytes()))
      }
    )
    add(
      McpTool(
        "delete_linkedin_data",
        "Removes the imported LinkedIn data. Ask the user first.",
        destructive = true,
      ) {
        mcpJson(pdfs(d).deleteLinkedIn())
      }
    )
  }

  private fun pdfs(d: McpDependencies): ResumePdfRepository =
    d.resumePdf() ?: throw McpToolError("Resume PDFs aren't available in this app.")

  private fun readable(path: String, extensions: Set<String>, maxBytes: Long): File {
    val file = File(path)
    if (!file.isFile) throw McpToolError("No file at ${file.path}.")
    if (file.extension.lowercase() !in extensions) {
      throw McpToolError("Use a ${extensions.joinToString(" or ") { it.uppercase() }} file.")
    }
    if (file.length() > maxBytes) {
      throw McpToolError("The file is over ${maxBytes / (1024 * 1024)} MB.")
    }
    return file
  }

  private fun section(wire: String): PdfSection =
    PdfSection.entries.firstOrNull { wire(it) == wire.trim().lowercase() }
      ?: throw McpToolError("Unknown section \"$wire\". Use $SECTIONS.")

  private fun wire(value: Enum<*>): String = value.name.lowercase()

  private const val MAX_RESUME_BYTES = 10L * 1024 * 1024
}
