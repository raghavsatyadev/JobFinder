package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.BackupResumeProfile
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs

/**
 * What the resume PDFs are made from besides the uploaded resume: the LinkedIn data export as read
 * (JSON of `LinkedInProfile`; the archive is not kept) and the PDF preferences (JSON of
 * `ResumePdfPrefs`). One row, [SINGLETON_ID].
 */
@Entity(tableName = "resume_profile")
data class ResumeProfileEntity(
  @PrimaryKey val id: Long = SINGLETON_ID,
  val linkedin: String? = null,
  @ColumnInfo(name = "linkedin_imported_at") val linkedinImportedAt: Long? = null,
  val prefs: String? = null,
)

@Dao
interface ResumeProfileDao {
  @Query("SELECT * FROM resume_profile WHERE id = $SINGLETON_ID")
  suspend fun get(): ResumeProfileEntity?

  @Upsert suspend fun upsert(profile: ResumeProfileEntity)

  @Query("DELETE FROM resume_profile") suspend fun deleteAll()
}

fun ResumeProfileEntity.toBackup(): BackupResumeProfile =
  BackupResumeProfile(
    linkedIn = linkedin,
    linkedInImportedAt = linkedinImportedAt,
    pdfPrefs =
      prefs?.let { runCatching { ApiJson.decodeFromString<ResumePdfPrefs>(it) }.getOrNull() },
  )

fun BackupResumeProfile.toEntity(): ResumeProfileEntity =
  ResumeProfileEntity(
    linkedin = linkedIn,
    linkedinImportedAt = linkedInImportedAt.takeIf { linkedIn != null },
    prefs = pdfPrefs?.let { ApiJson.encodeToString(it) },
  )
