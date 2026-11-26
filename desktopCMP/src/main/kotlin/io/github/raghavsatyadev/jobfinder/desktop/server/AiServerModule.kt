package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.desktop.models.DesktopLocalModels
import io.github.raghavsatyadev.jobfinder.desktop.models.GgufDownload
import io.github.raghavsatyadev.jobfinder.desktop.models.HttpOllamaModels
import io.github.raghavsatyadev.jobfinder.desktop.models.HuggingFace
import io.github.raghavsatyadev.jobfinder.desktop.models.ModelCheckStore
import io.github.raghavsatyadev.jobfinder.desktop.models.PcProfiler
import io.github.raghavsatyadev.jobfinder.desktop.setup.PowerShell
import io.github.raghavsatyadev.jobfinder.server.ai.AccuracyCheck
import io.github.raghavsatyadev.jobfinder.server.ai.AgyClient
import io.github.raghavsatyadev.jobfinder.server.ai.AiHealth
import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.ai.AiLanes
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeCatalog
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeClient
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeServer
import io.github.raghavsatyadev.jobfinder.server.ai.PromptBook
import io.github.raghavsatyadev.jobfinder.server.ai.PromptTrials
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.RoomAiUsageRecorder
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/** The AI part of [serverModule]: model sources, keys, lanes, health and the prompts. */
internal fun aiServerModule(dataDir: File) = module {
  single { AiService(get(), get()) }
  single<LocalModels> {
    val http = LlmClient.defaultHttpClient()
    DesktopLocalModels(
      ollama = HttpOllamaModels(http),
      huggingFace = HuggingFace(http),
      download = GgufDownload(http, dataDir.resolve(MODELS_DIR)),
      profiler = PcProfiler(PowerShell())::profile,
      accuracy = AccuracyCheck(get(), http),
      checks = ModelCheckStore(dataDir.resolve(MODEL_CHECKS_FILE)),
    )
  }
  // The online keys the user added and which model does which work.
  single {
    val db = get<ServerDatabase>()
    AiKeyPool(
      db.aiKeysDao(),
      db.settingsDao(),
      get(),
      LlmClient.defaultHttpClient(),
      health = get(),
    )
  }
  // Which model sources keep failing, for Settings and the desktop's warning.
  single { AiHealth() }
  // One lane per kind of work and online model: each kind runs on all its models at once.
  single { AiLanes() }
  // One client for every LLM call: its semaphore lets one request at a time reach the main model,
  // so the custom parser and matching never make Ollama swap models mid-scan; online keys run in
  // parallel lanes. It reads the AI choice from Settings before every call.
  single {
    val ai = get<AiService>()
    LlmClient(
      LlmClient.defaultHttpClient(),
      Semaphore(1),
      get<AiKeyPool>(),
      get(),
      get(),
      get<AiLanes>(),
      get<AiHealth>(),
    ) {
      ai.config()
    }
  }
  // The user's signed-in Antigravity CLI (agy) as a model source: one child per call.
  single { AgyClient() }
  // The user's OpenCode CLI as a free model source: started at its first call, stopped on exit.
  single {
    OpenCodeServer(dataDir.resolve(OPENCODE_DIR).toPath(), LlmClient.defaultHttpClient())
  } onClose
    {
      it?.close()
    }
  // The free OpenCode models, listed by OpenCode itself; one failing 3 times rests for a day.
  single {
    OpenCodeCatalog(
      get(),
      LlmClient.defaultHttpClient(),
      dataDir.resolve(OPENCODE_PAUSES_FILE).toPath(),
    )
  }
  single { OpenCodeClient(get(), LlmClient.defaultHttpClient(), catalog = get()) }
  single<AiUsageRecorder> { RoomAiUsageRecorder(get<ServerDatabase>().aiUsageDao()) }
  // The AI instructions, as edited in Settings; read before every call.
  single {
    val db = get<ServerDatabase>()
    PromptTrials(db.jobsDao(), db.resumeDao(), db.advancedMatchingDao(), get())
  }
  single {
    val scope = this
    PromptBook(get<ServerDatabase>().aiPromptsDao()) { id, text ->
      scope.get<PromptTrials>().run(id, text)
    }
  } bind AiPromptsRepository::class
}

private const val OPENCODE_DIR = "opencode"
private const val MODELS_DIR = "models"
private const val MODEL_CHECKS_FILE = "model-checks.json"

private const val OPENCODE_PAUSES_FILE = "opencode-paused-models.txt"
