package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

/**
 * An answer typed into a job application form, as the browser extension recorded it on submit. A
 * deleted job leaves its answers, without the link: they still fill the next form.
 */
@Entity(
  tableName = "form_answers",
  foreignKeys =
    [
      ForeignKey(
        entity = JobEntity::class,
        parentColumns = ["id"],
        childColumns = ["job_id"],
        onDelete = ForeignKey.SET_NULL,
        onUpdate = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("job_id"), Index("question_key")],
)
data class FormAnswerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "job_id") val jobId: Long? = null,
  /** Host without `www.`. */
  val site: String,
  val url: String,
  val label: String? = null,
  val name: String? = null,
  @ColumnInfo(name = "element_id") val elementId: String? = null,
  val placeholder: String? = null,
  @ColumnInfo(name = "aria_label") val ariaLabel: String? = null,
  @ColumnInfo(name = "input_type") val inputType: String,
  /** `QuestionKeys.keyFor`: a known key such as `first_name`, or the cleaned question. */
  @ColumnInfo(name = "question_key") val questionKey: String,
  val value: String,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** An answer with its job's title and company, for the Settings list. */
data class FormAnswerRow(
  val id: Long,
  @ColumnInfo(name = "job_id") val jobId: Long?,
  val site: String,
  val url: String,
  val label: String?,
  val name: String?,
  @ColumnInfo(name = "element_id") val elementId: String?,
  val placeholder: String?,
  @ColumnInfo(name = "aria_label") val ariaLabel: String?,
  @ColumnInfo(name = "input_type") val inputType: String,
  @ColumnInfo(name = "question_key") val questionKey: String,
  val value: String,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
  @ColumnInfo(name = "job_title") val jobTitle: String?,
  @ColumnInfo(name = "company_name") val companyName: String?,
)

@Dao
interface FormAnswersDao {
  @Query(
    "SELECT a.*, j.title AS job_title, j.company_name AS company_name FROM form_answers a " +
      "LEFT JOIN jobs j ON j.id = a.job_id ORDER BY a.updated_at DESC, a.id DESC"
  )
  suspend fun listWithJobs(): List<FormAnswerRow>

  @Query("SELECT * FROM form_answers ORDER BY updated_at DESC, id DESC")
  suspend fun listAll(): List<FormAnswerEntity>

  @Query("SELECT * FROM form_answers WHERE id = :id") suspend fun get(id: Long): FormAnswerEntity?

  /** The answer this field already has on this job (or this page without a job). */
  @Query(
    "SELECT * FROM form_answers WHERE question_key = :key AND site = :site AND " +
      "((:jobId IS NULL AND job_id IS NULL AND url = :url) OR job_id = :jobId) LIMIT 1"
  )
  suspend fun findSame(key: String, site: String, jobId: Long?, url: String): FormAnswerEntity?

  @Insert suspend fun insert(answer: FormAnswerEntity): Long

  @Insert suspend fun insertAll(answers: List<FormAnswerEntity>)

  @Query("UPDATE form_answers SET value = :value, updated_at = :now WHERE id = :id")
  suspend fun updateValue(id: Long, value: String, now: Long)

  @Query("DELETE FROM form_answers WHERE id = :id") suspend fun delete(id: Long)

  @Query("DELETE FROM form_answers") suspend fun deleteAll()
}
