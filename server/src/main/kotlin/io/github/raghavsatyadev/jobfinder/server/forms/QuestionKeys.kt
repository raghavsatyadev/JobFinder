package io.github.raghavsatyadev.jobfinder.server.forms

/**
 * One form field as the browser extension describes it. [value] is set when the form was sent
 * (`saveAnswers`); [ref] names the field in an `answersFor` request.
 */
data class FormField(
  val ref: String? = null,
  val label: String? = null,
  val name: String? = null,
  val elementId: String? = null,
  val placeholder: String? = null,
  val ariaLabel: String? = null,
  val inputType: String = "text",
  val autocomplete: String? = null,
  /** The visible choices of a select or radio group. */
  val options: List<String> = emptyList(),
  val value: String? = null,
) {
  /** The words the field is known by, best first, as written on the page. */
  val descriptions: List<String>
    get() = listOfNotNull(label, ariaLabel, placeholder, name, elementId).filter { it.isNotBlank() }

  /** What the question said, for prompts and the stored row. */
  val question: String
    get() = listOfNotNull(label, ariaLabel, placeholder).firstOrNull { it.isNotBlank() } ?: ""
}

/**
 * Turns a form question into the key autofill matches on: a known key ("first_name") when a synonym
 * says which, else the cleaned question text. Labels and attribute names both go through
 * [normalise], so `job_application[firstName]` and "First name *" give the same key.
 */
object QuestionKeys {
  /** Lower case words of letters and digits; camelCase and snake_case split; filler words out. */
  fun normalise(text: String): String {
    val split =
      text
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
    return split.split(' ').filter { it.isNotEmpty() && it !in FILLER }.joinToString(" ")
  }

  /** The known key for [normalised] text, or `null`. */
  fun canonical(normalised: String): String? {
    if (normalised.isEmpty()) return null
    EXACT[normalised]?.let {
      return it
    }
    val padded = " $normalised "
    // Longest synonym first, so "expected ctc" wins over a shorter one inside it.
    return PHRASES.firstOrNull { (phrase, _) -> " $phrase " in padded }?.second
  }

  /** The key [field] is saved and matched under. */
  fun keyFor(field: FormField): String {
    val texts = field.descriptions.map(::normalise).filter { it.isNotEmpty() }
    texts.firstNotNullOfOrNull(::canonical)?.let {
      return it
    }
    return texts.firstOrNull()?.take(MAX_KEY_CHARS) ?: field.inputType
  }

  /** Whether [key] is one of the known keys rather than a question's own words. */
  fun isKnown(key: String): Boolean = key in KNOWN

