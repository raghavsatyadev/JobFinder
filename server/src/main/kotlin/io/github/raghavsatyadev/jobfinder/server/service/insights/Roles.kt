package io.github.raghavsatyadev.jobfinder.server.service.insights

/** A role name as salary sites spell it, and whether it is the job's own role. */
data class RoleCandidate(val name: String, val exact: Boolean) {
  /** The page name: "Senior Android Developer" is `senior-android-developer`. */
  val slug: String
    get() = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

/**
 * Turns a job title into the roles to look up, the job's own role first, then the nearest ones:
 * "Senior Software Engineer - Android" gives Senior Android Engineer, Senior Android Developer,
 * Senior Software Engineer, Android Engineer, Android Developer, Software Engineer. A title without
 * a developer or engineer word is looked up as it is, never as a software role.
 */
object Roles {
  private val LEVELS =
    mapOf(
      "senior" to "Senior",
      "sr" to "Senior",
      "lead" to "Lead",
      "staff" to "Staff",
      "principal" to "Principal",
      "junior" to "Junior",
      "jr" to "Junior",
      "associate" to "Associate",
    )

  /** Level numbers after a role word: "SDE 2", "Engineer II", "Software Engineer L3". */
  private val NUMBERS =
    mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "1" to 1, "2" to 2, "3" to 3, "4" to 4)

  /** Longest first, so "react native" wins over "react". */
  private val DOMAINS =
    listOf(
      "react native" to "React Native",
      "machine learning" to "Machine Learning",
      "full stack" to "Full Stack",
      "fullstack" to "Full Stack",
      "front end" to "Frontend",
      "frontend" to "Frontend",
      "back end" to "Backend",
      "backend" to "Backend",
      "android" to "Android",
      "ios" to "iOS",
      "flutter" to "Flutter",
      "mobile" to "Mobile",
      "kotlin" to "Kotlin",
      "java" to "Java",
      "python" to "Python",
      "devops" to "DevOps",
      "embedded" to "Embedded",
      "web" to "Web",
      "data" to "Data",
      "qa" to "QA",
      "cloud" to "Cloud",
    )

  fun forTitle(title: String): List<RoleCandidate> {
    val words = title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(" ")
    val text = " ${words.joinToString(" ")} "
    val level = words.firstNotNullOfOrNull { LEVELS[it] }
    val number = levelNumber(words)
    val domain = DOMAINS.firstOrNull { (key, _) -> " $key " in text }?.second
    val isSde = "sde" in words || "software development engineer" in text
    val functions =
      when {
        "engineer" in words && "developer" !in words -> listOf("Engineer", "Developer")
        else -> listOf("Developer", "Engineer")
      }
    // Not a software role: only the title itself, never a software engineer's salary.
    if (!isSde && words.none { it in ROLE_WORDS }) {
      return listOfNotNull(plainTitle(title)?.let { RoleCandidate(it, exact = true) })
    }
    val numbered = number?.let { " $it" }.orEmpty()
    val leveled = level != null || number != null
    val generic = domain == null

    // The job's own role, then the same level in general terms, then the role without the level.
    val roles = buildList {
      if (domain != null) {
        functions.forEach { add(RoleCandidate(join(level, domain, it) + numbered, exact = true)) }
      }
      val sdeLevel = number?.let { RoleCandidate("SDE $it", exact = generic && isSde) }
      val engineerLevel =
        if (leveled) RoleCandidate(join(level, "Software Engineer") + numbered, generic && !isSde)
        else null
      if (isSde) addAll(listOfNotNull(sdeLevel, engineerLevel))
      else addAll(listOfNotNull(engineerLevel, sdeLevel))
      if (domain != null && leveled) {
        functions.forEach { add(RoleCandidate(join(domain, it), exact = false)) }
      }
      if (isSde) add(RoleCandidate("SDE", exact = generic && !leveled))
      add(RoleCandidate("Software Engineer", exact = generic && !leveled && !isSde))
      add(RoleCandidate("Software Developer", exact = generic && !leveled && !isSde))
    }
    return roles.distinctBy { it.slug }
  }

  /** "Senior UX Researcher - Remote (EU)" gives "Senior UX Researcher". */
  private fun plainTitle(title: String): String? =
    title.split(Regex("\\s[-–|]\\s|[(,|]")).first().replace(Regex("\\s+"), " ").trim().takeIf {
      it.isNotEmpty()
    }

  private fun levelNumber(words: List<String>): Int? {
    for ((i, word) in words.withIndex()) {
      if (word.length == 2 && word[0] == 'l' && word[1].isDigit()) return word[1].digitToInt()
      if (i > 0 && words[i - 1] in ROLE_WORDS)
        NUMBERS[word]?.let {
          return it
        }
    }
    return null
  }

  private val ROLE_WORDS = setOf("engineer", "developer", "sde", "programmer")

  private fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" ")
}
