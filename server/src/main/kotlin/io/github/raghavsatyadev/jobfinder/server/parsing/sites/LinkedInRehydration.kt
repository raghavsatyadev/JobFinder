package io.github.raghavsatyadev.jobfinder.server.parsing.sites

/**
 * Reads LinkedIn's "como rehydration" script (`<script id="rehydrate-data">`) without running it.
 *
 * The old code took the text between the first `[` and the last `]` and evaluated it with `new
 * Function`, expecting an array of strings. Here the same slice is scanned for JS string literals
 * (single, double or back-tick quoted, without `${}`), which are decoded with JS escape rules;
 * anything else in the array is ignored. Each decoded chunk is then searched row by row as before.
 */
internal object LinkedInRehydration {
  private val COMPONENT_KEY =
    Regex("\"componentKey\":\"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\"")
  private val DISMISSED_STATE_JOB_ID = Regex("JobCardFrameworkImplDismissedState_(\\d+)")
  private val REACT_CONTEXT_JOB_ID =
    Regex(
      "JobCardFrameworkImpl(?:DismissedState|NotDismissedBooleanState|DismissedBooleanState|" +
        "FooterState)_(\\d+)"
    )

  /**
   * componentKey UUID to job id, from rows that hold both (old `buildJobIdMapFromComoRehydration`).
   */
  fun buildJobIdMap(rawScript: String): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (chunk in stringLiterals(rawScript)) {
      for (row in chunk.split('\n')) {
        val key = COMPONENT_KEY.find(row) ?: continue
        val id = DISMISSED_STATE_JOB_ID.find(row) ?: continue
        map[key.groupValues[1]] = id.groupValues[1]
      }
    }
    return map
  }

  /** The job id in an `f2a-react-context` attribute (old `extractJobIdFromReactContextAttr`). */
  fun jobIdFromReactContext(attr: String?): String? = attr?.let {
    REACT_CONTEXT_JOB_ID.find(it)?.groupValues?.get(1)
  }

  /** The decoded string literals between the first `[` and the last `]` of [raw]. */
  fun stringLiterals(raw: String): List<String> {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    if (start == -1 || end == -1 || end < start) return emptyList()
    val out = mutableListOf<String>()
    var i = start + 1
    while (i < end) {
      val c = raw[i]
      when {
        c == '"' || c == '\'' || c == '`' -> {
          val sb = StringBuilder()
          i = readLiteral(raw, i, end, sb)
          if (i < 0) return out // unterminated: keep what was read
          out.add(sb.toString())
        }
        c == '/' && i + 1 < end && raw[i + 1] == '*' -> {
          val close = raw.indexOf("*/", i + 2)
          i = if (close < 0) end else close + 2
        }
        c == '/' && i + 1 < end && raw[i + 1] == '/' -> {
          val nl = raw.indexOf('\n', i + 2)
          i = if (nl < 0) end else nl + 1
        }
        else -> i++
      }
    }
    return out
  }

  /** Reads the literal that opens at [open]; returns the index after its closing quote or -1. */
  private fun readLiteral(s: String, open: Int, limit: Int, sb: StringBuilder): Int {
    val quote = s[open]
    var i = open + 1
    while (i < limit) {
      val c = s[i]
      when {
        c == quote -> return i + 1
        c == '\\' && i + 1 < limit -> i = readEscape(s, i + 1, sb)
        else -> {
          sb.append(c)
          i++
        }
      }
    }
    return -1
  }

  /** Decodes the escape after a backslash at [at] - 1; returns the next index. */
  private fun readEscape(s: String, at: Int, sb: StringBuilder): Int {
    fun hex(from: Int, len: Int): Int? =
      if (from + len <= s.length) s.substring(from, from + len).toIntOrNull(16) else null
    return when (val e = s[at]) {
      'n' -> sb.append('\n').let { at + 1 }
      't' -> sb.append('\t').let { at + 1 }
      'r' -> sb.append('\r').let { at + 1 }
      'b' -> sb.append('\b').let { at + 1 }
      'f' -> sb.append('\u000C').let { at + 1 }
      'v' -> sb.append('\u000B').let { at + 1 }
      '0' -> sb.append('\u0000').let { at + 1 }
      'x' ->
        hex(at + 1, 2)?.let {
          sb.append(it.toChar())
          at + 3
        } ?: sb.append(e).let { at + 1 }
      'u' ->
        if (s.getOrNull(at + 1) == '{') {
          val close = s.indexOf('}', at + 2)
          val cp = if (close > 0) s.substring(at + 2, close).toIntOrNull(16) else null
          if (cp != null) {
            sb.appendCodePoint(cp)
            close + 1
          } else sb.append(e).let { at + 1 }
        } else {
          hex(at + 1, 4)?.let {
            sb.append(it.toChar())
            at + 5
          } ?: sb.append(e).let { at + 1 }
        }
      '\r' -> if (s.getOrNull(at + 1) == '\n') at + 2 else at + 1 // line continuation
      '\n',
      ' ',
      ' ' -> at + 1
      else -> sb.append(e).let { at + 1 }
    }
  }
}
