package io.github.raghavsatyadev.jobfinder.desktop.mcp

import io.github.raghavsatyadev.jobfinder.server.mcp.McpSchema
import io.github.raghavsatyadev.jobfinder.server.mcp.McpTool
import io.github.raghavsatyadev.jobfinder.server.mcp.McpToolError
import io.github.raghavsatyadev.jobfinder.server.mcp.optStr
import io.github.raghavsatyadev.jobfinder.server.mcp.str
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.koin.core.Koin

/**
 * MCP tools for the local models in Ollama, as in Settings → AI → Local models. Adding and checking
 * run here in the background; the agent reads their progress with `get_local_model_progress`.
 * Downloads started here don't show in the app's own progress bars.
 */
class McpLocalModelTools(private val koin: Koin) : AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** The latest progress of each add or check started here, by model or source. */
  private val progress = ConcurrentHashMap<String, Job>()

  private class Job(val action: String, @Volatile var last: ModelProgress? = null) {
    val running: Boolean
      get() = last !is ModelProgress.Done && last !is ModelProgress.Failed
  }

  private fun models(): LocalModels =
    koin.getOrNull() ?: throw McpToolError("Local models aren't available in this app.")

  fun tools(): List<McpTool> = buildList {
    add(
      McpTool(
        "get_pc_profile",
        "This computer's memory, graphics card and free disk, and the local model the app suggests for it (or online AI when nothing that fits does well).",
        readOnly = true,
      ) {
        val p = models().profile()
        buildJsonObject {
          put("ramGb", p.ramGb)
          put("gpu", p.gpuName)
          put("vramGb", p.vramGb)
          put("freeDiskGb", p.freeDiskGb)
          put("tier", p.tier.name.lowercase())
          put("suggested", p.tier.source?.let(::describe))
        }
      }
    )
    add(
      McpTool(
        "list_local_models",
        "The models Ollama has, with size and the last accuracy check against the user's saved answers.",
        readOnly = true,
      ) {
        val installed =
          models().installed() ?: throw McpToolError("Ollama isn't running on this computer.")
        buildJsonArray {
          installed.forEach { m ->
            add(
              buildJsonObject {
                put("name", m.name)
                put("bytes", m.sizeBytes)
                put("check", m.check?.let { ApiJson.encodeToJsonElement(it) } ?: JsonNull)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "search_huggingface_models",
        "Finds GGUF model repositories on Hugging Face.",
        McpSchema.obj(
          "query" to McpSchema.string("Words to search for."),
          required = listOf("query"),
        ),
        readOnly = true,
      ) {
        buildJsonArray {
          models().searchHuggingFace(it.str("query")).forEach { r ->
            add(
              buildJsonObject {
                put("repo", r.id)
                put("downloads", r.downloads)
                put("likes", r.likes)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "list_model_quants",
        "The quantisations in a Hugging Face GGUF repository, with their download size.",
        McpSchema.obj(
          "repo" to McpSchema.string("Like unsloth/Qwen3-8B-GGUF."),
          required = listOf("repo"),
        ),
        readOnly = true,
      ) {
        buildJsonArray {
          models().quants(it.str("repo")).forEach { q ->
            add(
              buildJsonObject {
                put("quant", q.name)
                put("bytes", q.sizeBytes)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "get_model_size",
        "How much a model would download, before adding it. ${SOURCE_HELP}",
        SOURCE_SCHEMA,
        readOnly = true,
      ) {
        val source = source(it)
        buildJsonObject {
          put("source", describe(source))
          put("bytes", models().size(source))
        }
      }
    )
    add(
      McpTool(
        "add_local_model",
        "Downloads a model into Ollama and registers it, in the background. Downloads are large: tell the user the size (get_model_size) and get their yes first. Follow it with get_local_model_progress. ${SOURCE_HELP}",
        SOURCE_SCHEMA,
      ) {
        val source = source(it)
        start(describe(source), "add") { models().add(source) }
      }
    )
    add(
      McpTool(
        "check_local_model",
        "Checks a local model's answers against the user's saved ones, in the background. Follow it with get_local_model_progress.",
        McpSchema.obj(
          "model" to McpSchema.string("A name list_local_models gave."),
          required = listOf("model"),
        ),
      ) {
        val model = it.str("model")
        start(model, "check") { models().check(model) }
      }
    )
    add(
      McpTool(
        "get_local_model_progress",
        "How the adds and checks started with add_local_model and check_local_model are going.",
        readOnly = true,
      ) {
        buildJsonArray {
          progress.forEach { (key, job) ->
            add(
              buildJsonObject {
                put("model", key)
                put("action", job.action)
                put("running", job.running)
                put("progress", job.last?.let(::describe) ?: JsonNull)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "remove_local_model",
        "Deletes a model from Ollama. Ask the user first.",
        McpSchema.obj(
          "model" to McpSchema.string("A name list_local_models gave."),
          required = listOf("model"),
        ),
        destructive = true,
      ) {
        val model = it.str("model")
        if (progress[model]?.running == true) throw McpToolError("$model is still busy.")
        models().remove(model)
        progress.remove(model)
        "Removed $model."
      }
    )
  }

  /** Collects [flow] in the background, keeping its latest step for [key]. */
  private fun start(key: String, action: String, flow: () -> Flow<ModelProgress>): String {
    val job = Job(action)
    val previous = progress.putIfAbsent(key, job)
    if (previous != null) {
      if (previous.running) throw McpToolError("$key is already being ${previous.action}ed.")
      progress[key] = job
    }
    scope.launch {
      try {
        flow().collect { job.last = it }
        if (job.running) job.last = ModelProgress.Failed(ModelFailure.REGISTER_FAILED, "Stopped.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        job.last = ModelProgress.Failed(ModelFailure.DOWNLOAD_FAILED, e.message)
      }
    }
    return "Started ($action $key). Read get_local_model_progress for how it goes."
  }

  private fun source(args: JsonObject): ModelSource =
    when (args.str("kind")) {
      "library" -> ModelSource.Library(args.str("name"))
      "huggingface" -> ModelSource.HuggingFace(args.str("repo"), args.str("quant"))
      "link" -> {
        val url = args.str("url")
        if (!url.startsWith("https://")) throw McpToolError("Use an https:// link.")
        ModelSource.Link(url, args.optStr("sha256"))
      }
      else -> throw McpToolError("\"kind\" is library, huggingface or link.")
    }

  private fun describe(source: ModelSource): String =
    when (source) {
      is ModelSource.Library -> source.name
      is ModelSource.HuggingFace -> "hf.co/${source.repo}:${source.quant}"
      is ModelSource.Link -> source.url
    }

  private fun describe(step: ModelProgress) = buildJsonObject {
    when (step) {
      is ModelProgress.Downloading -> {
        put("step", "downloading")
        put("done", step.done)
        put("total", step.total)
      }
      ModelProgress.Registering -> put("step", "registering")
      is ModelProgress.Checking -> {
        put("step", "checking")
        put("done", step.done)
        put("total", step.total)
      }
      is ModelProgress.Done -> {
        put("step", "done")
        put("model", step.model)
        put("check", ApiJson.encodeToJsonElement(step.check))
      }
      is ModelProgress.Failed -> {
        put("step", "failed")
        put("failure", step.failure.name.lowercase())
        put("detail", step.detail)
      }
    }
  }

  override fun close() {
    scope.cancel()
  }

  private companion object {
    const val SOURCE_HELP =
      "Give kind library with name (like qwen3:8b), huggingface with repo and quant, or link with an https url to a GGUF file and its optional sha256."

    val SOURCE_SCHEMA =
      McpSchema.obj(
        "kind" to
          McpSchema.string("Where the model comes from.", listOf("library", "huggingface", "link")),
        "name" to McpSchema.string("kind library: a name from ollama.com/library."),
        "repo" to McpSchema.string("kind huggingface: the repository."),
        "quant" to McpSchema.string("kind huggingface: a quantisation list_model_quants gave."),
        "url" to McpSchema.string("kind link: an https link to a GGUF file."),
        "sha256" to McpSchema.string("kind link: the file's SHA-256, checked after the download."),
        required = listOf("kind"),
      )
  }
}
