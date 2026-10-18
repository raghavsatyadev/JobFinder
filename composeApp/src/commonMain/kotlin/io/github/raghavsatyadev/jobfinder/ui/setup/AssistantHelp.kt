package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What an AI assistant (Claude, Gemini, ChatGPT or another) can help with during setup. */
enum class AssistantTask {
  /** Job boards and search pages for the user's skills, pasted back as searches. */
  JOB_BOARDS,
  /** The AI filter's description and companies to skip, pasted back into AI filters. */
  FILTERS,
  /** An ATS-friendly rewrite of the resume, uploaded again in Settings. */
  RESUME,
}

/** A chat assistant the user may already use, opened in the browser. */
enum class Assistant(val label: String, val url: String) {
  CLAUDE("Claude", "https://claude.ai/new"),
  GEMINI("Gemini", "https://gemini.google.com/app"),
  CHATGPT("ChatGPT", "https://chatgpt.com/"),
}

/** How pasting an answer back went. */
sealed interface AssistantResult {
  /** [added] searches saved; [failed] lines that were not a usable search page. */
  data class SearchesAdded(val added: Int, val failed: Int) : AssistantResult

  /** The AI filter now uses the pasted description, plus [companies] more companies to skip. */
  data class FiltersSaved(val companies: Int) : AssistantResult

  /** Nothing in the answer could be used. */
  data object NothingFound : AssistantResult

  data object Failed : AssistantResult
}

@Immutable
data class AssistantHelpState(
  /** The request to copy for each task, built from what the app knows. */
  val requests: Map<AssistantTask, String> = emptyMap(),
  val answers: Map<AssistantTask, String> = emptyMap(),
  val applying: AssistantTask? = null,
  val results: Map<AssistantTask, AssistantResult> = emptyMap(),
)

/**
 * Requests for an outside AI assistant, and the answers pasted back: searches to add and an AI
 * filter to save. The user attaches their resume to the chat themselves; nothing is sent from here.
 */
class AssistantHelpViewModel(
  private val scan: ScanRepository,
  private val sites: SitesRepository,
  private val matching: MatchingRepository,
  private val ats: AtsRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(AssistantHelpState())
  val state: StateFlow<AssistantHelpState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val boards = quietly { sites.listSites().map { it.name }.filter { "(" !in it } }.orEmpty()
      val filter = quietly { matching.getConfig() }
      val flags = quietly { ats.getResume()?.flags }.orEmpty()
      _state.update {
        it.copy(
          requests =
            mapOf(
              AssistantTask.JOB_BOARDS to AssistantRequests.jobBoards(boards),
              AssistantTask.FILTERS to AssistantRequests.filters(filter?.prompt.orEmpty()),
              AssistantTask.RESUME to AssistantRequests.resume(flags),
            )
        )
      }
    }
  }

  fun answerChanged(task: AssistantTask, text: String) {
    _state.update { it.copy(answers = it.answers + (task to text), results = it.results - task) }
  }

  fun apply(task: AssistantTask) {
    val answer = _state.value.answers[task].orEmpty()
    if (answer.isBlank() || _state.value.applying != null) return
    _state.update { it.copy(applying = task) }
    viewModelScope.launch {
      val result =
        try {
          when (task) {
            AssistantTask.JOB_BOARDS -> addSearches(answer)
            AssistantTask.FILTERS -> saveFilters(answer)
            AssistantTask.RESUME -> AssistantResult.NothingFound
          }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          AssistantResult.Failed
        }
      _state.update {
        it.copy(
          applying = null,
          results = it.results + (task to result),
          answers = if (result.applied) it.answers - task else it.answers,
        )
      }
    }
  }

  private suspend fun addSearches(answer: String): AssistantResult {
    val searches = AssistantAnswers.searches(answer)
    if (searches.isEmpty()) return AssistantResult.NothingFound
    var added = 0
    for ((title, url) in searches) {
      try {
        scan.createLink(title, url)
        added++
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // A page the app can't use as a search; counted below.
      }
    }
    return AssistantResult.SearchesAdded(added, searches.size - added)
  }

  private suspend fun saveFilters(answer: String): AssistantResult {
    val (prompt, companies) = AssistantAnswers.filters(answer)
    if (prompt.isBlank()) return AssistantResult.NothingFound
    val current = matching.getConfig()
    val known = current.blacklistedCompanies.map { it.lowercase() }.toSet()
    val more = companies.filter { it.lowercase() !in known }
    matching.updateConfig(
      current.copy(
        prompt = prompt.take(AdvancedMatchingConfig.MAX_PROMPT_LENGTH),
        blacklistedCompanies = current.blacklistedCompanies + more,
      )
    )
    return AssistantResult.FiltersSaved(more.size)
  }

  private val AssistantResult.applied: Boolean
    get() =
      this is AssistantResult.FiltersSaved || (this is AssistantResult.SearchesAdded && failed == 0)

  private suspend fun <T> quietly(block: suspend () -> T): T? =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
}

/** Reads the answers the requests ask for. */
object AssistantAnswers {
  private val URL = Regex("""https?://[^\s<>"'|)\]]+""")

