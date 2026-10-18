package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.db.AiPromptEntity
import io.github.raghavsatyadev.jobfinder.server.db.AiPromptsDao
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.CustomPrompts
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.ats.OllamaAtsModel
import io.github.raghavsatyadev.jobfinder.server.service.ghost.CareersMatcher
import io.github.raghavsatyadev.jobfinder.server.service.tips.TipsService
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository

/**
 * The AI instructions: the app's own text unless the user edited one. Only edits are stored, so a
 * new app version's better default reaches every prompt the user left alone.
 */
class PromptBook(
  private val dao: AiPromptsDao,
  private val clock: () -> Long = System::currentTimeMillis,
) : AiPromptsRepository {
  /** What the AI is told for [id] now. */
  suspend fun instructions(id: PromptId): String = dao.get(id.wire)?.text ?: defaultText(id)

  override suspend fun listPrompts(): List<AiPrompt> {
    val edits = dao.all().associate { it.id to it.text }
    return PromptId.entries.map { AiPrompt(it, edits[it.wire] ?: defaultText(it), defaultText(it)) }
  }

  override suspend fun savePrompt(id: PromptId, text: String): AiPrompt {
    require(text.length <= AiPrompt.MAX_LENGTH) {
      "Instructions can be up to ${AiPrompt.MAX_LENGTH} characters."
    }
    if (text == defaultText(id)) dao.delete(id.wire)
    else dao.upsert(AiPromptEntity(id.wire, text, clock()))
    return AiPrompt(id, text, defaultText(id))
  }

  override suspend fun resetPrompt(id: PromptId): AiPrompt {
    dao.delete(id.wire)
    return AiPrompt(id, defaultText(id), defaultText(id))
  }

  /** The edited prompts, for a settings backup. */
  suspend fun edits(): Map<PromptId, String> =
    dao.all().mapNotNull { row -> PromptId.fromWire(row.id)?.let { it to row.text } }.toMap()

  /** Replaces the edits with a backup's: a prompt it does not list goes back to the default. */
  suspend fun restore(edits: Map<PromptId, String>) {
    dao.deleteAll()
    edits.forEach { (id, text) -> savePrompt(id, text) }
  }

  companion object {
    fun defaultText(id: PromptId): String =
      when (id) {
        PromptId.MATCHING -> OllamaJobCheck.INSTRUCTIONS
        PromptId.PARSE_LIST -> CustomPrompts.LIST_SYSTEM_PROMPT
        PromptId.PARSE_DESCRIPTION -> CustomPrompts.DESCRIPTION_SYSTEM_PROMPT
        PromptId.ATS_REVIEW -> OllamaAtsModel.INSTRUCTIONS
        PromptId.GHOST_MATCH -> CareersMatcher.INSTRUCTIONS
        PromptId.NEGOTIATION_TIPS -> TipsService.INSTRUCTIONS
      }
  }
}
