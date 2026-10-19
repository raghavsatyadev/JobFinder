package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A job's title and description in the user's language. Job lists carry only [title]; the
 * single-job call carries [description] too. Languages are ISO 639-1 codes ([JobLanguages]).
 */
@Serializable
data class JobTranslation(
  /** The language the job was posted in. */
  val from: String,
  /** The user's language ([ScannerSettings.translateTo]). */
  val to: String,
  val title: String,
  val description: String? = null,
)

@Serializable
enum class TranslationState {
  /** Translation is off in Settings. */
  @SerialName("off") OFF,
  /** Waiting for the AI. */
  @SerialName("queued") QUEUED,
  @SerialName("ready") READY,
  /** The job is already in the user's language, or its language is not known. */
  @SerialName("same") SAME,
  /** The AI gave no usable translation; the original is shown. */
  @SerialName("failed") FAILED,
  /** The phone has no computer to ask and no kept copy. */
  @SerialName("unavailable") UNAVAILABLE,
}

/** Where a job's translation stands; [from] is set once its language is known. */
@Serializable
data class TranslationStatus(
  val state: TranslationState,
  val translation: JobTranslation? = null,
  val from: String? = null,
)

/** The languages jobs can be translated into and recognized in, as ISO 639-1 codes. */
object JobLanguages {
  const val ENGLISH = "en"

  /** Every language [LanguageGuess] knows, in the order the Settings picker lists them. */
  val ALL: List<String> = listOf("en", "de", "fr", "nl", "es", "it", "pt", "sv", "da", "nb", "pl")

  /** The English name, for the AI's instructions. */
  fun englishName(code: String): String =
    when (code) {
      "en" -> "English"
      "de" -> "German"
      "fr" -> "French"
      "nl" -> "Dutch"
      "es" -> "Spanish"
      "it" -> "Italian"
      "pt" -> "Portuguese"
      "sv" -> "Swedish"
      "da" -> "Danish"
      "nb" -> "Norwegian"
      "pl" -> "Polish"
      else -> code
    }
}

/**
 * Guesses the language of a job posting from its common short words (no model, no download). Good
 * enough to tell a German or French posting from an English one; `null` when the text is too short
 * or mixed to say.
 */
object LanguageGuess {
  private val STOPWORDS: Map<String, Set<String>> =
    mapOf(
      "en" to
        words(
          "the and of to in for with you we our are is will on as be your this that have or an " +
            "at from by experience team work skills role about who what"
        ),
      "de" to
        words(
          "und der die das wir sie mit für ist ein eine im auf zu von den des dem bei du dich " +
            "dein deine ihre unser unsere oder als auch sich werden wird nicht kenntnisse " +
            "erfahrung aufgaben bereich"
        ),
      "fr" to
        words(
          "et le la les des du une un pour avec vous nous est dans sur au aux en de votre vos " +
            "nos notre sont être par ou qui que poste équipe expérience"
        ),
      "nl" to
        words(
          "en de het een van voor met je jij wij we zijn is op in te bij ons onze jouw als " +
            "ook naar wordt niet heb hebt ervaring werk"
        ),
      "es" to
        words(
          "y el la los las de del un una para con en es por que se tu su nuestro nuestra " +
            "experiencia equipo trabajo como más"
        ),
      "it" to
        words(
          "e il lo la gli le di del della un una per con in è che si nostro nostra sono " +
            "esperienza lavoro come anche più"
        ),
      "pt" to
        words(
          "e o a os as de do da dos das um uma para com em é que se nosso nossa você são " +
            "experiência equipe trabalho como"
        ),
      "sv" to
        words(
          "och att det som en ett för med på är av till vi du har vår våra din erfarenhet " +
            "arbete kommer inte"
        ),
      "da" to
        words(
          "og at det som en et for med på er af til vi du har vores din erfaring arbejde " +
            "ikke vil kan"
        ),
      "nb" to
        words(
          "og at det som en et for med på er av til vi du har vår våre din erfaring arbeid " +
            "ikke vil kan"
        ),
      "pl" to
        words(
          "i w na z do się jest dla oraz nie że jak od po przez nasz nasza twoje doświadczenie " +
            "pracy praca zespół"
        ),
    )

  /** "(m/w/d)", "(w/m/d)", "(m/f/d)": German postings mark the genders in the title. */
  private val GERMAN_GENDER_MARK = Regex("""\((?:m|w|f|d|x|i)(?:\s*/\s*(?:m|w|f|d|x|i)){1,3}\)""")

  private val WORD = Regex("""\p{L}+""")

  /** The language of [title] and [description], or `null` when unsure. */
  fun guess(title: String, description: String?): String? {
    val text = (description.orEmpty().take(SAMPLE_CHARS) + " " + title).lowercase()
    val tokens = WORD.findAll(text).map { it.value }.toList()
    val scores =
      STOPWORDS.mapValues { (_, set) -> tokens.count { it in set } }
        .entries
        .sortedByDescending { it.value }
    val best = scores.first()
    val second = scores.getOrNull(1)?.value ?: 0
    if (best.value >= MIN_HITS && best.value >= second * MARGIN) return best.key
    // A title alone has few short words; the German gender mark gives it away.
    if (GERMAN_GENDER_MARK.containsMatchIn(title.lowercase()) && best.value < MIN_HITS) return "de"
    return null
  }

  private fun words(list: String): Set<String> = list.split(' ').filter(String::isNotEmpty).toSet()

  private const val SAMPLE_CHARS = 4_000
  private const val MIN_HITS = 8
  private const val MARGIN = 1.3
}
