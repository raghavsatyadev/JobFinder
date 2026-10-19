package io.github.raghavsatyadev.jobfinder.server.service.resume

import io.github.raghavsatyadev.jobfinder.server.service.ats.KeywordMatcher
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs

/** A tailored resume and what it brought in from LinkedIn, for the user to see. */
class Tailored(val doc: ResumeDoc, val fromLinkedIn: List<String>)

/**
 * Fits a resume to one job without writing anything new: it brings in the user's own LinkedIn
 * skills, bullets, projects and certifications that have the job's missing words, and puts the
 * skills and bullets with the job's words first. No AI: every line is the user's own.
 */
object ResumeTailor {
  /** Most LinkedIn bullets added for one missing word. */
  private const val BULLETS_PER_WORD = 2

  fun tailor(doc: ResumeDoc, keywords: List<AtsKeyword>, linkedIn: LinkedInProfile?): Tailored {
    val terms = keywords.map { KeywordMatcher.normalize(it.term) }.filter { it.isNotEmpty() }
    val added = mutableListOf<String>()
    var out = doc
    if (linkedIn != null) {
      val missing =
        keywords
          .filter { it.match != KeywordMatch.EXACT }
          .map { KeywordMatcher.normalize(it.term) }
          .filter { it.isNotEmpty() && hits(out.text, listOf(it)) == 0 }
      out = addSkills(out, linkedIn, missing, added)
      out = addBullets(out, linkedIn, missing, added)
      out = addProjects(out, linkedIn, missing, added)
      out = addCertifications(out, linkedIn, missing, added)
    }
    return Tailored(rank(out, terms), added)
  }

  /** Times any of [terms] (normalized) is in [text], counting each term once. */
  fun hits(text: String, terms: List<String>): Int {
    val norm = KeywordMatcher.normalize(text)
    return terms.count { KeywordMatcher.count(norm, it) > 0 }
  }

  /** The skills rows and each entry's bullets with the job's words first; order kept otherwise. */
  private fun rank(doc: ResumeDoc, terms: List<String>): ResumeDoc =
    doc.copy(
      sections =
        doc.sections.map { section ->
          section.copy(
            entries =
              section.entries.map { entry ->
                entry.copy(
                  lines =
                    if (section.kind == PdfSection.SKILLS) {
                      entry.lines
                        .map { orderItems(it, terms) }
                        .sortedByDescending { hits(it, terms) }
                    } else entry.lines,
                  bullets = entry.bullets.sortedByDescending { hits(it, terms) },
                )
              }
          )
        }
    )

  /** `Label: a, b, c` with the items that are the job's words first. */
  private fun orderItems(line: String, terms: List<String>): String {
    val label = line.substringBefore(':', "").takeIf { it.isNotEmpty() && it.length < 40 }
    val items = (if (label != null) line.substringAfter(':') else line).split(',').map { it.trim() }
    if (items.size < 2) return line
    val sorted = items.sortedByDescending { hits(it, terms) }.joinToString(", ")
    return if (label != null) "$label: $sorted" else sorted
  }

  private fun addSkills(
    doc: ResumeDoc,
    p: LinkedInProfile,
    missing: List<String>,
    added: MutableList<String>,
  ): ResumeDoc {
    val skills = p.skills.filter { skill -> missing.any { hits(skill, listOf(it)) > 0 } }
    if (skills.isEmpty()) return doc
    added += skills.map { "Skill: $it" }
    val line = "Also skilled in: " + skills.joinToString(", ")
    return withEntry(doc, PdfSection.SKILLS, "Skills") { entries ->
      if (entries.isEmpty()) listOf(DocEntry(lines = listOf(line)))
      else entries.dropLast(1) + entries.last().let { it.copy(lines = it.lines + line) }
    }
  }

