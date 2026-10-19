package io.github.raghavsatyadev.jobfinder.server.service.resume

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import java.io.ByteArrayInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable

/** The resume parts of a LinkedIn data export, as read from its CSV files. */
@Serializable
data class LinkedInProfile(
  val firstName: String = "",
  val lastName: String = "",
  val headline: String = "",
  val summary: String = "",
  val location: String = "",
  val email: String = "",
  val phone: String = "",
  val websites: List<String> = emptyList(),
  val positions: List<Position> = emptyList(),
  val education: List<Education> = emptyList(),
  val skills: List<String> = emptyList(),
  val certifications: List<Certification> = emptyList(),
  val projects: List<Project> = emptyList(),
  val languages: List<Language> = emptyList(),
  val honors: List<Honor> = emptyList(),
  val courses: List<String> = emptyList(),
) {
  val name: String
    get() = "$firstName $lastName".trim()

  @Serializable
  data class Position(
    val company: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startedOn: String = "",
    val finishedOn: String = "",
  )

  @Serializable
  data class Education(
    val school: String,
    val degree: String = "",
    val notes: String = "",
    val activities: String = "",
    val startDate: String = "",
    val endDate: String = "",
  )

  @Serializable
  data class Certification(
    val name: String,
    val authority: String = "",
    val startedOn: String = "",
    val url: String = "",
  )

  @Serializable
  data class Project(
    val title: String,
    val description: String = "",
    val url: String = "",
    val startedOn: String = "",
    val finishedOn: String = "",
  )

  @Serializable data class Language(val name: String, val proficiency: String = "")

  @Serializable
  data class Honor(val title: String, val description: String = "", val issuedOn: String = "")
}

/**
 * Reads a LinkedIn data export ZIP ("Download your data"). Only the files a resume needs are read:
 * profile, positions, education, skills, certifications, projects, languages, honors, courses,
 * email and phone. Messages, connections and everything else are skipped unread.
 */
object LinkedInImport {
  /** Largest CSV read; the resume files are a few KB each. */
  private const val MAX_FILE_BYTES = 2 * 1024 * 1024

  private val WANTED =
    setOf(
      "profile.csv",
      "positions.csv",
      "education.csv",
      "skills.csv",
      "certifications.csv",
      "projects.csv",
      "languages.csv",
      "honors.csv",
      "courses.csv",
      "email addresses.csv",
      "phonenumbers.csv",
      "phone numbers.csv",
    )

  fun read(bytes: ByteArray): LinkedInProfile {
    val files = mutableMapOf<String, String>()
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          val name = entry.name.substringAfterLast('/').lowercase()
          if (entry.isDirectory || name !in WANTED) continue
          val data = zip.readNBytes(MAX_FILE_BYTES + 1)
          if (data.size <= MAX_FILE_BYTES) files[name] = data.decodeToString()
        }
      }
    } catch (_: ZipException) {
      bad("This is not a ZIP file. Pick the ZIP that LinkedIn sent.")
    }
    if (files.isEmpty()) {
      bad("This ZIP has none of LinkedIn's profile files. Pick the ZIP from Download your data.")
    }
    return profile(files)
  }

  private fun profile(files: Map<String, String>): LinkedInProfile {
    fun records(file: String, vararg anyOf: String) =
      files[file]?.let { Csv.records(it, anyOf.toSet()) } ?: emptyList()
    val profile = records("profile.csv", "First Name", "Headline").firstOrNull() ?: emptyMap()
    val emails = records("email addresses.csv", "Email Address")
    val phones = records("phonenumbers.csv", "Number") + records("phone numbers.csv", "Number")
    return LinkedInProfile(
      firstName = profile.of("First Name"),
      lastName = profile.of("Last Name"),
      headline = profile.of("Headline"),
      summary = profile.of("Summary"),
      location = profile.of("Geo Location").ifEmpty { profile.of("Address") },
      email =
        (emails.firstOrNull { it.of("Primary").equals("yes", true) } ?: emails.firstOrNull())
          ?.of("Email Address")
          .orEmpty(),
      phone = phones.firstOrNull()?.of("Number").orEmpty(),
      websites = websites(profile.of("Websites")),
      positions =
        records("positions.csv", "Company Name", "Title").mapNotNull {
          LinkedInProfile.Position(
              company = it.of("Company Name"),
              title = it.of("Title"),
              description = it.of("Description"),
              location = it.of("Location"),
              startedOn = it.of("Started On"),
              finishedOn = it.of("Finished On"),
            )
            .takeIf { p -> p.company.isNotEmpty() || p.title.isNotEmpty() }
        },
      education =
        records("education.csv", "School Name").mapNotNull {
          LinkedInProfile.Education(
              school = it.of("School Name"),
              degree = it.of("Degree Name"),
              notes = it.of("Notes"),
              activities = it.of("Activities"),
              startDate = it.of("Start Date"),
              endDate = it.of("End Date"),
            )
            .takeIf { e -> e.school.isNotEmpty() }
        },
      skills = records("skills.csv", "Name").map { it.of("Name") }.filter { it.isNotEmpty() },
      certifications =
        records("certifications.csv", "Name", "Authority").mapNotNull {
          LinkedInProfile.Certification(
              name = it.of("Name"),
              authority = it.of("Authority"),
              startedOn = it.of("Started On"),
              url = it.of("Url"),
            )
            .takeIf { c -> c.name.isNotEmpty() }
        },
      projects =
        records("projects.csv", "Title").mapNotNull {
          LinkedInProfile.Project(
              title = it.of("Title"),
              description = it.of("Description"),
              url = it.of("Url"),
              startedOn = it.of("Started On"),
              finishedOn = it.of("Finished On"),
            )
            .takeIf { p -> p.title.isNotEmpty() }
        },
      languages =
        records("languages.csv", "Name", "Proficiency").mapNotNull {
          LinkedInProfile.Language(it.of("Name"), it.of("Proficiency")).takeIf { l ->
            l.name.isNotEmpty()
          }
        },
      honors =
        records("honors.csv", "Title").mapNotNull {
          LinkedInProfile.Honor(it.of("Title"), it.of("Description"), it.of("Issued On")).takeIf { h
            ->
            h.title.isNotEmpty()
          }
        },
      courses = records("courses.csv", "Name").map { it.of("Name") }.filter { it.isNotEmpty() },
    )
  }

  private fun Map<String, String>.of(column: String): String = this[column].orEmpty().trim()

  /** LinkedIn writes websites as `[PORTFOLIO:https://…,OTHER:https://…]`. */
  private fun websites(raw: String): List<String> =
    Regex("https?://[^,\\]\\s]+").findAll(raw).map { it.value }.toList()

  private fun bad(message: String): Nothing = throw ApiException(ApiErrorCode.BAD_REQUEST, message)
}
