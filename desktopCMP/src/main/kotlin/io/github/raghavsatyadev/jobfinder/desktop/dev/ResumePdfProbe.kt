package io.github.raghavsatyadev.jobfinder.desktop.dev

import io.github.raghavsatyadev.jobfinder.server.service.ats.KeywordMatcher
import io.github.raghavsatyadev.jobfinder.server.service.ats.ResumeReader
import io.github.raghavsatyadev.jobfinder.server.service.resume.LinkedInImport
import io.github.raghavsatyadev.jobfinder.server.service.resume.ResumeDocs
import io.github.raghavsatyadev.jobfinder.server.service.resume.ResumePdfService
import io.github.raghavsatyadev.jobfinder.server.service.resume.ResumePdfWriter
import io.github.raghavsatyadev.jobfinder.server.service.resume.ResumeTailor
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.PdfPageSize
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Makes resume PDFs from a made-up resume and LinkedIn export, to look at and score: `general.pdf`
 * from the resume, `linkedin.pdf` from the export alone and `job.pdf` fitted to a sample job. With
 * a second argument, reads that real LinkedIn export ZIP instead of the made-up one. Nothing is
 * sent anywhere.
 */
fun main(args: Array<String>) {
  val out = File(args.getOrNull(0) ?: "tmp/resume-probe").apply { mkdirs() }
  val zip = args.getOrNull(1)?.let { File(it).readBytes() } ?: sampleExport()
  val linkedIn = LinkedInImport.read(zip)
  println(
    "LinkedIn: ${linkedIn.name}, ${linkedIn.positions.size} positions, " +
      "${linkedIn.skills.size} skills, ${linkedIn.projects.size} projects"
  )
  val resume = ResumeDocs.fromText(RESUME) ?: error("No sections found")
  println("Sections: " + resume.sections.joinToString { "${it.kind}(${it.entries.size})" })
  val prefs = ResumePdfPrefs(pageSize = PdfPageSize.LETTER, maxPages = 1)
  write(out, "general", ResumeTailor.arrange(resume, prefs), prefs)
  write(out, "linkedin", ResumeDocs.fromLinkedIn(linkedIn), prefs.copy(maxPages = 2))
  val tailored = ResumeTailor.tailor(resume, KEYWORDS, linkedIn)
  tailored.fromLinkedIn.forEach { println("  from LinkedIn: $it") }
  val bytes = write(out, "job", ResumeTailor.arrange(tailored.doc, prefs), prefs)
  val read = ResumeReader.read("job.pdf", bytes)
  val before = KeywordMatcher.matchPercent(KEYWORDS)
  val after = KeywordMatcher.matchPercent(ResumePdfService.rematch(KEYWORDS, read.text))
  println("Match $before% -> $after%, flags ${read.flags}")
}

private fun write(
  out: File,
  name: String,
  doc: io.github.raghavsatyadev.jobfinder.server.service.resume.ResumeDoc,
  prefs: ResumePdfPrefs,
): ByteArray {
  val pdf = ResumePdfWriter(prefs.pageSize).write(doc)
  File(out, "$name.pdf").writeBytes(pdf.bytes)
  val read = ResumeReader.read("$name.pdf", pdf.bytes)
  println("$name.pdf: ${pdf.pages} page(s), ${read.words} words, flags ${read.flags}")
  return pdf.bytes
}

private fun keyword(term: String, match: KeywordMatch) =
  AtsKeyword(term, KeywordCategory.HARD_SKILL, match)

private val KEYWORDS =
  listOf(
    keyword("Kotlin", KeywordMatch.EXACT),
    keyword("Jetpack Compose", KeywordMatch.EXACT),
    keyword("Kotlin Multiplatform", KeywordMatch.MISSING),
    keyword("GraphQL", KeywordMatch.MISSING),
    keyword("CI/CD", KeywordMatch.MISSING),
    keyword("Stakeholders", KeywordMatch.MISSING),
    keyword("Firebase", KeywordMatch.EXACT),
  )