  /** 0–1: how alike two normalised questions are, by shared words and letter pairs. */
  fun similarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    val wordsA = a.split(' ').toSet()
    val wordsB = b.split(' ').toSet()
    val words = 2.0 * (wordsA intersect wordsB).size / (wordsA.size + wordsB.size)
    val pairsA = bigrams(a)
    val pairsB = bigrams(b)
    val pairs =
      if (pairsA.isEmpty() || pairsB.isEmpty()) 0.0
      else 2.0 * (pairsA intersect pairsB).size / (pairsA.size + pairsB.size)
    return (words + pairs) / 2
  }

  private fun bigrams(s: String): Set<String> {
    val compact = s.replace(" ", "")
    return (0 until compact.length - 1).map { compact.substring(it, it + 2) }.toSet()
  }

  private const val MAX_KEY_CHARS = 160

  private val FILLER =
    setOf(
      "please",
      "kindly",
      "enter",
      "provide",
      "your",
      "the",
      "a",
      "an",
      "required",
      "optional",
      "mandatory",
      "field",
    )

  /** Known keys and the phrases that name them, already normalised. */
  private val SYNONYMS: Map<String, List<String>> =
    mapOf(
      "first_name" to listOf("first name", "given name", "forename", "fname", "firstname"),
      "middle_name" to listOf("middle name", "middlename"),
      "last_name" to listOf("last name", "surname", "family name", "lname", "lastname"),
      "full_name" to listOf("full name", "name", "legal name", "fullname", "candidate name"),
      "preferred_name" to listOf("preferred name", "nickname"),
      "email" to
        listOf(
          "email",
          "email address",
          "e mail",
          "mail id",
          "email id",
          "confirm email",
          "confirm email address",
          "re enter email",
        ),
      "phone" to
        listOf(
          "phone",
          "phone number",
          "mobile",
          "mobile number",
          "mobile no",
          "telephone",
          "contact number",
          "cell phone",
          "tel",
        ),
      "linkedin" to listOf("linkedin", "linkedin profile", "linkedin url", "linkedin profile url"),
      "github" to listOf("github", "github profile", "github url"),
      "website" to
        listOf("website", "portfolio", "personal website", "portfolio url", "website url", "blog"),
      "address" to listOf("address", "street address", "address line 1", "address1"),
      "city" to listOf("city", "town", "current city"),
      "state" to listOf("state", "province", "region", "state province"),
      "country" to listOf("country", "country of residence"),
      "postal_code" to listOf("zip", "zip code", "postal code", "postcode", "pin code", "pincode"),
      "location" to listOf("location", "current location", "where are you located"),
      "current_company" to
        listOf("current company", "current employer", "present employer", "company name"),
      "current_title" to
        listOf("current title", "current job title", "current designation", "current role"),
      "years_experience" to
        listOf(
          "years of experience",
          "total experience",
          "total years of experience",
          "years experience",
          "work experience in years",
          "how many years of experience",
        ),
      "notice_period" to listOf("notice period", "notice period in days", "notice"),
      "current_ctc" to
        listOf("current ctc", "current salary", "current compensation", "current annual salary"),
      "expected_ctc" to
        listOf(
          "expected ctc",
          "expected salary",
          "salary expectation",
          "salary expectations",
          "desired salary",
          "expected compensation",
        ),
      "work_authorization" to
        listOf(
          "authorized to work",
          "authorised to work",
          "work authorization",
          "work authorisation",
          "legally authorized",
          "right to work",
        ),
      "sponsorship" to
        listOf("require sponsorship", "visa sponsorship", "need sponsorship", "sponsorship"),
      "relocate" to listOf("willing to relocate", "relocation", "open to relocation", "relocate"),
      "start_date" to
        listOf("start date", "earliest start date", "when can you start", "available to start"),
      "how_heard" to
        listOf(
          "how did you hear about us",
          "how did you hear about this job",
          "how did you hear",
          "referral source",
        ),
      "gender" to listOf("gender", "gender identity", "sex"),
      "pronouns" to listOf("pronouns"),
      "date_of_birth" to listOf("date of birth", "dob", "birth date", "birthdate"),
      "nationality" to listOf("nationality", "citizenship"),
      "degree" to listOf("degree", "highest degree", "highest qualification", "qualification"),
      "university" to listOf("university", "college", "school", "institution"),
      "graduation_year" to listOf("graduation year", "year of graduation", "passing year"),
      "cover_letter" to listOf("cover letter", "coverletter"),
      "resume" to listOf("resume", "cv", "resume cv", "upload resume"),
    )

  private val KNOWN = SYNONYMS.keys

  /** Single words only count as the whole question; phrases also count inside a longer one. */
  private val EXACT: Map<String, String> =
    SYNONYMS.flatMap { (key, list) -> list.map { it to key } }.toMap()

  private val PHRASES: List<Pair<String, String>> =
    EXACT.entries
      .filter { it.key.contains(' ') }
      .map { it.key to it.value }
      .sortedByDescending { it.first.length }
}

/**
 * Fields the app never records or fills: passwords, card and bank details, ID numbers and one-time
 * codes, found by input type, `autocomplete` and the words around the field. The extension checks
 * the same before sending; this is the second check.
 */
object SensitiveFields {
  fun isSensitive(field: FormField): Boolean {
    val type = field.inputType.lowercase()
    if (type == "password" || type == "hidden") return true
    val auto = field.autocomplete.orEmpty().lowercase()
    if (auto.split(' ').any { it.startsWith("cc-") || it in SENSITIVE_AUTOCOMPLETE }) return true
    val words = field.descriptions.joinToString(" | ") { QuestionKeys.normalise(it) }
    if (SENSITIVE_WORDS.containsMatchIn(words)) return true
    val value = field.value.orEmpty()
    return type != "tel" && looksLikeCard(value)
  }

  /** 13–19 digits (spaces and dashes allowed) that pass the Luhn check. */
  fun looksLikeCard(value: String): Boolean {
    if (!Regex("^[0-9 \\-]{13,23}$").matches(value.trim())) return false
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 13..19) return false
    var sum = 0
    digits.reversed().forEachIndexed { i, c ->
      var d = c - '0'
      if (i % 2 == 1) {
        d *= 2
        if (d > 9) d -= 9
      }
      sum += d
    }
    return sum % 10 == 0
  }

  private val SENSITIVE_AUTOCOMPLETE =
    setOf("current-password", "new-password", "one-time-code", "transaction-amount")

  /** Matched against normalised words (lower case, single spaces). */
  private val SENSITIVE_WORDS =
    Regex(
      "\\b(password|passcode|passwd|pwd|cvv|cvc|csc|cvv2|card number|card no|credit card|" +
        "debit card|cardholder|card holder|expiry date|expiration date|card expiry|iban|swift|bic|" +
        "routing number|sort code|ifsc|bank|account number|account no|acct|ssn|social security|" +
        "aadhaar|aadhar|uidai|pan|pan number|pan card|passport|national id|national identification|" +
        "nin|tax id|tin|itin|ein|driver s licen[cs]e|driving licen[cs]e|licen[cs]e number|otp|" +
        "one time code|one time password|verification code|security code|security answer|" +
        "secret|pin)\\b(?! code)"
    )
}
