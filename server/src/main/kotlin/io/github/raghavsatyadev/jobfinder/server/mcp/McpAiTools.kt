package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.ai.AddKeyResult
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.shared.model.AgyModels
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Tools for the main AI model, the online keys and which model does which work. */
internal object McpAiTools {
  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "get_ai_model",
        "The main AI model (provider, model, address) that does the work no route takes, whether a key is saved for each provider (never the key), and the providers to choose from.",
        readOnly = true,
      ) {
        aiModel(d)
      }
    )
    add(
      McpTool(
        "set_ai_model",
        "Sets the main AI model, as in Settings → AI model. The local model (ollama) is the default and keeps the resume on this computer; ask the user before choosing an online one. A key passes through this chat, so suggest the user pastes it in Settings instead unless they want you to set it.",
        McpSchema.obj(
          "provider" to
            McpSchema.string(
              "Provider; leave out to keep.",
              AiProvider.entries.map { it.name.lowercase() },
            ),
          "model" to
            McpSchema.string(
              "Model name; leave out to keep (or the provider's suggested one when the provider changes)."
            ),
          "base_url" to
            McpSchema.string("The provider's address; empty for its own. Needed for custom."),
          "key" to McpSchema.string("API key for this provider. Optional; never returned."),
          "remove_key" to McpSchema.boolean("Removes the saved key for this provider."),
        ),
      ) {
        val ai = aiService(d)
        val current = ai.settings()
        val provider = it.optStr("provider")?.let { p -> provider(p) } ?: current.provider
        val model =
          it.optStr("model")?.trim()?.takeIf(String::isNotEmpty)
            ?: if (provider == current.provider) current.model else provider.suggestedModel
        if (model.isBlank()) throw McpToolError("Give a model for $provider.")
        val baseUrl =
          if (it.containsKey("base_url")) it.optStr("base_url")?.trim()?.ifEmpty { null }
          else current.baseUrl.takeIf { provider == current.provider }
        if (provider == AiProvider.CUSTOM && baseUrl == null) {
          throw McpToolError("A custom provider needs base_url.")
        }
        val key =
          when {
            it.optBool("remove_key") == true -> ""
            else -> it.optStr("key")?.trim()?.takeIf(String::isNotEmpty)
          }
        if (key != null && key.isNotEmpty() && !ai.keysAvailable) {
          throw McpToolError("API keys can't be kept safely on this computer.")
        }
        ai.save(AiSettings(provider, model, baseUrl), key)
        aiModel(d)
      }
    )
    add(
      McpTool(
        "list_free_ai_providers",
        "Free online AI providers the app supports, none needing a payment card: where to make a key, the model, the free limits, the work each suits and whether it may keep prompts.",
        readOnly = true,
      ) {
        buildJsonArray {
          FreeAiCatalog.TIERS.forEach { t ->
            add(
              buildJsonObject {
                put("provider", t.provider.name.lowercase())
                put("keyPage", t.keyPage)
                put("model", t.model)
                put("requestsPerMinute", t.requestsPerMinute)
                put("requestsPerDay", t.requestsPerDay)
                put("tokensPerDay", t.tokensPerDay)
                putJsonArray("suits") { t.suits.forEach { s -> add(mcpJson(s)) } }
                put("mayKeepPrompts", t.keepsPrompts)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "list_ai_keys",
        "The online AI keys added (never the keys themselves), today's use and expiry.",
        readOnly = true,
      ) {
        keysJson(d)
      }
    )
    add(
      McpTool(
        "add_ai_key",
        "Adds an online AI key; the app checks it with the provider (found from the key's prefix) and keeps it in the computer's protected store. The key passes through this chat, so suggest the user pastes it in Settings → Free online AI instead unless they want you to add it.",
        McpSchema.obj(
          "key" to McpSchema.string("The API key."),
          "expires_in_days" to
            McpSchema.integer(
              "Days until the key expires, as set on the provider's site. Optional."
            ),
          required = listOf("key"),
        ),
      ) {
        val expiresAt =
          it.optLong("expires_in_days")?.let { days ->
            System.currentTimeMillis() + days * AiKey.DAY_MS
          }
        when (val result = d.keys.add(it.str("key"), expiresAt = expiresAt)) {
          is AddKeyResult.Added -> describeKey(result.key, 0, System.currentTimeMillis())
          AddKeyResult.UnknownProvider ->
            throw McpToolError("The key's prefix doesn't match a supported provider.")
          AddKeyResult.Refused -> throw McpToolError("The provider refused the key.")
        }
      }
    )
    add(
      McpTool(
        "remove_ai_key",
        "Removes an online AI key from the app. Ask the user first.",
        McpSchema.obj(
          "id" to McpSchema.string("Key id from list_ai_keys."),
          required = listOf("id"),
        ),
        destructive = true,
      ) {
        val id = it.str("id")
        if (d.keys.list().none { k -> k.id == id }) throw McpToolError("No key $id.")
        d.keys.remove(id)
        "Removed key $id."
      }
    )
    add(
      McpTool(
        "get_ai_routing",
        "Which models do which work, tried in order, with the main AI model and the OpenCode and Antigravity CLI choices. A task missing from 'routes' uses the default order. Read help topic ai-routing first.",
        readOnly = true,
      ) {
        routingJson(d)
      }
    )
    add(
      McpTool(
        "set_ai_routing",
        "Saves which models do which work: the same shape as get_ai_routing's 'routing' (routes by task, openCode, agy). Routes name a key id (from list_ai_keys), \"opencode-cli\", \"agy-cli\", or no keyId for the main model. Work with the resume (ats, tips, answers) should stay on the main model unless the user chooses otherwise.",
        McpSchema.obj(
          "routing" to McpSchema.any("The routing object."),
          required = listOf("routing"),
        ),
      ) {
        val routing = fromMcpJson<AiRouting>(it.obj("routing"), "routing object")
        save(d, routing)
      }
    )
    add(
      McpTool(
        "set_ai_clis",
        "Turns the OpenCode CLI (free models, no key) and the Antigravity CLI (agy, the user's Google sign-in) on or off as model sources, and picks their models. The CLI must be installed and signed in on this computer.",
        McpSchema.obj(
          "opencode_enabled" to McpSchema.boolean("Use the OpenCode CLI. Leave out to keep."),
          "opencode_model" to
            McpSchema.string("OpenCode model to try first; empty for every free model in order."),
          "agy_enabled" to McpSchema.boolean("Use the Antigravity CLI. Leave out to keep."),
          "agy_model" to McpSchema.string("Antigravity model.", AgyModels.LEVELS),
        ),
      ) {
        val current = d.keys.routing()
        val agyModel = it.optStr("agy_model")?.trim()
        if (agyModel != null && agyModel !in AgyModels.LEVELS) {
          throw McpToolError("Use one of ${AgyModels.LEVELS}.")
        }
        val updated =
          current.copy(
            openCode =
              current.openCode.copy(
                enabled = it.optBool("opencode_enabled") ?: current.openCode.enabled,
                model = it.optStr("opencode_model")?.trim() ?: current.openCode.model,
              ),
            agy =
              current.agy.copy(
                enabled = it.optBool("agy_enabled") ?: current.agy.enabled,
                model = agyModel ?: current.agy.model,
              ),
          )
        d.keys.setRouting(updated)
        routingJson(d)
      }
    )
  }

  private suspend fun save(d: McpDependencies, routing: AiRouting): JsonObject {
    val ids = d.keys.list().map { k -> k.id }.toSet()
    val unknown =
      routing.routes.values
        .flatten()
        .mapNotNull { r -> r.keyId }
        .filter { k -> k !in ids && k != AiRoute.OPEN_CODE_ID && k != AiRoute.AGY_ID }
    if (unknown.isNotEmpty()) throw McpToolError("Unknown key ids: ${unknown.distinct()}")
    // An older or unknown Antigravity model moves to the offered one, as the app's own picker does.
    d.keys.setRouting(
      routing.copy(agy = routing.agy.copy(model = AgyModels.current(routing.agy.model)))
    )
    return routingJson(d)
  }

  suspend fun routingJson(d: McpDependencies): JsonObject {
    val keys = d.keys.list()
    val routing = d.keys.routing()
    return buildJsonObject {
      put("mainModel", mcpJson(d.settings.getAiSettings()))
      put("routing", mcpJson(routing))
      putJsonObject("effective") {
        AiTask.entries.forEach { task ->
          put(mcpJson(task).toString().trim('"'), mcpJson(routing.forTask(task, keys)))
        }
      }
    }
  }

  suspend fun keysJson(d: McpDependencies) = buildJsonArray {
    val usage = d.keys.usageToday().associateBy { it.keyId }
    val now = System.currentTimeMillis()
    d.keys.list().forEach { k -> add(describeKey(k, usage[k.id]?.requests ?: 0, now)) }
  }

  suspend fun aiModel(d: McpDependencies): JsonObject {
    val ai = d.ai()
    val settings = ai?.settings() ?: d.settings.getAiSettings()
    return buildJsonObject {
      put("provider", settings.provider.name.lowercase())
      put("model", settings.model)
      put("baseUrl", settings.baseUrl)
      put("endpoint", settings.endpoint)
      put("keySaved", ai?.hasKey(settings.provider))
      putJsonArray("providers") {
        AiProvider.entries.forEach { p ->
          add(
            buildJsonObject {
              put("provider", p.name.lowercase())
              put("online", p.online)
              put("needsKey", p.needsKey)
              put("suggestedModel", p.suggestedModel)
              put("defaultBaseUrl", p.defaultBaseUrl)
              put("keySaved", ai?.hasKey(p))
            }
          )
        }
      }
    }
  }

  private fun aiService(d: McpDependencies): AiService =
    d.ai() ?: throw McpToolError("The AI model choice isn't available in this app.")

  private fun provider(wire: String): AiProvider =
    AiProvider.entries.firstOrNull { it.name.equals(wire.trim(), ignoreCase = true) }
      ?: throw McpToolError(
        "Unknown provider \"$wire\". Use one of ${AiProvider.entries.map { it.name.lowercase() }}."
      )
}