private const val RESUME =
  """Sam Sample
sam.sample@example.com | +91 98765 43210 | Bengaluru, India | linkedin.com/in/sam-sample

SUMMARY
Senior Android engineer with 7 years building Kotlin apps used by millions, from offline-first
field tools to payments. Expected pay ₹45L – ₹55L.

EXPERIENCE
Senior Android Engineer   Mar 2022 – Present
Northwind Traders, Bengaluru
• Led the move of a 40-screen app from Views to Jetpack Compose, cutting UI bugs by 35%.
• Built offline sync with Room and WorkManager for 120,000 field agents.
• Worked with product and design on the payments flow, raising completion by 12%.
• Mentored four engineers and ran the weekly Android guild.
Android Engineer   Jun 2018 – Feb 2022
Contoso Ltd, Pune
• Shipped a Kotlin rewrite of the consumer app with 4.6 rating and 5M installs.
• Added Firebase Crashlytics and Remote Config; crash-free sessions rose to 99.7%.
• Wrote the app's modular Gradle build, halving clean build time.
• Write-up: https://example.com/engineering/blog/2021/modular-gradle-builds-for-large-android-apps-with-convention-plugins-and-version-catalogs

SKILLS
Languages: Kotlin, Java, SQL
Android: Jetpack Compose, Room, WorkManager, Hilt, Coroutines, Flow
Tools: Firebase, Gradle, Git, Android Studio

EDUCATION
B.Tech, Computer Science   2014 – 2018
Example Institute of Technology
"""

/** A made-up LinkedIn export with the files and columns LinkedIn uses. */
private fun sampleExport(): ByteArray {
  val files =
    mapOf(
      "Profile.csv" to
        "First Name,Last Name,Maiden Name,Address,Birth Date,Headline,Summary,Industry," +
          "Zip Code,Geo Location,Twitter Handles,Websites,Instant Messengers\n" +
          "Sam,Sample,,,,Senior Android Engineer,\"Android engineer who likes offline-first " +
          "apps.\nKotlin Multiplatform since 2023.\",Software,,\"Bengaluru, Karnataka, India\"," +
          ",[PORTFOLIO:https://sam.example.com],\n",
      "Positions.csv" to
        "Company Name,Title,Description,Location,Started On,Finished On\n" +
          "Northwind Traders,Senior Android Engineer,\"Shared the sync layer with iOS using " +
          "Kotlin Multiplatform.\nSet up CI/CD with GitHub Actions and Firebase App " +
          "Distribution.\nPresented the roadmap to business stakeholders every quarter.\"," +
          "\"Bengaluru, India\",Mar 2022,\n" +
          "Contoso Ltd,Android Engineer,\"Moved the API layer to GraphQL with Apollo.\"," +
          "\"Pune, India\",Jun 2018,Feb 2022\n",
      "Skills.csv" to "Name\nKotlin\nGraphQL\nKotlin Multiplatform\nJetpack Compose\nCI/CD\n",
      "Projects.csv" to
        "Title,Description,Url,Started On,Finished On\n" +
          "KMP Weather,\"A Kotlin Multiplatform weather app for Android and iOS.\"," +
          "https://github.com/sam/kmp-weather,Jan 2024,Mar 2024\n",
      "Education.csv" to
        "School Name,Start Date,End Date,Notes,Degree Name,Activities\n" +
          "Example Institute of Technology,2014,2018,,B.Tech Computer Science,Coding club\n",
      "Email Addresses.csv" to
        "Email Address,Confirmed,Primary,Updated On\nsam.sample@example.com,Yes,Yes,1/1/24\n",
      "PhoneNumbers.csv" to "Extension,Number,Type\n,+91 98765 43210,Mobile\n",
      "messages.csv" to "FROM,TO,CONTENT\nsomeone,Sam,never read this\n",
    )
  val bytes = ByteArrayOutputStream()
  ZipOutputStream(bytes).use { zip ->
    files.forEach { (name, text) ->
      zip.putNextEntry(ZipEntry(name))
      zip.write(text.toByteArray())
      zip.closeEntry()
    }
  }
  return bytes.toByteArray()
}
