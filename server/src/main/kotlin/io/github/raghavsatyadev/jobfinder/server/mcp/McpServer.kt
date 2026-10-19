package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * A tool an agent calls. [call] gets the arguments and returns what the agent reads: a [String] is
 * sent as is, anything else as JSON. A thrown [McpToolError] or [ApiException] comes back as a tool
 * error the agent can read and fix; other failures are logged and answered with a generic message.
 */
class McpTool(
  val name: String,
  val description: String,
  val inputSchema: JsonObject = McpSchema.obj(),
  /** Reads only: agents may call it without asking the user. */
  val readOnly: Boolean = false,
  /** Removes something the user made. */
  val destructive: Boolean = false,
  val call: suspend (JsonObject) -> Any,
)

/** A text document an agent can read, such as a help topic. */
class McpResource(
  val uri: String,
  val name: String,
  val description: String,
  val mimeType: String = "text/markdown",
  val read: suspend () -> String,
)

/** A ready-made request the user can start in their agent; [render] fills in the arguments. */
class McpPrompt(
  val name: String,
  val description: String,
  val arguments: List<McpPromptArgument> = emptyList(),
  val render: suspend (Map<String, String>) -> String,
)

class McpPromptArgument(val name: String, val description: String, val required: Boolean = false)

/** A mistake in a tool call the agent can fix; its message reaches the agent. */
class McpToolError(message: String) : Exception(message)

/**
 * The Model Context Protocol over JSON-RPC 2.0, without sessions: every request stands alone, so
 * one POST is one answer (the "Streamable HTTP" transport answering with plain JSON). Supports
 * tools, resources and prompts.
 */
