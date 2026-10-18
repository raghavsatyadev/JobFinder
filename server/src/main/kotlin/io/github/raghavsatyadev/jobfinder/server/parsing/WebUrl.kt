package io.github.raghavsatyadev.jobfinder.server.parsing

import java.net.IDN

/**
 * The subset of the WHATWG `URL` / `URLSearchParams` behaviour the old code relied on, for
 * hierarchical URLs (`http`, `https` and the like).
 *
 * `java.net.URI` rejects URLs that browsers accept (raw spaces, `|`, `"`) and Ktor's `Url`
 * serialises differently, so `cleanJobUrl` would not produce the same saved link. This class keeps
 * what matters for parity:
 * - host lower-cased (IDN to punycode), default port dropped, empty path becomes `/`, dot segments
 *   resolved, `\` read as `/`;
 * - path, query and fragment percent-encoded with the WHATWG encode sets;
 * - once [searchParams] is changed, the query is re-serialised as
 *   `application/x-www-form-urlencoded` (space as `+`, everything but `*-._` and alphanumerics
 *   percent-encoded), exactly what `URLSearchParams.set/delete` does.
 *
 * Not covered: opaque URLs (`mailto:`), relative URLs, IPv6 and IPv4 number normalisation.
 */
class WebUrl
private constructor(
  val scheme: String,
  private val userInfo: String,
  val hostname: String,
  private val port: String,
  val pathname: String,
  private var query: String?,
  private val fragment: String?,
) {
  /** `url.searchParams`; changes are written back to the URL, like the DOM. */
  val searchParams: SearchParams by lazy { SearchParams(query.orEmpty()) { query = it } }

  /** `url.search`: `""` or `?` + query. */
  val search: String
    get() = query?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""

  override fun toString(): String = buildString {
    append(scheme).append("://")
    if (userInfo.isNotEmpty()) append(userInfo).append('@')
    append(hostname)
    if (port.isNotEmpty()) append(':').append(port)
    append(pathname)
    query?.let { append('?').append(it) }
    fragment?.let { append('#').append(it) }
  }

  /** `URLSearchParams`: an ordered list of name/value pairs. */
  class SearchParams internal constructor(query: String, private val onChange: (String?) -> Unit) {
    private val pairs: MutableList<Pair<String, String>> = parseForm(query).toMutableList()

    fun get(name: String): String? = pairs.firstOrNull { it.first == name }?.second

    fun delete(name: String) {
      pairs.removeAll { it.first == name }
      update()
    }

    /** Replaces the first `name` pair (dropping the others) or appends one. */
    fun set(name: String, value: String) {
      val index = pairs.indexOfFirst { it.first == name }
      if (index < 0) {
        pairs.add(name to value)
      } else {
        pairs[index] = name to value
        var i = pairs.size - 1
        while (i > index) {
          if (pairs[i].first == name) pairs.removeAt(i)
          i--
        }
      }
      update()
    }

    override fun toString(): String = serializeForm(pairs)

    private fun update() = onChange(serializeForm(pairs).ifEmpty { null })
  }

  companion object {
    private val DEFAULT_PORTS =
      mapOf("http" to "80", "https" to "443", "ws" to "80", "wss" to "443")

    /** `new URL(input)`; throws [IllegalArgumentException] ("Invalid URL") like the DOM throws. */
    fun parse(input: String): WebUrl {
      val s = input.trim { it <= ' ' }.filterNot { it == '\t' || it == '\n' || it == '\r' }
      val colon = s.indexOf(':')
      require(colon > 0 && s[0].isLetter() && s.substring(0, colon).all(::isSchemeChar)) {
        "Invalid URL: $input"
      }
      val scheme = s.substring(0, colon).lowercase()
      var rest = s.substring(colon + 1)
      rest = rest.trimStart('/', '\\')

      val authorityEnd = rest.indexOfFirst { it == '/' || it == '\\' || it == '?' || it == '#' }
      val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
      rest = if (authorityEnd < 0) "" else rest.substring(authorityEnd)

      val at = authority.lastIndexOf('@')
      val userInfo = if (at < 0) "" else encode(authority.substring(0, at), ::inUserInfoSet)
      val hostPort = if (at < 0) authority else authority.substring(at + 1)
      val portSep = hostPort.lastIndexOf(':')
      val rawHost = if (portSep < 0) hostPort else hostPort.substring(0, portSep)
      var port = if (portSep < 0) "" else hostPort.substring(portSep + 1)
      require(port.all(Char::isDigit)) { "Invalid URL: $input" }
      if (port.isNotEmpty()) port = port.trimStart('0').ifEmpty { "0" }
      if (DEFAULT_PORTS[scheme] == port) port = ""
      val hostname = parseHost(percentDecode(rawHost))
      require(hostname.isNotEmpty()) { "Invalid URL: $input" }

      val hash = rest.indexOf('#')
      val fragment = if (hash < 0) null else encode(rest.substring(hash + 1), ::inFragmentSet)
      if (hash >= 0) rest = rest.substring(0, hash)
      val q = rest.indexOf('?')
      val query = if (q < 0) null else encode(rest.substring(q + 1), ::inSpecialQuerySet)
      val path = if (q < 0) rest else rest.substring(0, q)

      return WebUrl(scheme, userInfo, hostname, port, normalizePath(path), query, fragment)
    }

    /** `new URLSearchParams(query)`: `a=1&b=2` (a leading `?` is ignored). */
    fun parseForm(query: String): List<Pair<String, String>> =
      query
        .removePrefix("?")
        .split('&')
        .filter { it.isNotEmpty() }
        .map { part ->
          val eq = part.indexOf('=')
          val name = if (eq < 0) part else part.substring(0, eq)
          val value = if (eq < 0) "" else part.substring(eq + 1)
          formDecode(name) to formDecode(value)
        }

    private fun serializeForm(pairs: List<Pair<String, String>>): String =
      pairs.joinToString("&") { (k, v) -> formEncode(k) + "=" + formEncode(v) }

    private fun formDecode(s: String): String = percentDecode(s.replace('+', ' '))

    private fun formEncode(s: String): String = buildString {
      for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = (b.toInt() and 0xFF).toChar()
        when {
          c == ' ' -> append('+')
          c.isAsciiAlphanumeric() || c == '*' || c == '-' || c == '.' || c == '_' -> append(c)
          else -> appendPercent(b)
        }
      }
    }

    /** Percent-decodes to bytes and reads them as UTF-8; invalid `%` sequences stay as they are. */
    private fun percentDecode(s: String): String {
      if ('%' !in s) return s
      val bytes = s.toByteArray(Charsets.UTF_8)
      val out = java.io.ByteArrayOutputStream(bytes.size)
      var i = 0
      while (i < bytes.size) {
        val b = bytes[i]
        if (b == '%'.code.toByte() && i + 2 < bytes.size) {
          val hi = Character.digit(bytes[i + 1].toInt().toChar(), 16)
          val lo = Character.digit(bytes[i + 2].toInt().toChar(), 16)
          if (hi >= 0 && lo >= 0) {
            out.write(hi * 16 + lo)
            i += 3
            continue
          }
        }
        out.write(b.toInt())
        i++
      }
      return out.toByteArray().toString(Charsets.UTF_8)
    }

    private fun parseHost(host: String): String {
      if (host.startsWith("[")) return host.lowercase()
      val ascii = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
      return ascii.lowercase()
    }

    private fun normalizePath(path: String): String {
      val segments = path.replace('\\', '/').split('/').drop(1)
      if (path.isEmpty()) return "/"
      val out = ArrayList<String>()
      segments.forEachIndexed { index, raw ->
        val last = index == segments.size - 1
        when (raw.lowercase()) {
          ".",
          "%2e" -> if (last) out.add("")
          "..",
          ".%2e",
          "%2e.",
          "%2e%2e" -> {
            if (out.isNotEmpty()) out.removeAt(out.size - 1)
            if (last) out.add("")
          }
          else -> out.add(encode(raw, ::inPathSet))
        }
      }
      return "/" + out.joinToString("/")
    }

    private fun encode(s: String, inSet: (Char) -> Boolean): String = buildString {
      for (ch in s) {
        if (ch.code < 0x80 && !inSet(ch)) {
          append(ch)
        } else if (ch.code < 0x80) {
          appendPercent(ch.code.toByte())
        } else {
          // Surrogate pairs are encoded together below.
          append(ch)
        }
      }
    }
      .let { encodeNonAscii(it) }

    private fun encodeNonAscii(s: String): String {
      if (s.all { it.code < 0x80 }) return s
      val sb = StringBuilder()
      var i = 0
      while (i < s.length) {
        val cp = s.codePointAt(i)
        val len = Character.charCount(cp)
        if (cp < 0x80) sb.append(s[i])
        else
          String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).forEach { sb.appendPercent(it) }
        i += len
      }
      return sb.toString()
    }

    private fun StringBuilder.appendPercent(b: Byte) {
      val v = b.toInt() and 0xFF
      append('%').append(HEX[v shr 4]).append(HEX[v and 0xF])
    }

    private const val HEX = "0123456789ABCDEF"

    private fun Char.isAsciiAlphanumeric() =
      this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun isSchemeChar(c: Char) = c.isAsciiAlphanumeric() || c == '+' || c == '-' || c == '.'

    private fun inC0Set(c: Char) = c.code < 0x20 || c.code == 0x7F

    private fun inFragmentSet(c: Char) = inC0Set(c) || c in " \"<>`"

    private fun inQuerySet(c: Char) = inC0Set(c) || c in " \"#<>"

    private fun inSpecialQuerySet(c: Char) = inQuerySet(c) || c == '\''

    private fun inPathSet(c: Char) = inQuerySet(c) || c in "?`{}"

    private fun inUserInfoSet(c: Char) = inPathSet(c) || c in "/:;=@[\\]|"
  }
}
