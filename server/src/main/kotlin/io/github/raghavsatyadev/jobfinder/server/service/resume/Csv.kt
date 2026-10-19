package io.github.raghavsatyadev.jobfinder.server.service.resume

/**
 * A small RFC 4180 reader for the LinkedIn export: quoted fields with commas, doubled quotes and
 * line breaks, CRLF or LF, and a byte order mark. Rows are read by header name, so the export's
 * column order and extra columns do not matter.
 */
object Csv {
  /** Longest field kept; a LinkedIn summary is at most 2,600 characters. */
  private const val MAX_FIELD = 8_000

  /** Most rows read from one file. */
  private const val MAX_ROWS = 2_000

  /** All rows, the header first. */
  fun rows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = if (text.startsWith('﻿')) 1 else 0
    fun endField() {
      row += field.toString().take(MAX_FIELD)
      field.clear()
    }
    fun endRow() {
      endField()
      if (row.any { it.isNotBlank() }) rows += row.toList()
      row.clear()
    }
    while (i < text.length && rows.size < MAX_ROWS) {
      val c = text[i]
      when {
        quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
          field.append('"')
          i++
        }
        c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
        quoted -> field.append(c)
        c == ',' -> endField()
        c == '\r' && text.getOrNull(i + 1) == '\n' -> {
          endRow()
          i++
        }
        c == '\n' || c == '\r' -> endRow()
        else -> field.append(c)
      }
      i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
  }

  /**
   * The rows as maps from header to value. The header is the first row that has [anyOf] among its
   * fields; some exports put notes above it.
   */
  fun records(text: String, anyOf: Set<String>): List<Map<String, String>> {
    val rows = rows(text)
    val start = rows.indexOfFirst { row -> row.any { it.trim() in anyOf } }
    if (start < 0) return emptyList()
    val header = rows[start].map { it.trim() }
    return rows.drop(start + 1).map { row ->
      header.indices.associate { header[it] to (row.getOrNull(it)?.trim() ?: "") }
    }
  }
}
