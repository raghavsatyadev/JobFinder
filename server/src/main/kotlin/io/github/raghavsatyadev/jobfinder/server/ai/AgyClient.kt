package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AgyModels
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Sends one prompt to the user's signed-in Antigravity CLI (`agy`) and gives back the JSON text,
 * like an online key does in [LlmClient].
 *
 * Each call is one `agy` child in print mode, sandboxed, in a fresh empty folder (no workspace),
 * with slash commands off. The prompt goes in on stdin as one stream-json message, so its length
 * and quotes never meet the command line; the schema goes in a file beside the empty folder. Both
 * are deleted afterwards. `--dangerously-skip-permissions` is never passed, and the app never reads
 * agy's sign-in files.
 *
 * Out of quota or rate-limited, agy rests a while and [LlmClient] hands the call to the next route.
 * A crash or an answer without JSON gets one more try after a pause.
 */
class AgyClient(
  private val findExecutable: () -> String? = ::findAgy,
  private val clock: () -> Long = System::currentTimeMillis,
  private val semaphore: Semaphore = Semaphore(PARALLEL_CALLS),
) {
  private val log = LoggerFactory.getLogger(AgyClient::class.java)
  @Volatile private var restUntil = 0L
  @Volatile private var version: String? = null

  /** The `agy` executable; `null` when it isn't installed. */
  fun command(): String? = findExecutable()

  /** `agy --version` (e.g. "1.4.0"); `null` when not installed or it doesn't answer. */
  suspend fun version(): String? {
    version?.let {
      return it
    }
    val exe = command() ?: return null
    return withContext(Dispatchers.IO) {
      runCatching {
        val child =
          ProcessBuilder(exe, "--version")
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
            .start()
        val out = CompletableFuture.supplyAsync { child.inputStream.bufferedReader().readText() }
        if (!child.waitFor(VERSION_TIMEOUT_S, TimeUnit.SECONDS)) {
          kill(child)
          null
        } else
          out
            .get(VERSION_TIMEOUT_S, TimeUnit.SECONDS)
            .lineSequence()
            .lastOrNull {
              it.isNotBlank()
            }
            ?.trim()
      }
        .getOrNull()
        ?.also { version = it }
    }
  }

  /**
   * The JSON answer to [system] and [user], fitting [schema], from [model] (one of
   * [AgyModels.LEVELS]). Throws [LlmException] when agy didn't answer or is resting.
   */
  suspend fun chat(
    model: String,
    system: String,
    user: String,
    schema: JsonObject,
  ): ChatCompletion {
    if (restUntil > clock()) throw LlmException("agy is resting after a limit")
    val exe = command() ?: throw LlmException("The Antigravity CLI (agy) is not installed")
    val level = model.trim().ifEmpty { AgyModels.DEFAULT }
    val prompt = prompt(system, user, schema)
    return semaphore.withPermit {
      var last: Exception? = null
      for (attempt in 0..RETRIES) {
        if (attempt > 0) delay(BACKOFF_MS * attempt)
        try {
          return@withPermit ask(exe, level, prompt, schema)
        } catch (e: CancellationException) {
          throw e
        } catch (e: QuotaException) {
          restUntil = clock() + e.restMs
          log.info("agy rests {} min: {}", e.restMs / 60_000, e.message)
          throw LlmException("agy is out of quota: ${e.message}", e)
        } catch (e: PassingException) {
          last = e
        } catch (e: LlmException) {
          throw e
        } catch (e: Exception) {
          throw LlmException("agy failed: ${e.message ?: e::class.simpleName}", e)
        }
      }
      throw LlmException("agy failed: ${last?.message}", last)
    }
  }

  private suspend fun ask(
    exe: String,
    model: String,
    prompt: String,
    schema: JsonObject,
  ): ChatCompletion {
    val root = withContext(Dispatchers.IO) { Files.createTempDirectory(TEMP_PREFIX) }
    try {
      val work = root.resolve("work")
      val schemaFile = root.resolve("schema.json")
      val input = buildJsonObject {
        put("event", "user")
        put("message", buildJsonObject { put("content", prompt) })
      }
      val (exit, out) =
        runInterruptible(Dispatchers.IO) { run(exe, model, work, schemaFile, schema, input) }
      val result =
        out
          .lineSequence()
          .mapNotNull { line ->
            runCatching { JSON.parseToJsonElement(line.trim()).jsonObject }.getOrNull()
          }
          .lastOrNull { it["event"]?.jsonPrimitive?.contentOrNull == "result" }
          ?.get("result")
          ?.jsonObject ?: throw PassingException("agy gave no result (exit $exit)")
      val status = result["status"]?.jsonPrimitive?.contentOrNull
      if (status != "SUCCESS" || exit != 0) {
        val error =
          result["error"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            ?: "status $status"
        val lower = error.lowercase()
        when {
          QUOTA_WORDS.any { it in lower } -> throw QuotaException(error, QUOTA_REST_MS)
          RATE_WORDS.any { it in lower } -> throw QuotaException(error, RATE_REST_MS)
          else -> throw LlmException("agy $model: ${error.take(300)} (exit $exit)")
        }
      }
      val json =
        (result["structured_output"] as? JsonObject)?.toString()
          ?: result["response"]?.jsonPrimitive?.contentOrNull?.let(::jsonOf)
          ?: throw PassingException("agy $model answered without JSON")
      val usage = result["usage"] as? JsonObject
      val inTokens = usage?.get("input_tokens")?.jsonPrimitive?.longOrNull ?: (prompt.length / 4L)
      val output = usage?.get("output_tokens")?.jsonPrimitive?.longOrNull ?: (json.length / 4L)
      return ChatCompletion(json, inTokens, output)
    } finally {
      withContext(Dispatchers.IO) { runCatching { root.toFile().deleteRecursively() } }
    }
  }

  /** One `agy` child: the exit code and its stdout. Interrupting this kills the child. */
  private fun run(
    exe: String,
    model: String,
    work: Path,
    schemaFile: Path,
    schema: JsonObject,
    input: JsonObject,
  ): Pair<Int, String> {
    Files.createDirectories(work)
    Files.writeString(schemaFile, schema.toString())
    val child =
      ProcessBuilder(
          exe,
          "--model",
          model,
          "--input-format",
          "stream-json",
          "--output-format",
          "stream-json",
          "--sandbox",
          "--disable-slash-commands",
          "--print-timeout",
          "${PRINT_TIMEOUT_S}s",
          "--json-schema",
          schemaFile.toAbsolutePath().toString(),
        )
        .directory(work.toFile())
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    try {
      val out = CompletableFuture.supplyAsync { child.inputStream.bufferedReader().readText() }
      child.outputStream.bufferedWriter(Charsets.UTF_8).use {
        it.write(input.toString())
        it.write("\n")
      }
      // A timeout isn't tried again: the next route takes the call sooner.
      if (!child.waitFor(PROCESS_TIMEOUT_S, TimeUnit.SECONDS)) {
        throw LlmException("agy timed out after $PROCESS_TIMEOUT_S s")
      }
      return child.exitValue() to out.get(READ_TIMEOUT_S, TimeUnit.SECONDS)
    } catch (e: PassingException) {
      throw e
    } catch (e: LlmException) {
      throw e
    } catch (e: InterruptedException) {
      throw e
    } catch (e: Exception) {
      throw PassingException(e.message ?: e::class.simpleName.orEmpty(), e)
    } finally {
      if (child.isAlive) kill(child)
    }
  }

  /** A failure that may pass: crash, timeout, no JSON. */
  private class PassingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

  /** Out of quota or rate-limited: agy rests [restMs]. */
  private class QuotaException(message: String, val restMs: Long) : Exception(message)

  companion object {
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val PARALLEL_CALLS = 2
    private const val RETRIES = 1
    private const val BACKOFF_MS = 2_000L
    private const val PRINT_TIMEOUT_S = 90L
    private const val PROCESS_TIMEOUT_S = 120L
    private const val READ_TIMEOUT_S = 10L
    private const val VERSION_TIMEOUT_S = 10L
    private const val KILL_WAIT_S = 3L
    private const val RATE_REST_MS = 5 * 60_000L
    private const val QUOTA_REST_MS = 60 * 60_000L
    private const val TEMP_PREFIX = "jobfinder-agy-"

    private val QUOTA_WORDS = listOf("quota", "resource_exhausted", "resource exhausted")
    private val RATE_WORDS =
      listOf("rate limit", "rate-limit", "ratelimit", "too many requests", "429", "limit reached")

    private val WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** The [LlmTarget] for [model] in a task's list; [LlmClient] sends it here. */
    fun target(model: String): LlmTarget =
      LlmTarget(
        LlmConfig(
          provider = AiProvider.CUSTOM,
          baseUrl = "agy",
          model = model.ifBlank { AgyModels.DEFAULT },
        ),
        AiRoute.AGY_ID,
      )

    private fun prompt(system: String, user: String, schema: JsonObject): String = buildString {
      append(system.trim())
      append(
        "\n\nAnswer from the text in this message only. Never call tools, never run commands" +
          " and never read files."
      )
      append("\nReply with one JSON object only, no other text, that fits this JSON schema:\n")
      append(schema.toString())
      append("\n\n")
      append(user.trim())
    }

    /** The JSON object in [text]: a ```json fence and talk around it are dropped. */
    fun jsonOf(text: String): String? {
      val unfenced =
        text.trim().let { t ->
          if (!t.startsWith("```")) t else t.substringAfter('\n').substringBeforeLast("```").trim()
        }
      // agy may put a sentence before the JSON; try the last line first, then the widest braces.
      unfenced
        .lineSequence()
        .lastOrNull { it.isNotBlank() }
        ?.trim()
        ?.let { line ->
          if (runCatching { JSON.parseToJsonElement(line).jsonObject }.isSuccess) return line
        }
      return OpenCodeClient.jsonOf(unfenced)
    }

    private fun kill(child: Process) {
      runCatching {
        val descendants = child.descendants().toList()
        child.destroy()
        descendants.forEach { it.destroy() }
        if (!child.waitFor(KILL_WAIT_S, TimeUnit.SECONDS)) child.destroyForcibly()
        descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
      }
    }

    private fun nullFile() = File(if (WINDOWS) "NUL" else "/dev/null")

    /** `agy` on PATH, else where its installer puts it (`%LOCALAPPDATA%\agy\bin` on Windows). */
    fun findAgy(): String? {
      val name = if (WINDOWS) "agy.exe" else "agy"
      val dirs =
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
      for (dir in dirs) {
        val bin = File(dir.trim('"'), name)
        if (bin.isFile && (WINDOWS || bin.canExecute())) return bin.absolutePath
      }
      val home =
        if (WINDOWS) System.getenv("LOCALAPPDATA")?.let { File(it, "agy/bin") }
        else System.getProperty("user.home")?.let { File(it, ".local/bin") }
      return home?.let { File(it, name) }?.takeIf { it.isFile }?.absolutePath
    }
  }
}
