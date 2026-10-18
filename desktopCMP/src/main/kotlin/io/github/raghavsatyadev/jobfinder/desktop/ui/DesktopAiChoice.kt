package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.server.ai.AiCheck
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoiceState
import io.github.raghavsatyadev.jobfinder.ui.platform.AiTestResult

/** [AiChoice] on the in-process [AiService]; the keys stay in its protected store. */
class DesktopAiChoice(private val ai: AiService, private val client: LlmClient) : AiChoice {
  override suspend fun load(): AiChoiceState =
    AiChoiceState(
      settings = ai.settings(),
      keysSaved = AiProvider.entries.filter { ai.hasKey(it) }.toSet(),
      keysAvailable = ai.keysAvailable,
    )

  override suspend fun save(settings: AiSettings, key: String?): AiChoiceState {
    ai.save(settings, key)
    return load()
  }

  override suspend fun test(settings: AiSettings, key: String?): AiTestResult =
    when (val check = ai.check(client, settings, key)) {
      AiCheck.Ok -> AiTestResult.Ok
      AiCheck.KeyMissing -> AiTestResult.KeyMissing
      is AiCheck.Failed -> AiTestResult.Failed(check.reason)
    }
}