  private fun addBullets(
    doc: ResumeDoc,
    p: LinkedInProfile,
    missing: List<String>,
    added: MutableList<String>,
  ): ResumeDoc {
    val experience = doc.section(PdfSection.EXPERIENCE) ?: return doc
    val text = KeywordMatcher.normalize(doc.text)
    val used = mutableMapOf<String, Int>()
    val entries = experience.entries.toMutableList()
    for (position in p.positions) {
      val company = KeywordMatcher.normalize(position.company)
      if (company.isEmpty()) continue
      val at = entries.indexOfFirst {
        KeywordMatcher.normalize("${it.title} ${it.subtitle}").contains(company)
      }
      if (at < 0) continue
      for (bullet in ResumeDocs.bulletsOf(position.description)) {
        if (KeywordMatcher.normalize(bullet) in text) continue
        val words = missing.filter { hits(bullet, listOf(it)) > 0 }
        if (words.isEmpty() || words.all { (used[it] ?: 0) >= BULLETS_PER_WORD }) continue
        words.forEach { used[it] = (used[it] ?: 0) + 1 }
        entries[at] = entries[at].copy(bullets = entries[at].bullets + bullet)
        added += "Bullet at ${position.company}: $bullet"
      }
    }
    return replace(doc, experience, experience.copy(entries = entries))
  }

  private fun addProjects(
    doc: ResumeDoc,
    p: LinkedInProfile,
    missing: List<String>,
    added: MutableList<String>,
  ): ResumeDoc {
    val text = KeywordMatcher.normalize(doc.text)
    val projects =
      p.projects.filter {
        KeywordMatcher.normalize(it.title) !in text &&
          missing.any { word -> hits("${it.title} ${it.description}", listOf(word)) > 0 }
      }
    if (projects.isEmpty()) return doc
    added += projects.map { "Project: ${it.title}" }
    return withEntry(doc, PdfSection.PROJECTS, "Projects") {
      it + projects.map(ResumeDocs::project)
    }
  }

  private fun addCertifications(
    doc: ResumeDoc,
    p: LinkedInProfile,
    missing: List<String>,
    added: MutableList<String>,
  ): ResumeDoc {
    val text = KeywordMatcher.normalize(doc.text)
    val certifications =
      p.certifications.filter {
        KeywordMatcher.normalize(it.name) !in text &&
          missing.any { word -> hits(it.name, listOf(word)) > 0 }
      }
    if (certifications.isEmpty()) return doc
    added += certifications.map { "Certification: ${it.name}" }
    val lines = certifications.map(ResumeDocs::certification)
    return withEntry(doc, PdfSection.CERTIFICATIONS, "Certifications") { entries ->
      entries + DocEntry(lines = lines)
    }
  }

  /** [doc] with the [kind] section's entries changed, adding the section when it has none. */
  private fun withEntry(
    doc: ResumeDoc,
    kind: PdfSection,
    title: String,
    change: (List<DocEntry>) -> List<DocEntry>,
  ): ResumeDoc {
    val section = doc.section(kind)
    return if (section != null)
      replace(doc, section, section.copy(entries = change(section.entries)))
    else doc.copy(sections = doc.sections + DocSection(kind, title, change(emptyList())))
  }

  private fun replace(doc: ResumeDoc, old: DocSection, new: DocSection): ResumeDoc =
    doc.copy(sections = doc.sections.map { if (it === old) new else it })

  /** The sections in the user's order, without the hidden ones. */
  fun arrange(doc: ResumeDoc, prefs: ResumePdfPrefs): ResumeDoc {
    val order = prefs.fullOrder
    return doc.copy(
      sections =
        doc.sections.filter { it.kind !in prefs.hidden }.sortedBy { order.indexOf(it.kind) }
    )
  }

  /**
   * [doc] with one bullet less, for a resume over the page limit: the least related bullet of the
   * oldest entry that still has more than two. `null` when none can go.
   */
  fun shorten(doc: ResumeDoc, terms: List<String>): ResumeDoc? {
    val kinds = listOf(PdfSection.PROJECTS, PdfSection.EXPERIENCE, PdfSection.OTHER)
    for (section in kinds.flatMap { kind -> doc.sections.filter { it.kind == kind } }) {
      val at = section.entries.indexOfLast { it.bullets.size > 2 }
      if (at < 0) continue
      val entry = section.entries[at]
      val drop = entry.bullets.indices.minBy { hits(entry.bullets[it], terms) * 1000 - it }
      val entries = section.entries.toMutableList()
      entries[at] = entry.copy(bullets = entry.bullets.filterIndexed { i, _ -> i != drop })
      return replace(doc, section, section.copy(entries = entries))
    }
    return null
  }
}
