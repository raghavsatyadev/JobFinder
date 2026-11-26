package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptChecks
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.shared.model.tryable
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Tools for the instructions the app gives the AI (custom prompts). */
internal object McpPromptTools {
  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "list_ai_prompts",
        "The instructions the app gives the AI for each kind of work, the app's own text, and the words each must keep. Read help topic ai-prompts first.",
        readOnly = true,
      ) {
        buildJsonArray { prompts(d).listPrompts().forEach { add(describe(it)) } }
      }
    )
    add(
      McpTool(
        "set_ai_prompt",
        "Saves new instructions for one kind of AI work. Only the instructions change: the job, the resume and the answer format stay fixed around them. Returns warnings; fix strong ones before keeping the change.",
        McpSchema.obj(
          "id" to McpSchema.string("Prompt id.", PromptId.entries.map { it.wire }),
          "text" to
            McpSchema.string("The new instructions, at most ${AiPrompt.MAX_LENGTH} characters."),
          "confirm" to
            McpSchema.boolean("The user agreed to change a prompt that reads job boards."),
          required = listOf("id", "text"),
        ),
      ) {
        val id = promptId(it.str("id"))
        val text = it.str("text")
        if (text.length > AiPrompt.MAX_LENGTH) {
          throw McpToolError("Instructions can be up to ${AiPrompt.MAX_LENGTH} characters.")
        }
        val repo = prompts(d)
        val default = repo.listPrompts().first { p -> p.id == id }.defaultText
        val warnings = PromptChecks.check(id, text, default)
        val missing =
          warnings.filterIsInstance<PromptWarning.MissingTerms>().flatMap { w -> w.terms }
        if (missing.isNotEmpty()) {
          throw McpToolError(
            "Not saved: the answer needs these words in the text: ${missing.joinToString()}."
          )
        }
        if (id.drivesParsing && it.optBool("confirm") != true) {
          throw McpToolError(
            "Not saved: this prompt reads job boards and a bad edit can stop jobs coming in. Ask the user, then call again with confirm=true."
          )
        }
        val saved = repo.savePrompt(id, text)
        buildJsonObject {
          put("saved", describe(saved))
          putJsonArray("warnings") { warnings.forEach { w -> add(JsonPrimitive(warning(w))) } }
        }
      }
    )
    add(
      McpTool(
        "reset_ai_prompt",
        "Puts one kind of AI work back on the app's own instructions.",
        McpSchema.obj(
          "id" to McpSchema.string("Prompt id.", PromptId.entries.map { it.wire }),
          required = listOf("id"),
        ),
      ) {
        describe(prompts(d).resetPrompt(promptId(it.str("id"))))
      }
    )
    add(
      McpTool(
        "try_ai_prompt",
        "Runs instructions once on the newest job in New without saving them, and returns what the AI answered. Works for 'matching' and 'ats_review'. Can take a few minutes on the local model.",
        McpSchema.obj(
          "id" to
            McpSchema.string("Prompt id.", PromptId.entries.filter { it.tryable }.map { it.wire }),
          "text" to McpSchema.string("The instructions to try."),
          required = listOf("id", "text"),
        ),
      ) {
        val id = promptId(it.str("id"))
        if (!id.tryable) throw McpToolError("Only matching and ats_review can be tried.")
        mcpJson(prompts(d).tryPrompt(id, it.str("text")))
      }
    )
  }

  /** Every prompt the user edited, by id: the custom part of the AI instructions. */
  suspend fun edited(d: McpDependencies) =
    d.prompts?.listPrompts().orEmpty().filter { it.edited }.associate { it.id.wire to it.text }

  private fun prompts(d: McpDependencies): AiPromptsRepository =
    d.prompts ?: throw McpToolError("AI instructions aren't available in this app.")

  private fun promptId(wire: String): PromptId =
    PromptId.fromWire(wire)
      ?: throw McpToolError(
        "Unknown prompt \"$wire\". Use one of ${PromptId.entries.map { it.wire }}."
      )

  private fun describe(p: AiPrompt) = buildJsonObject {
    put("id", p.id.wire)
    put("edited", p.edited)
    put("readsJobBoards", p.id.drivesParsing)
    put("canTry", p.id.tryable)
    putJsonArray("mustKeepWords") { p.id.requiredTerms.forEach { add(JsonPrimitive(it)) } }
    put("text", p.text)
    if (p.edited) put("defaultText", p.defaultText)
  }

  private fun warning(w: PromptWarning): String =
    when (w) {
      PromptWarning.Empty -> "No instructions at all."
      is PromptWarning.MissingTerms ->
        "The answer needs these words, now missing: ${w.terms.joinToString()}."
      PromptWarning.MentionsFormat ->
        "The text talks about the answer's format, which the app fixes; it may confuse the model."
      PromptWarning.DrivesParsing ->
        "This prompt reads job boards: a bad edit can stop jobs coming in."
      PromptWarning.ResultsChange -> "Results will differ from before."
    }
}
