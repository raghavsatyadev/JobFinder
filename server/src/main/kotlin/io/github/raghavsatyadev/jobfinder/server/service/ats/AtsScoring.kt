package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsScores
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import kotlin.math.roundToInt

/**
 * The `ats-smart` arithmetic: overall out of 10 weighted keywords 30%, experience 30%, achievements
 * 25%, formatting, education and trajectory 5% each. Shortlisted only at 7.0 or more, a keyword
 * match of 60% or more and no hard disqualifier; a match under 40% is one.
 */
object AtsScoring {
  const val SHORTLIST_OVERALL = 7.0
  const val SHORTLIST_MATCH = 60
  const val DISQUALIFYING_MATCH = 40
  const val LOW_MATCH_DISQUALIFIER = "ATS keyword match under 40%"

  fun review(
    jobId: Long,
    jobText: String,
    resume: String,
    flags: List<ResumeFlag>,
    judgement: AtsJudgement,
    now: Long,
  ): AtsReview {
    val keywords = KeywordMatcher.match(judgement.keywords, jobText, resume)
    val match = KeywordMatcher.matchPercent(keywords)
    val scores =
      AtsScores(
        keywords = match / 10.0,
        experience = judgement.experience,
        achievements = judgement.achievements,
        formatting = formatting(flags),
        education = judgement.education,
        trajectory = judgement.trajectory,
      )
    val overall = overall(scores)
    val disqualifiers =
      Disqualifiers.keep(judgement.disqualifiers, jobText, resume).map { it.reason } +
        listOfNotNull(LOW_MATCH_DISQUALIFIER.takeIf { match < DISQUALIFYING_MATCH })
    val shortlisted =
      overall >= SHORTLIST_OVERALL && match >= SHORTLIST_MATCH && disqualifiers.isEmpty()
    return AtsReview(
      jobId = jobId,
      summary = AtsSummary(overall, match, shortlisted),
      scores = scores,
      keywords = keywords,
      disqualifiers = disqualifiers,
      assessment = judgement.assessment,
      fixes = judgement.fixes,
      formatting = flags,
      createdAt = now,
    )
  }

  /** 10, less 1.5 for each formatting problem. */
  fun formatting(flags: List<ResumeFlag>): Double = (10.0 - 1.5 * flags.size).coerceAtLeast(0.0)

  /** Rounded to one decimal. */
  fun overall(s: AtsScores): Double {
    val raw =
      0.30 * s.keywords +
        0.30 * s.experience +
        0.25 * s.achievements +
        0.05 * s.formatting +
        0.05 * s.education +
        0.05 * s.trajectory
    return (raw * 10).roundToInt() / 10.0
  }
}

/**
 * Keeps the model's disqualifiers that hold up: the quoted requirement is really in the job, and
 * not under a "nice to have" heading; the quoted dates are really in the resume.
 */
object Disqualifiers {
  private val OPTIONAL =
    Regex(
      "nice to have|nice-to-have|preferred|bonus|a plus|desirable|optional",
      RegexOption.IGNORE_CASE,
    )

  /** Shorter quotes match too much to prove anything. */
  private const val MIN_EVIDENCE_CHARS = 8

  fun keep(list: List<Disqualifier>, jobText: String, resume: String): List<Disqualifier> =
    list.filter { d ->
      val evidence = KeywordMatcher.normalize(d.evidence.trim('"', '\'', ' '))
      if (evidence.length < MIN_EVIDENCE_CHARS) return@filter false
      when (d.kind) {
        DisqualifierKind.DATES -> evidence in KeywordMatcher.normalize(resume)
        DisqualifierKind.YEARS,
        DisqualifierKind.MANDATORY -> required(evidence, jobText)
      }
    }

  /** The quote is on a line of the job that is not optional, nor under an optional heading. */
  private fun required(evidence: String, jobText: String): Boolean {
    val lines = jobText.lines()
    val at = lines.indexOfFirst { evidence in KeywordMatcher.normalize(it) }
    if (at < 0) return false
    if (OPTIONAL.containsMatchIn(lines[at])) return false
    val heading = (at - 1 downTo 0).map { lines[it] }.firstOrNull(::isHeading)
    return heading == null || !OPTIONAL.containsMatchIn(heading)
  }

  private fun isHeading(line: String): Boolean {
    val text = line.trim()
    if (text.isEmpty() || text.length > 60) return false
    return text.startsWith("#") || text.startsWith("**") || text.endsWith(":")
  }
}