  /** `Title | URL` lines (or bare URLs), at most [MAX_SEARCHES], each URL once. */
  fun searches(answer: String): List<Pair<String, String>> =
    answer
      .lines()
      .mapNotNull { line ->
        val url = URL.find(line)?.value?.trimEnd('.', ',', ';') ?: return@mapNotNull null
        val title =
          line
            .substringBefore(url)
            .trim()
            .trimStart('-', '*', '•', ' ')
            .replace(Regex("""^\d+[.)]\s*"""), "")
            .trimEnd('|', ':', '-', ' ')
            .replace("**", "")
            .trim()
        (title.ifEmpty { hostOf(url) }) to url
      }
      .distinctBy { it.second }
      .take(MAX_SEARCHES)

  /** The description, and the companies on a last `Companies:` line. */
  fun filters(answer: String): Pair<String, List<String>> {
    val lines = answer.trim().lines()
    val companiesLine = lines.lastOrNull { it.trim().startsWith("Companies:", ignoreCase = true) }
    val companies =
      companiesLine
        ?.substringAfter(':')
        ?.split(',')
        ?.map { it.trim().trim('.', '"') }
        ?.filter { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
        .orEmpty()
    val prompt =
      lines.filterNot { it === companiesLine }.joinToString("\n").trim().removeSurrounding("\"")
    return prompt to companies
  }

  private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').removePrefix("www.")

  const val MAX_SEARCHES = 30
}

/** The requests, in English: assistants answer best in the language they are asked in. */
object AssistantRequests {
  fun jobBoards(boards: List<String>): String =
    """
    |I'm looking for a job and use JobFinder, an app that checks job search result pages for new jobs. My resume is attached (if it isn't, ask me for my skills, target roles and locations first).
    |
    |Suggest the 10 to 15 best job search pages for me: the big boards and the niche boards for my field and country. For each, build the search on that board with my role, location or remote, recent jobs first (last 24 hours or last week), and my experience level, and give me the full web address of the search results page.
    |
    |JobFinder knows these boards best: ${boards.joinToString(", ").ifEmpty { "LinkedIn, Indeed, Glassdoor, Naukri" }}. Other boards work too.
    |
    |Answer with one line per search and nothing else, in this form:
    |Short title | full URL
    |For example: LinkedIn: Senior Android, Remote | https://www.linkedin.com/jobs/search/?keywords=...
    """
      .trimMargin()

  fun filters(current: String): String =
    """
    |I use JobFinder, an app whose AI reads each new job and hides the ones that don't match a short description I write. My resume is attached (if it isn't, ask me about my experience).
    |
    |First ask me up to 5 short questions about what I want: roles and seniority, lowest salary I'd take, locations and work mode (remote, hybrid, on-site), skills, domains or company types to avoid. Then write the description.
    |
    |Rules for the description: plain sentences, at most 1,500 characters, say what to filter OUT, and be concrete (skills, seniority levels, salary numbers with currency, work modes, places).
    |After the description, add one last line: Companies: name, name (companies to always skip, or "none").
    ${if (current.isBlank()) "" else "|\n    |My description today, to improve on:\n    |$current"}
    """
      .trimMargin()
      .trim()

  fun resume(flags: List<ResumeFlag>): String {
    val found = flags.mapNotNull(::describe)
    return """
    |My resume is attached. Help me make it ATS-friendly for the roles I'm applying to, and keep every fact true. Ask me for missing facts instead of inventing them.
    ${if (found.isEmpty()) "" else "|\n    |An automatic check found these problems:\n" + found.joinToString("\n") { "    |- $it" }}
    |
    |Rules: one column; standard headings (Summary, Experience, Skills, Education); a company, title and dates for every role; short bullet points that start with a verb and show results with numbers where possible; the keywords of my target roles where they are true; no tables, images, icons or text boxes; one or two pages.
    |
    |Give me the whole resume as plain text, ready to paste into a document.
    """
      .trimMargin()
      .trim()
  }

  private fun describe(flag: ResumeFlag): String? =
    when (flag) {
      ResumeFlag.TOO_SHORT -> "It's under 200 words, too thin for most roles."
      ResumeFlag.TOO_LONG -> "It's over 2 pages or 1,200 words."
      ResumeFlag.NO_EMAIL -> "No email address."
      ResumeFlag.NO_PHONE -> "No phone number."
      ResumeFlag.NO_EXPERIENCE_SECTION -> "No section titled Experience."
      ResumeFlag.NO_EDUCATION_SECTION -> "No section titled Education."
      ResumeFlag.NO_SKILLS_SECTION -> "No section titled Skills."
      ResumeFlag.NO_DATES -> "No dates for the roles."
      ResumeFlag.ODD_CHARACTERS -> "Icon fonts or symbols that come out as junk text."
      ResumeFlag.TABLES -> "Tables, which ATS software often reads in the wrong order."
      ResumeFlag.IMAGES -> "Images, which ATS software can't read."
      ResumeFlag.MULTI_COLUMN -> "Several columns, which ATS software often mixes up."
    }
}