class McpServer(
  private val name: String,
  private val version: String,
  private val instructions: String,
  private val tools: List<McpTool>,
  private val resources: List<McpResource>,
  private val prompts: List<McpPrompt>,
) {
  private val log = LoggerFactory.getLogger(McpServer::class.java)
  private val toolsByName = tools.associateBy { it.name }

  /** The answer to one HTTP body, or `null` when it held only notifications. */
  suspend fun handle(body: String): String? {
    val parsed =
      try {
        JSON.parseToJsonElement(body)
      } catch (_: Exception) {
        return JSON.encodeToString(
          JsonElement.serializer(),
          error(JsonNull, PARSE_ERROR, "Not JSON"),
        )
      }
    val answer =
      when (parsed) {
        is JsonArray ->
          parsed.mapNotNull { answer(it) }.takeIf { it.isNotEmpty() }?.let(::JsonArray)
        else -> answer(parsed)
      }
    return answer?.let { JSON.encodeToString(JsonElement.serializer(), it) }
  }

  private suspend fun answer(message: JsonElement): JsonObject? {
    val request = message as? JsonObject ?: return error(JsonNull, INVALID_REQUEST, "Not a request")
    val id = request["id"]
    val method =
      (request["method"] as? JsonPrimitive)?.contentOrNull
        ?: return id?.let { error(it, INVALID_REQUEST, "No method") }
    // Notifications (no id) and answers to our own requests get no reply.
    if (id == null || id is JsonNull) return null
    val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())
    return try {
      result(id, dispatch(method, params) ?: return error(id, METHOD_NOT_FOUND, method))
    } catch (e: CancellationException) {
      throw e
    } catch (e: InvalidParams) {
      error(id, INVALID_PARAMS, e.message ?: "Invalid params")
    } catch (e: Exception) {
      log.error("MCP {} failed", method, e)
      error(id, INTERNAL_ERROR, "Internal error")
    }
  }

  private suspend fun dispatch(method: String, params: JsonObject): JsonElement? =
    when (method) {
      "initialize" -> initialize(params)
      "ping" -> JsonObject(emptyMap())
      "tools/list" -> buildJsonObject { put("tools", JsonArray(tools.map(::describe))) }
      "tools/call" -> callTool(params)
      "resources/list" -> buildJsonObject { put("resources", JsonArray(resources.map(::describe))) }
      "resources/templates/list" -> buildJsonObject { putJsonArray("resourceTemplates") {} }
      "resources/read" -> readResource(params)
      "prompts/list" -> buildJsonObject { put("prompts", JsonArray(prompts.map(::describe))) }
      "prompts/get" -> getPrompt(params)
      "logging/setLevel" -> JsonObject(emptyMap())
      else -> null
    }

  private fun initialize(params: JsonObject): JsonObject {
    val asked = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
    return buildJsonObject {
      put("protocolVersion", asked?.takeIf { it in VERSIONS } ?: VERSIONS.first())
      putJsonObject("capabilities") {
        putJsonObject("tools") { put("listChanged", false) }
        putJsonObject("resources") {
          put("listChanged", false)
          put("subscribe", false)
        }
        putJsonObject("prompts") { put("listChanged", false) }
      }
      putJsonObject("serverInfo") {
        put("name", name)
        put("title", "JobFinder")
        put("version", version)
      }
      put("instructions", instructions)
    }
  }

  private suspend fun callTool(params: JsonObject): JsonObject {
    val toolName = params.string("name") ?: throw InvalidParams("No tool name")
    val tool = toolsByName[toolName] ?: throw InvalidParams("Unknown tool: $toolName")
    val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
    val (text, failed) =
      try {
        when (val out = tool.call(arguments)) {
          is String -> out to false
          is JsonElement -> PRETTY.encodeToString(JsonElement.serializer(), out) to false
          else -> error("Tool $toolName returned ${out::class.simpleName}")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: McpToolError) {
        (e.message ?: "Failed") to true
      } catch (e: ApiException) {
        (e.message ?: e.code.defaultMessage) to true
      } catch (e: IllegalArgumentException) {
        (e.message ?: "Invalid arguments") to true
      } catch (e: Exception) {
        log.error("MCP tool {} failed", toolName, e)
        "The app could not do that: ${e::class.simpleName}. Its log has the details." to true
      }
    return buildJsonObject {
      putJsonArray("content") {
        add(
          buildJsonObject {
            put("type", "text")
            put("text", text)
          }
        )
      }
      put("isError", failed)
    }
  }

  private suspend fun readResource(params: JsonObject): JsonObject {
    val uri = params.string("uri") ?: throw InvalidParams("No uri")
    val resource =
      resources.firstOrNull { it.uri == uri } ?: throw InvalidParams("No resource $uri")
    return buildJsonObject {
      putJsonArray("contents") {
        add(
          buildJsonObject {
            put("uri", resource.uri)
            put("mimeType", resource.mimeType)
            put("text", resource.read())
          }
        )
      }
    }
  }

  private suspend fun getPrompt(params: JsonObject): JsonObject {
    val promptName = params.string("name") ?: throw InvalidParams("No prompt name")
    val prompt = prompts.firstOrNull { it.name == promptName } ?: throw InvalidParams(promptName)
    val args =
      (params["arguments"] as? JsonObject)
        ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
        ?.toMap()
        .orEmpty()
    val missing = prompt.arguments.filter { it.required && args[it.name].isNullOrBlank() }
    if (missing.isNotEmpty()) throw InvalidParams("Missing ${missing.joinToString { it.name }}")
    return buildJsonObject {
      put("description", prompt.description)
      putJsonArray("messages") {
        add(
          buildJsonObject {
            put("role", "user")
            putJsonObject("content") {
              put("type", "text")
              put("text", prompt.render(args))
            }
          }
        )
      }
    }
  }

  private fun describe(tool: McpTool) = buildJsonObject {
    put("name", tool.name)
    put("description", tool.description)
    put("inputSchema", tool.inputSchema)
    putJsonObject("annotations") {
      put("readOnlyHint", tool.readOnly)
      put("destructiveHint", tool.destructive)
      put("openWorldHint", false)
    }
  }

  private fun describe(resource: McpResource) = buildJsonObject {
    put("uri", resource.uri)
    put("name", resource.name)
    put("description", resource.description)
    put("mimeType", resource.mimeType)
  }

  private fun describe(prompt: McpPrompt) = buildJsonObject {
    put("name", prompt.name)
    put("description", prompt.description)
    putJsonArray("arguments") {
      prompt.arguments.forEach { arg ->
        add(
          buildJsonObject {
            put("name", arg.name)
            put("description", arg.description)
            put("required", arg.required)
          }
        )
      }
    }
  }

  private fun result(id: JsonElement, result: JsonElement) = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    put("result", result)
  }

  private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    putJsonObject("error") {
      put("code", code)
      put("message", message)
    }
  }

  private class InvalidParams(message: String) : Exception(message)

  companion object {
    /** Newest first; an unknown version asked for gets the newest. */
    val VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

    private const val PARSE_ERROR = -32700
    private const val INVALID_REQUEST = -32600
    private const val METHOD_NOT_FOUND = -32601
    private const val INVALID_PARAMS = -32602
    private const val INTERNAL_ERROR = -32603

    private val JSON = Json { ignoreUnknownKeys = true }
    private val PRETTY = Json { prettyPrint = true }
  }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** JSON Schema pieces for tool inputs. */
object McpSchema {
  fun obj(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()) =
    buildJsonObject {
      put("type", "object")
      putJsonObject("properties") { properties.forEach { (k, v) -> put(k, v) } }
      if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
      put("additionalProperties", false)
    }

  fun string(description: String, enum: List<String>? = null) = buildJsonObject {
    put("type", "string")
    put("description", description)
    if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
  }

  fun integer(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
  }

  fun boolean(description: String) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
  }

  fun strings(description: String) = buildJsonObject {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "string") }
  }

  fun any(description: String) = buildJsonObject {
    put("type", "object")
    put("description", description)
  }
}

/** Typed reads of tool arguments; a missing or wrong one is a [McpToolError]. */
fun JsonObject.str(key: String): String =
  optStr(key)?.takeIf { it.isNotBlank() } ?: throw McpToolError("\"$key\" is required.")

fun JsonObject.optStr(key: String): String? =
  (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.long(key: String): Long =
  optLong(key) ?: throw McpToolError("\"$key\" is required and must be a number.")

fun JsonObject.optLong(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

fun JsonObject.optBool(key: String): Boolean? =
  (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

fun JsonObject.optStrings(key: String): List<String>? =
  (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }

fun JsonObject.obj(key: String): JsonObject =
  (this[key] as? JsonObject) ?: throw McpToolError("\"$key\" is required and must be an object.")
