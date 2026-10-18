package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Keeps secrets such as API keys outside the database, logs and backups. On Windows it is DPAPI,
 * readable only by this Windows user.
 */
interface SecretStore {
  /** `false` where keys can't be kept safely; online providers are then unavailable. */
  val available: Boolean

  fun get(name: String): String?

  fun put(name: String, value: String)

  fun remove(name: String)
}

/** Keeps secrets in memory only; for tests. */
class MemorySecretStore : SecretStore {
  private val values = ConcurrentHashMap<String, String>()

  override val available = true

  override fun get(name: String): String? = values[name]

  override fun put(name: String, value: String) {
    values[name] = value
  }

  override fun remove(name: String) {
    values.remove(name)
  }
}

/** Where secrets can't be kept: no keys, so only the local model works. */
object NoSecretStore : SecretStore {
  override val available = false

  override fun get(name: String): String? = null

  override fun put(name: String, value: String) =
    throw IllegalStateException("API keys can't be kept safely on this computer")

  override fun remove(name: String) = Unit
}

/** The result of the "Test" button. */
sealed interface AiCheck {
  data object Ok : AiCheck

  data object KeyMissing : AiCheck

  data class Failed(val reason: String) : AiCheck
}

/**
 * The AI choice: provider and model in the settings row, the key in [secrets] (one per provider, so
 * switching back keeps it). [config] is what [LlmClient] reads before every call.
 */
class AiService(
  private val settings: LocalSettingsRepository,
  private val secrets: SecretStore,
) {
  suspend fun settings(): AiSettings = settings.getAiSettings()

  fun hasKey(provider: AiProvider): Boolean = !secrets.get(keyName(provider)).isNullOrBlank()

  val keysAvailable: Boolean
    get() = secrets.available

  /** Saves [choice]. [key]: `null` keeps the saved one, blank removes it. */
  suspend fun save(choice: AiSettings, key: String?): AiSettings {
    if (key != null) {
      if (key.isBlank()) secrets.remove(keyName(choice.provider))
      else secrets.put(keyName(choice.provider), key.trim())
    }
    return settings.updateAiSettings(choice)
  }

  suspend fun config(): LlmConfig {
    val choice = settings()
    return LlmConfig.of(choice, secrets.get(keyName(choice.provider)))
  }

  /** Asks [choice]'s model one small question, with [key] or the saved one. */
  suspend fun check(client: LlmClient, choice: AiSettings, key: String?): AiCheck {
    val usedKey = key?.trim()?.takeIf { it.isNotEmpty() } ?: secrets.get(keyName(choice.provider))
    if (choice.provider.needsKey && usedKey.isNullOrBlank()) return AiCheck.KeyMissing
    return try {
      val answer = client.test(LlmConfig.of(choice, usedKey), TEST_SCHEMA)
      if (answer.content.contains("true")) AiCheck.Ok
      else AiCheck.Failed("Unexpected answer: ${answer.content.take(100)}")
    } catch (e: LlmException) {
      AiCheck.Failed(e.message.orEmpty())
    } catch (e: IllegalArgumentException) {
      AiCheck.Failed(e.message.orEmpty())
    }
  }

  companion object {
    fun keyName(provider: AiProvider) = "ai-key-${provider.name.lowercase()}"

    private val TEST_SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      put("properties", buildJsonObject { put("ok", buildJsonObject { put("type", "boolean") }) })
      put("required", buildJsonArray { add("ok") })
      put("additionalProperties", false)
    }
  }
}
