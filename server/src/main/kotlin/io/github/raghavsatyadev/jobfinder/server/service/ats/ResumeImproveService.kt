package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeDao
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovedResume
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementState
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeEdit
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeImprovement
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeSection
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * Fix plans for the resume against one job, written by the AI only when the user asks, from the
 * job's ATS review (the `ats-smart` fix plan). The code keeps an edit only when its "before" text
 * is really in the resume, and marks one that brings in a skill or a number the resume does not
 * show. Plans are kept in memory per job until the resume changes. The resume goes where the user
 * routed ATS work.
 */
class ResumeImproveService(
  private val resumes: ResumeDao,
  private val jobs: JobsDao,
  private val client: LlmClient,
  /** The job's ATS review against the current resume, or `null` when it has none yet. */
  private val review: suspend (Long) -> AtsReview?,
  private val events: ServerEvents,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  private val instructions: suspend () -> String = { INSTRUCTIONS },
  private val clock: () -> Long = System::currentTimeMillis,
) : ResumeImproveRepository {
  private val queue = Channel<Long>(Channel.UNLIMITED)
  private val queued = ConcurrentHashMap.newKeySet<Long>()

  /** By job: the resume hash it was written for, and the plan (`null` when it failed). */
  private val plans = ConcurrentHashMap<Long, Pair<String, ResumeImprovement?>>()
  private val json = Json { ignoreUnknownKeys = true }

  fun start(scope: CoroutineScope) {
    scope.launch {
      events.events.collect { if (it == ServerEvent.ResumeChanged) plans.clear() }
    }
    scope.launch {
      for (jobId in queue) {
        try {
          write(jobId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Resume fix plan for job {} failed: {}", jobId, e.message)
          resumes.get()?.let { plans[jobId] = it.hash to null }
        } finally {
          queued.remove(jobId)
        }
        events.emit(ServerEvent.ImprovementReady(jobId))
      }
    }
  }

  override suspend fun requestImprovement(jobId: Long, write: Boolean): ImprovementStatus {
    jobs.get(jobId) ?: notFound("Job")
    val hash = resumes.get()?.hash
    val stored = plans[jobId]?.takeIf { it.first == hash }
    return when {
      jobId in queued -> ImprovementStatus(ImprovementState.QUEUED, stored?.second)
      hash == null || review(jobId) == null -> ImprovementStatus(ImprovementState.NO_REVIEW)
      write -> {
        if (queued.add(jobId)) queue.trySend(jobId)
        ImprovementStatus(ImprovementState.QUEUED, stored?.second)
      }
      stored == null -> ImprovementStatus(ImprovementState.NONE)
      stored.second == null -> ImprovementStatus(ImprovementState.FAILED)
      else -> ImprovementStatus(ImprovementState.READY, stored.second)
    }
  }

  override suspend fun improvedResume(jobId: Long, edits: List<Int>): ImprovedResume {
    val resume = resumes.get() ?: notFound("Resume")
    val plan = plans[jobId]?.takeIf { it.first == resume.hash }?.second ?: notFound("Fix plan")
    val chosen = plan.edits.filter { it.id in edits }
    return ImprovedResume(apply(resume.text, chosen))
  }

  private suspend fun write(jobId: Long) {
    val resume = resumes.get() ?: return
    val job = jobs.get(jobId)?.toModel() ?: return
    val review = review(jobId) ?: return
    val system = instructions() + "\n\n" + OUTPUT_RULES
    val user = userPrompt(resume.text, job, review)
    val first = check(ask(system, user), resume.text, review.keywords)
    // One more round for the edits that lost the line's facts: the model rewrote instead of edited.
    val retry = first.rejected.filter { it.second in REPAIRABLE }.map { it.first }
    val edits =
      if (retry.isEmpty() || first.edits.size >= MAX_EDITS) first.edits
      else {
        val second = runCatching {
          ask(system, user + "\n\n" + repairPrompt(retry))
        }
          .getOrDefault(emptyList())
        check(second, resume.text, review.keywords, first.edits).edits
      }
    // The resume changed while the model worked: this plan is for the old one.
    if (resumes.get()?.hash != resume.hash) return
    plans[jobId] =
      resume.hash to
        ResumeImprovement(
          jobId = jobId,
          keywords = review.keywords,
          edits = edits,
          createdAt = clock(),
        )
  }

  private suspend fun ask(system: String, user: String): List<EditAnswer> {
    val completion =
      client.chat(
        task = AiTask.ATS,
        system = system,
        user = user,
        schemaName = SCHEMA_NAME,
        schema = SCHEMA,
        maxTokens = MAX_TOKENS,
      )
    val answer =
      try {
        json.decodeFromString(Answer.serializer(), completion.content)
      } catch (e: Exception) {
        throw LlmException("Unreadable ResumeFixPlan: ${completion.content.take(200)}", e)
      }
    usage.record(completion.inputTokens, completion.outputTokens)
    return answer.edits
  }

  @Serializable private data class Answer(val edits: List<EditAnswer>)

  @Serializable
  data class EditAnswer(
    val section: String,
    val before: String,
    val after: String,
    val reason: String,
  )

  companion object {
    private const val SCHEMA_NAME = "ResumeFixPlan"
    private const val MAX_TOKENS = 4_000
    private const val MAX_EDITS = 8
    private const val MAX_RESUME_CHARS = 14_000
    private const val MAX_JOB_CHARS = 8_000

    private const val LOSES_NUMBER = "loses a number"
    private const val LOSES_FACTS = "loses facts"
    private val REPAIRABLE = setOf(LOSES_NUMBER, LOSES_FACTS)

    /** Shorter quotes match too much to say where the edit goes. */
    private const val MIN_BEFORE_CHARS = 12
    private val log = LoggerFactory.getLogger("jobfinder.improve")

    private val SECTIONS = ResumeSection.entries.associateBy { it.name.lowercase() }
    private val BULLET = Regex("^[\\s•·▪◦*\\-–—]+")
    private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
    /**
     * A date such as `March 2025` or `2021 – Present`: the line names a role, not what was done.
     */
    private val ROLE_LINE =
      Regex(
        "\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(?:19|20)\\d{2}\\b|" +
          "\\b(?:19|20)\\d{2}\\s*[-–—]\\s*(?:present|current|now|(?:19|20)\\d{2})\\b",
        RegexOption.IGNORE_CASE,
      )
    private val BLANK = Regex("\\[[^\\[\\]]{1,20}]")

    /** What the AI is told; the user can edit it. */
    val INSTRUCTIONS: String =
      """You improve a resume for one job, the way a strict recruiter's fix plan would. You get the job, the resume and its ATS review: the job's keywords with how the resume covers them (exact, partial with the variant it uses, or missing), how often each appears, and the reviewer's assessment.

Write up to 8 edits, the ones that raise the keyword match and the recruiter's view the most first:
- Swap a variant for the job's exact term where the resume says the same thing another way.
- Weave a missing or once-only keyword into an existing bullet that is really about it; this is better than adding it to the skills list.
- Add the job's exact title or key terms to the summary when the resume supports them.
- Make a bullet lead with an action verb and show impact, scope or scale. Where a number would help but the resume has none, leave a blank such as [X%] or [N users] for the user to fill; never invent one.
- An edit changes one line and keeps its facts: "after" has every number of "before" and nearly all of its words, what was built and the technologies. Add or swap words; never replace a bullet with a different achievement, and never drop an item from a skills line.
- Only use skills and experience the resume shows. Never add a skill the resume has no sign of, and never change dates, titles or employers. A job keyword the resume has no sign of is a gap to report, not to write in.

For each edit:
- section: summary, experience, skills, projects or other
- before: the resume's text copied exactly, a whole bullet or line
- after: the full new text for that bullet or line, at most 35 words for a bullet
- reason: one short sentence on what it fixes"""

    private val OUTPUT_RULES: String =
      """Reply with JSON only: "edits", a list of objects with "section", "before", "after" and "reason"."""

    private val SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("edits") {
          put("type", "array")
          putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
              putJsonObject("section") {
                put("type", "string")
                putJsonArray("enum") { SECTIONS.keys.forEach { add(it) } }
              }
              putJsonObject("before") { put("type", "string") }
              putJsonObject("after") { put("type", "string") }
              putJsonObject("reason") { put("type", "string") }
            }
            put("required", required("section", "before", "after", "reason"))
            put("additionalProperties", false)
          }
        }
      }
      put("required", required("edits"))
      put("additionalProperties", false)
    }

    private fun required(vararg names: String) = buildJsonArray {
      names.forEach { add(JsonPrimitive(it)) }
    }

    fun userPrompt(resume: String, job: Job, review: AtsReview): String = buildString {
      appendLine("JOB")
      appendLine("Title: ${job.title}")
      appendLine("Company: ${job.companyName}")
      appendLine("Description:")
      appendLine(job.description.orEmpty().take(MAX_JOB_CHARS))
      appendLine()
      appendLine("ATS REVIEW")
      appendLine("Keyword match ${review.summary.atsMatch}%, overall ${review.summary.overall}/10")
      for (k in review.keywords) {
        val how =
          when (k.match) {
            KeywordMatch.EXACT -> "exact, ${k.count}x"
            KeywordMatch.PARTIAL -> "partial as \"${k.matchedAs}\""
            KeywordMatch.MISSING -> "missing"
          }
        appendLine("- ${k.term} (${k.category.name.lowercase()}): $how")
      }
      if (review.disqualifiers.isNotEmpty()) {
        appendLine("Gaps: ${review.disqualifiers.joinToString("; ")}")
      }
      appendLine("Assessment: ${review.assessment}")
      if (review.fixes.isNotEmpty()) appendLine("Suggested: ${review.fixes.joinToString(" ")}")
      appendLine()
      appendLine("RESUME")
      append(resume.take(MAX_RESUME_CHARS))
    }

    /** The edits kept, and the model's answers dropped with why. */
    class Checked(val edits: List<ResumeEdit>, val rejected: List<Pair<EditAnswer, String>>)

    /** Asks again for [rejected] edits, this time keeping each line's facts. */
    fun repairPrompt(rejected: List<EditAnswer>): String = buildString {
      appendLine("THESE EDITS WERE REJECTED")
      appendLine(
        "Each \"after\" dropped facts or numbers of its \"before\". Write them again as edits of " +
          "the same line: keep every number and nearly all of the original words, and only add " +
          "or swap words. If a line can't take the job's words truthfully, leave it out."
      )
      for (r in rejected) {
        appendLine("- before: ${r.before.replace(Regex("\\s+"), " ")}")
        appendLine("  rejected after: ${r.after}")
      }
    }

    /**
     * The model's edits that hold up: "before" is in the resume (where it is found first, not
     * overlapping an earlier edit), "after" says something new. Each is marked with the keywords it
     * gains and anything the user must check.
     */
    private fun check(
      answers: List<EditAnswer>,
      resume: String,
      keywords: List<AtsKeyword>,
      /** Edits kept from an earlier round: new ones go elsewhere and are numbered after them. */
      earlier: List<ResumeEdit> = emptyList(),
    ): Checked {
      val taken = earlier.mapNotNull { locate(it.before, resume) }.toMutableList()
      val edits = earlier.toMutableList()
      val rejected = mutableListOf<Pair<EditAnswer, String>>()
      for (answer in answers) {
        fun drop(why: String) {
          rejected += answer to why
        }
        if (edits.size == MAX_EDITS) break
        val before = answer.before.replace(BULLET, "").trim()
        val after = answer.after.replace(BULLET, "").trim()
        if (before.length < MIN_BEFORE_CHARS || after.isEmpty()) {
          drop("too short")
          continue
        }
        if (KeywordMatcher.normalize(before) == KeywordMatcher.normalize(after)) {
          drop("unchanged")
          continue
        }
        val range =
          locate(before, resume)
            ?: run {
              drop("not in the resume")
              null
            }
            ?: continue
        if (taken.any { it.first <= range.last && range.first <= it.last }) {
          drop("overlaps")
          continue
        }
        val found = resume.substring(range)
        val section = SECTIONS[answer.section.lowercase()] ?: ResumeSection.OTHER
        val oldNumbers = NUMBER.findAll(found).map { it.value }.toSet()
        // A real number dropped, or most of the line's facts gone: not an edit but a new line.
        if (!NUMBER.findAll(after).map { it.value }.toSet().containsAll(oldNumbers)) {
          drop(LOSES_NUMBER)
          continue
        }
        if (kept(found, after) < minKept(section)) {
          drop(LOSES_FACTS)
          continue
        }
        // A role's title, employer and dates line: those are facts, never reworded for a job.
        if (ROLE_LINE.containsMatchIn(found) && kept(found, after) < 1.0) {
          drop("changes a role line")
          continue
        }
        val was = KeywordMatcher.normalize(found)
        val now = KeywordMatcher.normalize(after)
        // Already exact elsewhere in the resume: the edit uses it again, the match stays.
        val gains = keywords.filter { k ->
          val term = KeywordMatcher.normalize(k.term)
          k.match != KeywordMatch.EXACT &&
            KeywordMatcher.count(now, term) > 0 &&
            KeywordMatcher.count(was, term) == 0
        }
        val blanks = BLANK.findAll(after).map { it.value }.distinct().toList()
        val withoutBlanks = BLANK.replace(after, " ")
        taken += range
        edits +=
          ResumeEdit(
            id = edits.size + 1,
            section = section,
            before = found,
            after = after,
            reason = answer.reason.trim(),
            gains = gains.map { it.term },
            newSkills = gains.filter { it.match == KeywordMatch.MISSING }.map { it.term },
            newNumbers =
              NUMBER.findAll(withoutBlanks)
                .map { it.value }
                .filter { it !in oldNumbers }
                .distinct()
                .toList(),
            blanks = blanks,
          )
      }
      log.info(
        "Fix plan: kept {} of {} edits; dropped {}",
        edits.size - earlier.size,
        answers.size,
        rejected.groupingBy { it.second }.eachCount(),
      )
      return Checked(edits, rejected)
    }

    /** The share of the line's facts an edit must keep: a skills line keeps its items. */
    private fun minKept(section: ResumeSection): Double =
      when (section) {
        ResumeSection.SKILLS -> 1.0
        ResumeSection.SUMMARY -> 0.6
        else -> 0.6
      }

    /**
     * The share of [before]'s content words that [after] still has, a word counting as kept when
     * one in [after] starts with its stem (so "integrated" keeps "integrating").
     */
    fun kept(before: String, after: String): Double {
      val old = words(before).toSet()
      if (old.isEmpty()) return 1.0
      val now = words(after)
      val kept = old.count { w ->
        val stem = w.take(maxOf(4, w.length - 3))
        now.any { it.startsWith(stem) }
      }
      return kept.toDouble() / old.size
    }

    private fun words(text: String): List<String> =
      text.lowercase().split(Regex("[^\\p{L}\\p{N}+#]+")).filter {
        it.length >= 3 && it !in STOP_WORDS
      }

    private val STOP_WORDS =
      setOf(
        "and",
        "the",
        "for",
        "with",
        "from",
        "into",
        "that",
        "this",
        "over",
        "via",
        "using",
        "across",
        "per",
        "each",
        "all",
        "its",
        "their",
        "our",
        "your",
        "while",
        "within",
      )

    /** Where [quote] is in [text], word by word with any spacing between; `null` when it is not. */
    fun locate(quote: String, text: String): IntRange? {
      val words = quote.split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (words.isEmpty()) return null
      val pattern = Regex(words.joinToString("\\s+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
      return pattern.find(text)?.range
    }

    /** [text] with each edit's "before" replaced by its "after", where it is found. */
    fun apply(text: String, edits: List<ResumeEdit>): String {
      val ranges =
        edits
          .mapNotNull { edit -> locate(edit.before, text)?.let { it to edit.after } }
          .sortedByDescending { it.first.first }
      val out = StringBuilder(text)
      var lowest = Int.MAX_VALUE
      for ((range, after) in ranges) {
        if (range.last >= lowest) continue
        out.replace(range.first, range.last + 1, after)
        lowest = range.first
      }
      return out.toString()
    }
  }
}
