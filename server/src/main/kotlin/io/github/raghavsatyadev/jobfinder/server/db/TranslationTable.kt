package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert

/**
 * A job's title and description in the user's language [toLang], or why there is none: [state] is
 * `ready`, `same` (already in that language, or not known) or `failed`. [sourceHash] is the hash of
 * the title and description it was made from; a changed job is translated again.
 */
@Entity(tableName = "job_translations")
data class JobTranslationEntity(
  @PrimaryKey @ColumnInfo(name = "job_id") val jobId: Long,
  /** The language guessed for the job; `null` when not known. */
  @ColumnInfo(name = "from_lang") val fromLang: String?,
  @ColumnInfo(name = "to_lang") val toLang: String,
  @ColumnInfo(name = "source_hash") val sourceHash: String,
  val state: String,
  val title: String? = null,
  val description: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
) {
  companion object {
    const val READY = "ready"
    const val SAME = "same"
    const val FAILED = "failed"
  }
}

@Dao
interface TranslationsDao {
  @Query("SELECT * FROM job_translations WHERE job_id = :jobId")
  suspend fun get(jobId: Long): JobTranslationEntity?

  @Upsert suspend fun upsert(translation: JobTranslationEntity)

  @Query("DELETE FROM job_translations WHERE job_id = :jobId") suspend fun delete(jobId: Long)

  @Query("DELETE FROM job_translations") suspend fun deleteAll()

  /** The newest job in [status] with a description and nothing stored for [toLang]. */
  @Query(
    """SELECT j.id FROM jobs j
       WHERE j.status = :status AND j.description IS NOT NULL AND trim(j.description) != ''
       AND NOT EXISTS (SELECT 1 FROM job_translations t
                       WHERE t.job_id = j.id AND t.to_lang = :toLang)
       ORDER BY j.created_at DESC, j.id DESC LIMIT 1"""
  )
  suspend fun nextUnchecked(status: String, toLang: String): Long?
}
