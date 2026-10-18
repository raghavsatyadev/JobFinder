package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Each kind of work the app gives the AI. Each has its own ordered list of models. */
@Serializable
enum class AiTask(
  /** The prompt carries the resume, so by default it stays on this computer. */
  val usesResume: Boolean
) {
  /** The AI filter: keep or exclude each new job. */
  @SerialName("matching") MATCHING(false),
  /** Reading a job list or description page no parser knows. */
  @SerialName("parsing") PARSING(false),
  /** Reading company ratings and salary pages. */
  @SerialName("insights") INSIGHTS(false),
  /** Is the job on the company's own careers site. */
  @SerialName("ghost") GHOST(false),
  @SerialName("ats") ATS(true),
  @SerialName("tips") TIPS(true),
  /** Drafting answers to application forms. */
  @SerialName("answers") ANSWERS(true),
}

/**
 * An online API key the user added. The key itself is never here: it stays in the computer's
 * protected store under [id]. Limits are per day (reset at midnight local time) and per minute;
 * `null` means no limit is kept.
 */
@Serializable
data class AiKey(
  val id: String,
  val provider: AiProvider,
  /** Shown instead of the provider's name, e.g. "Groq (work account)". */
  val label: String? = null,
  /** `null` uses the provider's own address. */
  val baseUrl: String? = null,
  val requestsPerMinute: Int? = null,
  val requestsPerDay: Int? = null,
  val tokensPerDay: Long? = null,
  val createdAt: Long = 0,
  /** When the site stops taking the key; `null` when unknown or never. */
  val expiresAt: Long? = null,
) {
  val endpoint: String
    get() = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: provider.defaultBaseUrl

  fun expired(now: Long): Boolean = expiresAt != null && expiresAt <= now

  /** Whole days until [expiresAt], rounded up; `null` without a date, negative once past. */
  fun daysLeft(now: Long): Long? = expiresAt?.let { at ->
    val ms = at - now
    if (ms <= 0) ms / DAY_MS - 1 else (ms + DAY_MS - 1) / DAY_MS
  }

  companion object {
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** The expiry to choose when making a key: a leaked key then stops working by itself. */
    const val SUGGESTED_EXPIRY_DAYS = 90

    /** From this many days left the key shows as expiring soon. */
    const val EXPIRY_WARNING_DAYS = 14
  }
}

/**
 * One choice in a task's list: a model on an added key, [OPEN_CODE] (the OpenCode CLI on this
 * computer), [AGY] (the Antigravity CLI on this computer), or [LOCAL]: the main AI model choice.
 */
@Serializable
data class AiRoute(
  /** `null`: the model chosen above in "AI model", usually the one on this computer. */
  val keyId: String? = null,
  val model: String = "",
) {
  val local: Boolean
    get() = keyId == null

  /** The free models of the OpenCode CLI installed on this computer. */
  val openCode: Boolean
    get() = keyId == OPEN_CODE_ID

  /** The Gemini models of the Antigravity CLI (`agy`) installed on this computer. */
  val agy: Boolean
    get() = keyId == AGY_ID

  companion object {
    val LOCAL = AiRoute()

    /** The id [OPEN_CODE] takes in place of a key's; never a real key's id (those are UUIDs). */
    const val OPEN_CODE_ID = "opencode-cli"

    /** Blank model: the one chosen in [OpenCodeChoice]. */
    val OPEN_CODE = AiRoute(OPEN_CODE_ID)

    /** The id [AGY] takes in place of a key's; never a real key's id. */
    const val AGY_ID = "agy-cli"

    /** Blank model: the one chosen in [AgyChoice]. */
    val AGY = AiRoute(AGY_ID)
  }
}

/**
 * The OpenCode CLI as a free model source: no key, the user's installed `opencode` runs its free
 * models. [model] is tried first; the other [OpenCodeModels.FREE] follow when it is out of quota.
 */
@Serializable
data class OpenCodeChoice(val enabled: Boolean = false, val model: String = OpenCodeModels.DEFAULT)

/** The OpenCode free models the app uses, in fallback order (October 2026, opencode v2). */
object OpenCodeModels {
  /** Zero retention and no training (DeepSeek, GLM), then Beam (kept 30 days). */
  val FREE: List<String> = listOf("deepseek-v4.1-flash-free", "glm-5.3-flash-free", "beam-free")

  val DEFAULT: String = FREE.first()

  /** [preferred] first, then the rest of [FREE]. */
  fun order(preferred: String): List<String> =
    (listOf(preferred.trim()).filter { it.isNotEmpty() } + FREE).distinct()
}

/**
 * The Antigravity CLI (`agy`) as a model source: no key, the user's signed-in `agy` answers with
 * Gemini Flash at the thinking level in [model].
 */
@Serializable
data class AgyChoice(val enabled: Boolean = false, val model: String = AgyModels.DEFAULT)

