package io.github.raghavsatyadev.jobfinder.ui.platform

import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import kotlinx.coroutines.flow.Flow

/** This computer's memory and free disk, for the model tier. `null` where unknown. */
data class PcProfile(
  val ramGb: Double?,
  val gpuName: String?,
  val vramGb: Double?,
  /** Free space where Ollama keeps its models. */
  val freeDiskGb: Double?,
) {
  /**
   * The tier from the accuracy tests (`docs/model-accuracy.md`). A model "fits" only when it fits
   * on the graphics card: the part that spills into main memory runs on the processor, many times
   * slower, so main memory never counts as room for the model.
   */
  val tier: ModelTier
    get() {
      val ram = ramGb ?: return ModelTier.UNKNOWN
      val vram = vramGb ?: 0.0
      return when {
        ram >= TIER_FULL_RAM && vram >= TIER_FULL_FAST_VRAM -> ModelTier.FULL
        ram >= TIER_SPLIT_RAM && vram >= TIER_FULL_VRAM -> ModelTier.SPLIT
        ram >= TIER_8B_RAM && vram >= TIER_8B_VRAM -> ModelTier.MEDIUM
        ram >= TIER_4B_RAM && vram >= TIER_4B_VRAM -> ModelTier.SMALL
        else -> ModelTier.ONLINE
      }
    }

  private companion object {
    // A little under the round numbers: Windows reports a 32 GB PC as 31.8 GB and so on.
    const val TIER_FULL_RAM = 23.0
    /** The app's model is about 22.5 GB: all of it on the card. */
    const val TIER_FULL_FAST_VRAM = 23.0
    /** It runs with about 13 GB on the card and the rest in main memory. */
    const val TIER_SPLIT_RAM = 31.0
    const val TIER_FULL_VRAM = 11.5
    const val TIER_8B_RAM = 15.0
    const val TIER_8B_VRAM = 7.5
    const val TIER_4B_RAM = 7.5
    const val TIER_4B_VRAM = 3.5
  }
}

/** The model suggested for a PC, from the accuracy tests on the benchmark's own jobs. */
enum class ModelTier(
  /** What to add, `null` for [ONLINE] and [UNKNOWN]. */
  val source: ModelSource?
) {
  /** 24 GB+ on the graphics card: the benchmark itself, all of it on the card. */
  FULL(ModelSource.Library("qwen3.6:35b-a3b")),
  /**
   * 12–24 GB on the card and 32 GB+ memory: the benchmark runs, but about 9 GB of it on the
   * processor (2.7 s a job to filter, 26 s an ATS review), so free online AI should take the bulk.
   */
  SPLIT(ModelSource.Library("qwen3.6:35b-a3b")),
  /** 16 GB and 8 GB: Qwen3 8B agreed on 19 of 22 matching decisions. */
  MEDIUM(ModelSource.HuggingFace("unsloth/Qwen3-8B-GGUF", "Q4_K_M")),
  /** 8 GB and 4 GB: Qwen3 4B Instruct 2507, every answer valid, 17 of 22. */
  SMALL(ModelSource.HuggingFace("unsloth/Qwen3-4B-Instruct-2507-GGUF", "Q4_K_M")),
  /** Less: an online model with the user's own key does better than any model that fits. */
  ONLINE(null),
  UNKNOWN(null),
}

/** Where a model comes from. */
sealed interface ModelSource {
  /** A name from ollama.com/library, like `qwen3:8b`. */
  data class Library(val name: String) : ModelSource

  /** A GGUF repository on Hugging Face and one of its quantisations, like `Q4_K_M`. */
  data class HuggingFace(val repo: String, val quant: String) : ModelSource

  /** A direct link to a `.gguf` file, with its SHA-256 when the user has it. */
  data class Link(val url: String, val sha256: String? = null) : ModelSource
}

/** A model Ollama has. */
data class LocalModel(
  val name: String,
  val sizeBytes: Long,
  /** The last accuracy check, `null` when never checked. */
  val check: ModelCheck? = null,
)

/** A GGUF repository found on Hugging Face. */
data class HfRepo(val id: String, val downloads: Long, val likes: Long)

/** One quantisation in a [HfRepo]; [sizeBytes] adds up split files. */
data class HfQuant(val name: String, val sizeBytes: Long)

/** Why adding or checking a model stopped. */
enum class ModelFailure {
  OLLAMA_NOT_RUNNING,
  NOT_FOUND,
  NO_SPACE,
  DOWNLOAD_FAILED,
  /** The file's SHA-256 is not the one the user gave. */
  WRONG_CHECKSUM,
  /** Ollama could not make a model from it. */
  REGISTER_FAILED,
}

/** A model being added or checked. */
sealed interface ModelProgress {
  data class Downloading(val done: Long, val total: Long?) : ModelProgress

  data object Registering : ModelProgress

  data class Checking(val done: Int, val total: Int) : ModelProgress

  data class Done(val model: String, val check: ModelCheck) : ModelProgress

  data class Failed(val failure: ModelFailure, val detail: String? = null) : ModelProgress
}

/**
 * Local models on this computer: what Ollama has, adding one from the Ollama library, Hugging Face
 * or a link (registered with a 16k context and the model's sampling settings), and the accuracy
 * check against the answers already saved. Desktop only; nothing downloads before the user asks.
 */
interface LocalModels {
  suspend fun profile(): PcProfile

  /** `null` when Ollama does not answer. */
  suspend fun installed(): List<LocalModel>?

  suspend fun searchHuggingFace(query: String): List<HfRepo>

  suspend fun quants(repo: String): List<HfQuant>

  /** The download size of [source] when it can be known before downloading. */
  suspend fun size(source: ModelSource): Long?

  /** Downloads, registers and checks [source]; ends with [ModelProgress.Done] or `Failed`. */
  fun add(source: ModelSource): Flow<ModelProgress>

  /** Checks [model] again. */
  fun check(model: String): Flow<ModelProgress>

  suspend fun remove(model: String)
}
