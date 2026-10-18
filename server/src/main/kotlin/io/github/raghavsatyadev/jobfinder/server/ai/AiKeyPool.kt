package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.db.AiKeyEntity
import io.github.raghavsatyadev.jobfinder.server.db.AiKeysDao
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDao
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDefaults
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiKeyUsage
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One model to try for a call: on an added key, or ([keyId] `null`) the main AI choice. */
class LlmTarget(val config: LlmConfig, val keyId: String?)

/** What adding a key found. */
sealed interface AddKeyResult {
  data class Added(val key: AiKey) : AddKeyResult

  /** The key's prefix doesn't say where it is from. */
  data object UnknownProvider : AddKeyResult

  /** The provider refused the key. */
  data object Refused : AddKeyResult
}

/**
 * The online keys the user added, which model does which work ([AiRouting]) and each key's quota:
 * per minute in memory, per day in the database. A key the provider rate-limited rests until
 * [cooldownMs] has passed. Keys live in [secrets], never in the database.
 */
class AiKeyPool(
  private val keys: AiKeysDao,
  private val settings: SettingsDao,
  private val secrets: SecretStore,
  private val http: HttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
) {
  private val lock = Mutex()
  private val minute = ConcurrentHashMap<String, ArrayDeque<Long>>()
  private val restUntil = ConcurrentHashMap<String, Long>()
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun list(): List<AiKey> = keys.list().map { it.toModel() }

  /**
   * Saves [secret] as a new key. The provider comes from [provider] or the key's prefix, so a key
   * is never sent to a site it can't belong to. Limits start from the free tier. [expiresAt] is
   * what the user chose on the site; a site that reports the key's expiry overrides it.
   */
  suspend fun add(
    secret: String,
    provider: AiProvider? = null,
    label: String? = null,
    baseUrl: String? = null,
    expiresAt: Long? = null,
  ): AddKeyResult {
    val key = secret.trim()
    require(key.isNotEmpty()) { "The key is empty" }
    val found = provider ?: AiProvider.detect(key) ?: return AddKeyResult.UnknownProvider
    if (models(found, baseUrl, key) == null && found != AiProvider.ANTHROPIC) {
      return AddKeyResult.Refused
    }
    val tier = FreeAiCatalog.of(found)
    val entity =
      AiKeyEntity(
        id = UUID.randomUUID().toString(),
        provider = found.serialName(),
        label = label?.trim()?.takeIf { it.isNotEmpty() },
        baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() },
        requestsPerMinute = tier?.requestsPerMinute,
        requestsPerDay = tier?.requestsPerDay,
        tokensPerDay = tier?.tokensPerDay,
        createdAt = clock(),
        expiresAt = reportedExpiry(found, baseUrl, key) ?: expiresAt,
      )
    secrets.put(secretName(entity.id), key)
    keys.upsert(entity)
    return AddKeyResult.Added(entity.toModel())
  }

  /** Saves a key's label, address and limits; the key itself can't change here. */
  suspend fun update(key: AiKey) {
    val current = keys.get(key.id) ?: return
    keys.upsert(
      current.copy(
        label = key.label?.trim()?.takeIf { it.isNotEmpty() },
        baseUrl = key.baseUrl?.trim()?.takeIf { it.isNotEmpty() },
        requestsPerMinute = key.requestsPerMinute,
        requestsPerDay = key.requestsPerDay,
        tokensPerDay = key.tokensPerDay,
      )
    )
  }

  /** Records when [id] expires; `null` clears it. */
  suspend fun setExpiry(id: String, expiresAt: Long?) {
    val current = keys.get(id) ?: return
    keys.upsert(current.copy(expiresAt = expiresAt))
  }

  suspend fun remove(id: String) {
    secrets.remove(secretName(id))
    keys.delete(id)
    keys.deleteUsage(id)
    minute.remove(id)
    restUntil.remove(id)
  }

  /**
   * Every key with its secret, for a password-locked export only; a key whose secret is gone from
   * the store is left out.
   */
  suspend fun backupKeys(): List<BackupApiKey> =
    list().mapNotNull { key ->
      secrets.get(secretName(key.id))?.takeIf { it.isNotBlank() }?.let { BackupApiKey(key, it) }
    }

  /**
   * Puts back keys from a locked backup: into the protected store and `ai_keys`, as they were
   * (provider, label, address, limits, dates). A key already here (the same secret) is skipped.
   * Returns how many were added; none where keys can't be kept safely.
   */
  suspend fun restoreKeys(backup: List<BackupApiKey>): Int {
    if (!secrets.available) return 0
    val here = keys.list()
    val known = here.mapNotNull { secrets.get(secretName(it.id))?.trim() }.toMutableSet()
    val ids = here.map { it.id }.toMutableSet()
    var added = 0
    for ((key, secret) in backup) {
      val value = secret.trim()
      if (value.isEmpty() || !known.add(value)) continue
      val id = key.id.takeIf { it.isNotBlank() && ids.add(it) } ?: UUID.randomUUID().toString()
      ids += id
      secrets.put(secretName(id), value)
      keys.upsert(
        AiKeyEntity(
          id = id,
          provider = key.provider.serialName(),
          label = key.label?.trim()?.takeIf { it.isNotEmpty() },
          baseUrl = key.baseUrl?.trim()?.takeIf { it.isNotEmpty() },
          requestsPerMinute = key.requestsPerMinute,
          requestsPerDay = key.requestsPerDay,
          tokensPerDay = key.tokensPerDay,
          createdAt = key.createdAt.takeIf { it > 0 } ?: clock(),
          expiresAt = key.expiresAt,
        )
      )
      added++
    }
    return added
  }

  /** The models [id]'s provider offers, as listed by its `/models`; empty when it can't say. */
  suspend fun models(id: String): List<String> {
    val key = keys.get(id)?.toModel() ?: return emptyList()
    return models(key.provider, key.baseUrl, secrets.get(secretName(id))).orEmpty()
  }

  suspend fun routing(): AiRouting =
    settings.get()?.aiRouting?.let {
      runCatching { ApiJson.decodeFromString(AiRouting.serializer(), it) }.getOrNull()
    } ?: AiRouting()

  suspend fun setRouting(routing: AiRouting) {
    val row = settings.get() ?: SettingsDefaults.newRow()
    settings.upsert(row.copy(aiRouting = ApiJson.encodeToString(AiRouting.serializer(), routing)))
  }

  suspend fun usageToday(): List<AiKeyUsage> =
    keys.usage(today()).map { AiKeyUsage(it.keyId, it.requests, it.tokens) }

  /**
   * [task]'s models in order; [main] is the main AI choice (usually this computer). The OpenCode
   * CLI comes as [OpenCodeClient.target] and the Antigravity CLI as [AgyClient.target] while they
   * are turned on.
   */
  suspend fun targets(task: AiTask, main: LlmConfig): List<LlmTarget> {
    val now = clock()
    val all = list()
    val routing = routing()
    return routing.forTask(task, all).mapNotNull { route ->
      if (route.local) return@mapNotNull LlmTarget(main, null)
      // No key: LlmClient hands it to the OpenCode CLI, which keeps its own quota.
      if (route.openCode) {
        return@mapNotNull OpenCodeClient.target(route.model.ifBlank { routing.openCode.model })
      }
      // No key either: LlmClient hands it to agy, which rests on its own limits.
      if (route.agy) return@mapNotNull AgyClient.target(route.model.ifBlank { routing.agy.model })
      val key = all.firstOrNull { it.id == route.keyId } ?: return@mapNotNull null
      // The site no longer takes it.
      if (key.expired(now)) return@mapNotNull null
      val secret = secrets.get(secretName(key.id)) ?: return@mapNotNull null
      val model =
        route.model.ifBlank { FreeAiCatalog.of(key.provider)?.model ?: key.provider.suggestedModel }
      runCatching {
        LlmTarget(
          LlmConfig(
            provider = key.provider,
            baseUrl = key.endpoint,
            model = model,
            apiKey = secret,
            requestTimeoutMs = ONLINE_TIMEOUT_MS,
          ),
          key.id,
        )
      }
        .getOrNull()
    }
  }

  /**
   * Takes one request from [keyId]'s quota; `false` when it is resting or this minute's or day's
   * quota is used up, so the call goes to the next model.
   */
  suspend fun take(keyId: String): Boolean = lock.withLock {
    val now = clock()
    if ((restUntil[keyId] ?: 0) > now) return false
    val key = keys.get(keyId) ?: return false
    key.requestsPerMinute?.let { limit ->
      val window = minute.getOrPut(keyId) { ArrayDeque() }
      while (window.isNotEmpty() && now - window.first() >= MINUTE_MS) window.removeFirst()
      if (window.size >= limit) return false
      window.addLast(now)
    }
    val used = keys.usage(keyId, today())
    if (key.requestsPerDay != null && (used?.requests ?: 0) >= key.requestsPerDay) return false
    if (key.tokensPerDay != null && (used?.tokens ?: 0) >= key.tokensPerDay) return false
    true
  }

  /** Counts a finished request and its tokens against [keyId]'s day. */
  suspend fun record(keyId: String, tokens: Long) {
    keys.addUsage(keyId, today(), 1, tokens)
  }

  /** The provider said "too many requests": [keyId] rests a while. */
  fun rest(keyId: String, ms: Long = COOLDOWN_MS) {
    restUntil[keyId] = clock() + ms
  }

  private suspend fun models(provider: AiProvider, baseUrl: String?, key: String?): List<String>? =
    try {
      val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: provider.defaultBaseUrl
      val response =
        http.get("${base.trimEnd('/')}/models") {
          if (key != null) {
            if (provider == AiProvider.ANTHROPIC) {
              header("x-api-key", key)
              header("anthropic-version", "2023-06-01")
            } else header(HttpHeaders.Authorization, "Bearer $key")
          }
          timeout { requestTimeoutMillis = MODELS_TIMEOUT_MS }
        }
      if (response.status.value != 200) null
      else
        (json.parseToJsonElement(response.bodyAsText()) as JsonObject)["data"]
          ?.jsonArray
          ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
          ?.sorted()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }

  /**
   * The expiry the site reports for [key], where it has a call for that (OpenRouter's `/key`);
   * `null` otherwise or on any failure.
   */
  private suspend fun reportedExpiry(provider: AiProvider, baseUrl: String?, key: String): Long? {
    if (provider != AiProvider.OPENROUTER) return null
    return try {
      val base = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: provider.defaultBaseUrl
      val response =
        http.get("${base.trimEnd('/')}/key") {
          header(HttpHeaders.Authorization, "Bearer $key")
          timeout { requestTimeoutMillis = MODELS_TIMEOUT_MS }
        }
      if (response.status.value != 200) return null
      val data = (json.parseToJsonElement(response.bodyAsText()) as JsonObject)["data"]?.jsonObject
      data?.get("expires_at")?.jsonPrimitive?.contentOrNull?.let {
        java.time.Instant.parse(it).toEpochMilli()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }

  private fun today(): String =
    LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock()), zone).toString()

  private fun AiKeyEntity.toModel() =
    AiKey(
      id = id,
      provider =
        AiProvider.entries.firstOrNull { it.serialName() == provider } ?: AiProvider.CUSTOM,
      label = label,
      baseUrl = baseUrl,
      requestsPerMinute = requestsPerMinute,
      requestsPerDay = requestsPerDay,
      tokensPerDay = tokensPerDay,
      createdAt = createdAt,
      expiresAt = expiresAt,
    )

  companion object {
    private const val MINUTE_MS = 60_000L
    private const val COOLDOWN_MS = 60_000L
    private const val MODELS_TIMEOUT_MS = 15_000L
    private const val ONLINE_TIMEOUT_MS = 3 * 60_000L

    fun secretName(id: String) = "ai-key-id-$id"

    private fun AiProvider.serialName(): String =
      ApiJson.encodeToString(AiProvider.serializer(), this).trim('"')
  }
}
