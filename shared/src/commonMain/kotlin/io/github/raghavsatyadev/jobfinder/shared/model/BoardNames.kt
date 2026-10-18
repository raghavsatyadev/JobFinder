package io.github.raghavsatyadev.jobfinder.shared.model

/** Names for job boards the app has no parser for (custom boards), from their web address. */
object BoardNames {
  /** `jobs.jobgether.com` and `jobgether.com` are both `jobgether`; `x.co.uk` is `x`. */
  fun domainLabel(host: String): String {
    val labels = host.lowercase().removePrefix("www.").split('.')
    if (labels.size < 2) return labels.first()
    val second = labels[labels.size - 2]
    return if (second in SECOND_LEVEL && labels.size >= 3) labels[labels.size - 3] else second
  }

  /** `remote-jobs.example.co` gives "Remote Jobs". */
  fun fromHost(host: String): String =
    domainLabel(host)
      .split('-', '_')
      .filter { it.isNotBlank() }
      .joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
      }

  private val SECOND_LEVEL = setOf("co", "com", "org", "net", "ac", "gov", "edu")
}
