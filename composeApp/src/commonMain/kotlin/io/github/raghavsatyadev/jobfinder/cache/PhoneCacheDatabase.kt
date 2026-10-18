package io.github.raghavsatyadev.jobfinder.cache

import androidx.room3.AutoMigration
import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * A job the phone has seen, per computer ([computer] is the saved computer's key). [job] is the job
 * as JSON without its description, which is kept apart in [description] so a list refresh does not
 * drop a description read before.
 */
@Entity(
  tableName = "jobs",
  primaryKeys = ["computer", "id"],
  indices = [Index("computer", "status", "updated_at")],
)
data class CachedJob(
  val computer: String,
  val id: Long,
  val status: String,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
  val job: String,
  val description: String? = null,
)

/** Anything else the screens read, as JSON under a key: sites, links, notes of a job, … */
@Entity(tableName = "answers", primaryKeys = ["computer", "key"])
data class CachedAnswer(
  val computer: String,
  val key: String,
  val json: String,
  @ColumnInfo(name = "saved_at") val savedAt: Long,
)

/** A change made while the computer was out of reach, sent in order once it is back. */
@Entity(tableName = "pending_changes", indices = [Index("computer")])
data class PendingChange(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val computer: String,
  @ColumnInfo(name = "job_id") val jobId: Long,
  val kind: String,
  /** The new status's wire name, or the labels as JSON. */
  val value: String,
  @ColumnInfo(name = "created_at") val createdAt: Long,
) {
  companion object {
    const val STATUS = "status"
    const val LABELS = "labels"
  }
}

/**
 * A full backup imported on the phone, waiting to be sent to [computer]; [file] is its name in
 * [ImportFiles]. Its jobs show in the lists meanwhile, kept with negative ids.
 */
@Entity(tableName = "pending_imports", indices = [Index("computer")])
data class PendingImport(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val computer: String,
  val file: String,
  val jobs: Int,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface PhoneCacheDao {
  @Query(
    "SELECT * FROM jobs WHERE computer = :computer AND status = :status " +
      "ORDER BY updated_at DESC, id DESC"
  )
  suspend fun jobs(computer: String, status: String): List<CachedJob>

  @Query("SELECT * FROM jobs WHERE computer = :computer AND id = :id")
  suspend fun job(computer: String, id: Long): CachedJob?

  @Query("SELECT status, COUNT(*) AS count FROM jobs WHERE computer = :computer GROUP BY status")
  suspend fun statusCounts(computer: String): List<StatusCount>

  @Query("SELECT COUNT(*) FROM jobs WHERE computer = :computer")
  suspend fun jobCount(computer: String): Int

  @Upsert suspend fun upsertJobs(jobs: List<CachedJob>)

  @Query(
    "SELECT id FROM jobs WHERE computer = :computer AND status IN (:statuses) " +
      "AND description IS NULL ORDER BY updated_at DESC LIMIT :limit"
  )
  suspend fun withoutDescription(computer: String, statuses: List<String>, limit: Int): List<Long>

  /** Imported jobs (negative ids) stay until their backup reached the computer. */
  @Query(
    "DELETE FROM jobs WHERE computer = :computer AND status = :status AND id > 0 " +
      "AND id NOT IN (:keep)"
  )
  suspend fun deleteOthers(computer: String, status: String, keep: List<Long>)

  @Query("DELETE FROM jobs WHERE computer = :computer AND status = :status AND id > 0")
  suspend fun deleteStatus(computer: String, status: String)

  @Query("SELECT * FROM jobs WHERE computer = :computer AND id < 0")
  suspend fun importedJobs(computer: String): List<CachedJob>

  @Query("SELECT job FROM jobs WHERE computer = :computer")
  suspend fun allJobs(computer: String): List<String>

  @Query("SELECT * FROM jobs WHERE computer = :computer AND id > 0")
  suspend fun computerJobs(computer: String): List<CachedJob>

  @Query("DELETE FROM jobs WHERE computer = :computer AND id < 0")
  suspend fun deleteImportedJobs(computer: String)

  @Query("SELECT * FROM pending_imports WHERE computer = :computer ORDER BY id")
  suspend fun imports(computer: String): List<PendingImport>

  @Insert suspend fun addImport(pending: PendingImport): Long

  @Query("DELETE FROM pending_imports WHERE id = :id") suspend fun deleteImport(id: Long)

  @Query("DELETE FROM pending_imports WHERE computer = :computer")
  suspend fun forgetImports(computer: String)

  @Query("SELECT * FROM answers WHERE computer = :computer AND `key` = :key")
  suspend fun answer(computer: String, key: String): CachedAnswer?

  @Upsert suspend fun upsertAnswer(answer: CachedAnswer)

  @Query(
    "SELECT * FROM answers WHERE computer = :computer AND `key` LIKE :prefix || '%' ORDER BY `key`"
  )
  suspend fun answersStartingWith(computer: String, prefix: String): List<CachedAnswer>

  /** Drops the answer under [key] only while it still holds [json]. */
  @Query("DELETE FROM answers WHERE computer = :computer AND `key` = :key AND json = :json")
  suspend fun deleteAnswerIf(computer: String, key: String, json: String)

  @Query("DELETE FROM answers WHERE computer = :computer AND `key` = :key")
  suspend fun deleteAnswer(computer: String, key: String)

  /** Answers whose key starts with [prefix], but not those of imported jobs (`<prefix>-1`). */
  @Query(
    "DELETE FROM answers WHERE computer = :computer AND `key` LIKE :prefix || '%' " +
      "AND `key` NOT LIKE :prefix || '-%'"
  )
  suspend fun deleteAnswers(computer: String, prefix: String)

  @Query("SELECT * FROM pending_changes WHERE computer = :computer ORDER BY id")
  suspend fun pending(computer: String): List<PendingChange>

  @Insert suspend fun addPending(change: PendingChange): Long

  @Query("DELETE FROM pending_changes WHERE id = :id") suspend fun deletePending(id: Long)

  @Query("DELETE FROM jobs WHERE computer = :computer") suspend fun forgetJobs(computer: String)

  @Query("DELETE FROM answers WHERE computer = :computer")
  suspend fun forgetAnswers(computer: String)

  @Query("DELETE FROM pending_changes WHERE computer = :computer")
  suspend fun forgetPending(computer: String)

  /** Everything kept for [computer], when the user forgets it. */
  @Transaction
  suspend fun forget(computer: String) {
    forgetJobs(computer)
    forgetAnswers(computer)
    forgetPending(computer)
    forgetImports(computer)
  }
}

data class StatusCount(val status: String, val count: Int)

/** The phone's copy of what it read from each computer, so jobs stay on screen off the Wi-Fi. */
@Database(
  entities = [CachedJob::class, CachedAnswer::class, PendingChange::class, PendingImport::class],
  version = 2,
  autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(PhoneCacheDatabaseConstructor::class)
abstract class PhoneCacheDatabase : RoomDatabase() {
  abstract fun dao(): PhoneCacheDao

  companion object {
    /** The file name each platform keeps the database under, in the app's own storage. */
    const val NAME = "phone-cache.db"

    /** Builds the database from the platform's [builder] (it knows where the file goes). */
    fun create(builder: RoomDatabase.Builder<PhoneCacheDatabase>): PhoneCacheDatabase =
      builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // A cache: a newer layout starts empty and fills from the computer again.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
  }
}

/** Room's generated constructor for platforms without reflection. */
@Suppress("KotlinNoActualForExpect")
expect object PhoneCacheDatabaseConstructor : RoomDatabaseConstructor<PhoneCacheDatabase> {
  override fun initialize(): PhoneCacheDatabase
}
