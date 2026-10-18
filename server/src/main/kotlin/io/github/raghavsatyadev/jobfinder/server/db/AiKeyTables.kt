package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

/** An online API key the user added; the key itself is in the protected store under [id]. */
@Entity(tableName = "ai_keys")
data class AiKeyEntity(
  @PrimaryKey val id: String,
  /** `AiProvider`'s serial name. */
  val provider: String,
  val label: String? = null,
  @ColumnInfo(name = "base_url") val baseUrl: String? = null,
  @ColumnInfo(name = "requests_per_minute") val requestsPerMinute: Int? = null,
  @ColumnInfo(name = "requests_per_day") val requestsPerDay: Int? = null,
  @ColumnInfo(name = "tokens_per_day") val tokensPerDay: Long? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  /** When the site stops taking the key; `null` when unknown or never. */
  @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
  /** The model the key's work uses; `null` takes the free tier's. */
  val model: String? = null,
)

/** One key's use on one local day. */
@Entity(tableName = "ai_key_usage", primaryKeys = ["key_id", "usage_date"])
data class AiKeyUsageEntity(
  @ColumnInfo(name = "key_id") val keyId: String,
  /** ISO date, `yyyy-MM-dd`, in the computer's time zone. */
  @ColumnInfo(name = "usage_date") val usageDate: String,
  val requests: Int = 0,
  val tokens: Long = 0,
)

@Dao
interface AiKeysDao {
  @Query("SELECT * FROM ai_keys ORDER BY created_at") suspend fun list(): List<AiKeyEntity>

  @Query("SELECT * FROM ai_keys WHERE id = :id") suspend fun get(id: String): AiKeyEntity?

  @Upsert suspend fun upsert(key: AiKeyEntity)

  @Query("DELETE FROM ai_keys WHERE id = :id") suspend fun delete(id: String)

  @Query("DELETE FROM ai_key_usage WHERE key_id = :id") suspend fun deleteUsage(id: String)

  @Query("SELECT * FROM ai_key_usage WHERE usage_date = :date")
  suspend fun usage(date: String): List<AiKeyUsageEntity>

  @Query("SELECT * FROM ai_key_usage WHERE key_id = :id AND usage_date = :date")
  suspend fun usage(id: String, date: String): AiKeyUsageEntity?

  @Upsert suspend fun upsertUsage(usage: AiKeyUsageEntity)

  /** Adds one request and its tokens to the day's row. */
  @Transaction
  suspend fun addUsage(id: String, date: String, requests: Int, tokens: Long) {
    val current = usage(id, date) ?: AiKeyUsageEntity(id, date)
    upsertUsage(
      current.copy(requests = current.requests + requests, tokens = current.tokens + tokens)
    )
  }
}