/** The Antigravity CLI models the app offers (October 2026, agy 1.4): Gemini Flash by level. */
object AgyModels {
  val LEVELS: List<String> =
    listOf("gemini-3.8-flash-low", "gemini-3.8-flash-medium", "gemini-3.8-flash-high")

  val DEFAULT: String = LEVELS.first()
}

/** Which models do which work, tried in order; a model out of quota hands the work to the next. */
@Serializable
data class AiRouting(
  val routes: Map<AiTask, List<AiRoute>> = emptyMap(),
  val openCode: OpenCodeChoice = OpenCodeChoice(),
  val agy: AgyChoice = AgyChoice(),
) {
  /** [task]'s list; a task never set up uses [defaultFor]. The CLIs only while turned on. */
  fun forTask(task: AiTask, keys: List<AiKey>): List<AiRoute> =
    (routes[task] ?: defaultFor(task, keys, openCode.enabled, agy.enabled)).filter { r ->
      r.local ||
        (r.openCode && openCode.enabled) ||
        (r.agy && agy.enabled) ||
        keys.any { it.id == r.keyId }
    }

  companion object {
    /**
     * Public job text goes to the online keys first (most free quota first), then the OpenCode CLI
     * when [openCode] is on, then the Antigravity CLI when [agy] is on, then this computer. Work
     * with the resume stays on this computer.
     */
    fun defaultFor(
      task: AiTask,
      keys: List<AiKey>,
      openCode: Boolean = false,
      agy: Boolean = false,
    ): List<AiRoute> =
      if (task.usesResume) listOf(AiRoute.LOCAL)
      else
        keys
          .sortedBy { FreeAiCatalog.rank(it.provider) }
          .map {
            AiRoute(it.id, FreeAiCatalog.of(it.provider)?.model ?: it.provider.suggestedModel)
          } +
          listOfNotNull(AiRoute.OPEN_CODE.takeIf { openCode }, AiRoute.AGY.takeIf { agy }) +
          AiRoute.LOCAL
  }
}

/** How much of a key's quota today's work used. */
@Serializable data class AiKeyUsage(val keyId: String, val requests: Int = 0, val tokens: Long = 0)

/** What the app knows about a free tier: limits to start from, the model to use and the terms. */
data class FreeTier(
  val provider: AiProvider,
  /** Where the user makes a key. */
  val keyPage: String,
  val model: String,
  val requestsPerMinute: Int?,
  val requestsPerDay: Int?,
  val tokensPerDay: Long?,
  /** The tasks it suits, best first. */
  val suits: List<AiTask>,
  /** The free tier may keep or train on prompts. */
  val keepsPrompts: Boolean,
)

/**
 * The free tiers, most free quota first (October 2026). Kept in one place: the setup guide, the
 * "Add a key" suggestions and the default routing all read this order.
 */
object FreeAiCatalog {
  private val PUBLIC_TEXT = listOf(AiTask.MATCHING, AiTask.PARSING, AiTask.INSIGHTS, AiTask.GHOST)

  val TIERS: List<FreeTier> =
    listOf(
      FreeTier(
        AiProvider.NVIDIA,
        "https://build.nvidia.com/settings/api-keys",
        "deepseek-ai/deepseek-v4.1-flash",
        requestsPerMinute = 40,
        requestsPerDay = null,
        tokensPerDay = null,
        suits = PUBLIC_TEXT,
        keepsPrompts = false,
      ),
      FreeTier(
        AiProvider.GROQ,
        "https://console.groq.com/keys",
        "openai/gpt-oss-120b",
        requestsPerMinute = 30,
        requestsPerDay = 1_000,
        tokensPerDay = 200_000,
        suits = listOf(AiTask.GHOST, AiTask.INSIGHTS, AiTask.MATCHING),
        keepsPrompts = false,
      ),
      FreeTier(
        AiProvider.OPENROUTER,
        "https://openrouter.ai/settings/keys",
        "deepseek/deepseek-v4-flash:free",
        requestsPerMinute = 20,
        requestsPerDay = 50,
        tokensPerDay = null,
        suits = listOf(AiTask.GHOST, AiTask.INSIGHTS),
        keepsPrompts = true,
      ),
      FreeTier(
        AiProvider.GEMINI,
        "https://aistudio.google.com/apikey",
        "gemini-2.5-flash",
        requestsPerMinute = 10,
        requestsPerDay = 250,
        tokensPerDay = null,
        suits = listOf(AiTask.PARSING, AiTask.INSIGHTS),
        keepsPrompts = true,
      ),
    )

  fun of(provider: AiProvider): FreeTier? = TIERS.firstOrNull { it.provider == provider }

  /** Position in the quota order; providers without a free tier come last. */
  fun rank(provider: AiProvider): Int =
    TIERS.indexOfFirst { it.provider == provider }.takeIf { it >= 0 } ?: TIERS.size
}
