package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The wire formats: the OpenAI-compatible chat API and Anthropic's Messages API. */
internal object LlmWire {
  class Request(val path: String, val body: String, val read: (String) -> ChatCompletion)

  fun openAiRequest(
    config: LlmConfig,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    strict: Boolean,
  ): Request {
    val local = config.provider == AiProvider.OLLAMA
    val body = buildJsonObject {
      put("model", config.model)
      put(
        "messages",
        buildJsonArray {
          add(
            message(
              "system",
              if (strict) system
              else
                "$system" +
                  "\n\nReply with one JSON object that fits this JSON schema:\n" +
                  "$schema",
            )
          )
          add(message("user", user))
        },
      )
      if (config.provider == AiProvider.OPENAI) {
        // OpenAI's reasoning models take this name, and spend part of it thinking.
        put("max_completion_tokens", maxTokens * ONLINE_TOKEN_ROOM)
      } else {
        put("max_tokens", if (local) maxTokens else maxTokens * ONLINE_TOKEN_ROOM)
      }
      put("stream", false)
      // The same job always gets the same answer. OpenAI's reasoning models only take the default.
      if (config.provider != AiProvider.OPENAI) put("temperature", 0.0)
      // No thinking on the local model: the content stays plain JSON and comes faster.
      if (local) put("reasoning_effort", "none")
      put(
        "response_format",
        buildJsonObject {
          if (strict) {
            put("type", "json_schema")
            put(
              "json_schema",
              buildJsonObject {
                put("name", schemaName)
                put("strict", true)
                put("schema", schema)
              },
            )
          } else {
            put("type", "json_object")
          }
        },
      )
    }
    return Request("chat/completions", body.toString()) { text -> readOpenAi(config, text) }
  }

  fun readOpenAi(config: LlmConfig, text: String): ChatCompletion {
    val parsed =
      try {
        JSON.decodeFromString(ChatResponse.serializer(), text)
      } catch (e: Exception) {
        throw LlmException("Unreadable ${name(config)} response: ${e.message}", e)
      }
    val choice =
      parsed.choices.firstOrNull() ?: throw LlmException("${name(config)} returned no choices")
    if (choice.finishReason != "stop") {
      throw LlmException("${name(config)} response did not finish: ${choice.finishReason}")
    }
    val content = choice.message?.content
    if (content.isNullOrBlank()) throw LlmException("${name(config)} response has no content")
    return ChatCompletion(
      content = content,
      inputTokens = parsed.usage?.promptTokens ?: 0,
      outputTokens = parsed.usage?.completionTokens ?: 0,
    )
  }

  /** Anthropic gets the schema as the one tool it must call; the tool's input is the answer. */
  fun anthropicRequest(
    config: LlmConfig,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
  ): Request {
    val body = buildJsonObject {
      put("model", config.model)
      put("max_tokens", maxTokens * ONLINE_TOKEN_ROOM)
      put("temperature", 0.0)
      put("system", system)
      put("messages", buildJsonArray { add(message("user", user)) })
      put(
        "tools",
        buildJsonArray {
          add(
            buildJsonObject {
              put("name", schemaName)
              put("description", "Give the answer.")
              put("input_schema", schema)
            }
          )
        },
      )
      put(
        "tool_choice",
        buildJsonObject {
          put("type", "tool")
          put("name", schemaName)
        },
      )
    }
    return Request("messages", body.toString()) { text -> readAnthropic(text) }
  }

  fun readAnthropic(text: String): ChatCompletion {
    val parsed =
      try {
        JSON.decodeFromString(AnthropicResponse.serializer(), text)
      } catch (e: Exception) {
        throw LlmException("Unreadable Anthropic response: ${e.message}", e)
      }
    if (parsed.stopReason != "tool_use" && parsed.stopReason != "end_turn") {
      throw LlmException("Anthropic response did not finish: ${parsed.stopReason}")
    }
    val input =
      parsed.content.firstOrNull { it.type == "tool_use" }?.input
        ?: throw LlmException("Anthropic response has no answer")
    return ChatCompletion(
      content = input.toString(),
      inputTokens = parsed.usage?.inputTokens ?: 0,
      outputTokens = parsed.usage?.outputTokens ?: 0,
    )
  }

  fun HttpRequestBuilder.authorize(config: LlmConfig) {
    val key = config.apiKey ?: return
    when (config.provider) {
      AiProvider.ANTHROPIC -> {
        header("x-api-key", key)
        header("anthropic-version", ANTHROPIC_VERSION)
      }
      AiProvider.OLLAMA -> Unit
      else -> header(HttpHeaders.Authorization, "Bearer $key")
    }
    if (config.provider == AiProvider.OPENROUTER) header("X-Title", "JobFinder")
  }

  fun message(role: String, content: String) = buildJsonObject {
    put("role", role)
    put("content", content)
  }

  @Serializable
  data class ChatResponse(val choices: List<Choice> = emptyList(), val usage: Usage? = null)

  @Serializable
  data class Choice(
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
  )

  @Serializable data class ResponseMessage(val content: String? = null)

  @Serializable
  data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
  )

  @Serializable
  data class AnthropicResponse(
    val content: List<AnthropicBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
  )

  @Serializable data class AnthropicBlock(val type: String, val input: JsonElement? = null)

  @Serializable
  data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
  )

  val JSON = Json { ignoreUnknownKeys = true }

  const val ANTHROPIC_VERSION = "2023-06-01"

  /** Words in a 400 answer that mean the strict schema was the problem. */
  val SCHEMA_WORDS = listOf("response_format", "json_schema", "schema", "structured")

  /**
   * Online models may think before answering, inside the same token budget; the local model is told
   * not to. Room for that, so a long review doesn't stop at the limit.
   */
  const val ONLINE_TOKEN_ROOM = 4

  fun name(config: LlmConfig) = name(config.provider)

  /** The site's name as the user knows it. */
  fun name(provider: AiProvider) =
    when (provider) {
      AiProvider.OLLAMA -> "Ollama"
      AiProvider.OPENAI -> "OpenAI"
      AiProvider.ANTHROPIC -> "Anthropic"
      AiProvider.GEMINI -> "Gemini"
      AiProvider.OPENROUTER -> "OpenRouter"
      AiProvider.NVIDIA -> "NVIDIA"
      AiProvider.GROQ -> "Groq"
      AiProvider.HUGGINGFACE -> "Hugging Face"
      AiProvider.CUSTOM -> "The AI server"
    }

  /** Some providers echo part of a wrong key back; never let it reach a log. */
  fun hide(config: LlmConfig, text: String): String {
    val key = config.apiKey ?: return text
    var out = text.replace(key, "***")
    if (key.length > 8) out = out.replace(key.take(8), "***").replace(key.takeLast(4), "***")
    return out
  }
}

internal class RetryableException(message: String, cause: Throwable? = null) :
  Exception(message, cause)

/** HTTP 429: the key is out of quota for now. */
internal class RateLimitedException(message: String) : Exception(message)

/** HTTP 400 about the response format: the server doesn't take strict JSON schemas. */
internal class SchemaRefusedException(message: String) : Exception(message)
