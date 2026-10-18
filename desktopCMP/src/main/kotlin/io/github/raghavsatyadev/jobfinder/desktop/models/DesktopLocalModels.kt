package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.server.ai.AccuracyCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.HfQuant
import io.github.raghavsatyadev.jobfinder.ui.platform.HfRepo
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModel
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * [LocalModels] on Ollama: library and Hugging Face models are pulled by Ollama, a link is
 * downloaded by the app and handed to Ollama as a blob. Each is registered as `jobfinder-<name>`
 * ([ModelNames]) and then checked with [AccuracyCheck]. Only models the app added can be removed,
 * and the agent kit's `nimble` is never listed.
 */
class DesktopLocalModels(
  private val ollama: OllamaModels,
  private val huggingFace: HuggingFace,
  private val download: GgufDownload,
  private val profiler: suspend () -> PcProfile,
  private val accuracy: AccuracyCheck,
  private val checks: ModelCheckStore,
  /** Free bytes where Ollama keeps models, `null` when unknown. */
  private val freeBytes: () -> Long? = { ollamaModelsDir().usableSpace.takeIf { it > 0 } },
) : LocalModels {
  override suspend fun profile(): PcProfile = profiler()

  override suspend fun installed(): List<LocalModel>? {
    val models = ollama.list() ?: return null
    val saved = checks.all()
    return models
      .filterNot { isNimble(it.name) }
      .map {
        LocalModel(it.name, it.sizeBytes, saved[it.name] ?: saved[it.name.removeSuffix(LATEST)])
      }
      .sortedWith(
        compareByDescending<LocalModel> { it.name.startsWith(ModelNames.PREFIX) }.thenBy { it.name }
      )
  }

  override suspend fun searchHuggingFace(query: String): List<HfRepo> =
    if (query.isBlank()) emptyList() else huggingFace.search(query)

  override suspend fun quants(repo: String): List<HfQuant> = huggingFace.quants(repo)

  override suspend fun size(source: ModelSource): Long? = runCatching {
    when (source) {
      is ModelSource.Library -> null
      is ModelSource.HuggingFace ->
        huggingFace
          .quants(source.repo)
          .firstOrNull { it.name.equals(source.quant, true) }
          ?.sizeBytes
      is ModelSource.Link -> download.size(source.url)
    }
  }
    .getOrNull()

  override fun add(source: ModelSource): Flow<ModelProgress> = channelFlow {
    if (ollama.list() == null)
      return@channelFlow send(ModelProgress.Failed(ModelFailure.OLLAMA_NOT_RUNNING))
    val size = size(source)
    val free = freeBytes()
    if (size != null && free != null && size + SPACE_MARGIN > free) {
      return@channelFlow send(ModelProgress.Failed(ModelFailure.NO_SPACE))
    }
    val name = ModelNames.appName(source)
    val failure = fetchAndRegister(source, name)
    if (failure != null) return@channelFlow send(failure)
    runCheck(name)
  }
    .flowOn(Dispatchers.IO)

  override fun check(model: String): Flow<ModelProgress> = channelFlow {
    runCheck(model)
  }
    .flowOn(Dispatchers.IO)

  override suspend fun remove(model: String) {
    require(model.startsWith(ModelNames.PREFIX)) { "Only models the app added can be removed" }
    ollama.delete(model)
    checks.remove(model)
  }

  /** Gets [source] and registers it as [name]; the failure to show, or `null`. */
  private suspend fun ProducerScope<ModelProgress>.fetchAndRegister(
    source: ModelSource,
    name: String,
  ): ModelProgress.Failed? {
    val parameters = ModelNames.parameters(source)
    send(ModelProgress.Downloading(0, null))
    val onProgress = { done: Long, total: Long? ->
      trySend(ModelProgress.Downloading(done, total)).let {}
    }
    try {
      when (source) {
        is ModelSource.Link -> {
          val (file, digest) = download.fetch(source.url, source.sha256, onProgress)
          send(ModelProgress.Registering)
          try {
            ollama.pushBlob(file, digest)
            ollama.create(name, files = mapOf(file.name to digest), parameters = parameters)
          } finally {
            // Ollama keeps its own copy.
            file.delete()
          }
        }
        else -> {
          val pulled = ModelNames.pullName(source)!!
          ollama.pull(pulled, onProgress)
          send(ModelProgress.Registering)
          ollama.create(name, from = pulled, parameters = parameters)
        }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      return ModelProgress.Failed(failure(e), e.message)
    }
    return null
  }

  private suspend fun ProducerScope<ModelProgress>.runCheck(model: String) {
    try {
      val samples = accuracy.samples()
      send(ModelProgress.Checking(0, samples.size))
      val result =
        accuracy.run(model, samples) { done, total -> trySend(ModelProgress.Checking(done, total)) }
      checks.put(result)
      send(ModelProgress.Done(model, result))
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      send(ModelProgress.Failed(ModelFailure.REGISTER_FAILED, e.message))
    }
  }

  private fun failure(e: Exception): ModelFailure {
    val message = e.message.orEmpty().lowercase()
    return when {
      e is ChecksumMismatch -> ModelFailure.WRONG_CHECKSUM
      "not found" in message || "does not exist" in message || "404" in message ->
        ModelFailure.NOT_FOUND
      "space" in message -> ModelFailure.NO_SPACE
      e is OllamaException && "create" in message -> ModelFailure.REGISTER_FAILED
      e is IOException || e is OllamaException || e is IllegalStateException ->
        ModelFailure.DOWNLOAD_FAILED
      else -> ModelFailure.DOWNLOAD_FAILED
    }
  }

  private fun isNimble(name: String) = name.trim().lowercase().startsWith("nimble")

  private companion object {
    const val LATEST = ":latest"
    /** Room left after the download for Ollama and Windows. */
    const val SPACE_MARGIN = 2_000_000_000L
  }
}
