package io.github.raghavsatyadev.jobfinder.server.parsing

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * HTML fragment to Markdown, the replacement for the old `new turndown({ bulletListMarker: '-',
 * codeBlockStyle: 'fenced' })`.
 *
 * flexmark is set up close to turndown's defaults: `-` bullets, setext `===`/`---` headings for h1
 * and h2, `* * *` rules, `**` strong, inline links, raw non-breaking spaces and no typographic
 * quote changes. The output is still not byte-for-byte the same: emphasis is `*em*` (turndown:
 * `_em_`), one space per list marker (turndown: three), a space between a closing `**` and
 * punctuation, fewer escapes and fewer blank lines. `MarkdownNormalizer` in the tests documents
 * what may differ.
 */
object HtmlToMarkdown {
  private val converter: FlexmarkHtmlConverter =
    FlexmarkHtmlConverter.builder(
        MutableDataSet().apply {
          set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-')
          set(FlexmarkHtmlConverter.SETEXT_HEADINGS, true)
          set(FlexmarkHtmlConverter.THEMATIC_BREAK, "* * *")
          set(FlexmarkHtmlConverter.NBSP_TEXT, " ")
          set(FlexmarkHtmlConverter.TYPOGRAPHIC_QUOTES, false)
          set(FlexmarkHtmlConverter.TYPOGRAPHIC_SMARTS, false)
          set(FlexmarkHtmlConverter.EXTRACT_AUTO_LINKS, false)
          set(FlexmarkHtmlConverter.WRAP_AUTO_LINKS, false)
          set(FlexmarkHtmlConverter.OUTPUT_UNKNOWN_TAGS, false)
          set(FlexmarkHtmlConverter.RENDER_COMMENTS, false)
          set(FlexmarkHtmlConverter.BR_AS_PARA_BREAKS, false)
          set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false)
          set(FlexmarkHtmlConverter.MAX_BLANK_LINES, 1)
          set(FlexmarkHtmlConverter.MAX_TRAILING_BLANK_LINES, 0)
          set(FlexmarkHtmlConverter.ADD_TRAILING_EOL, false)
        }
      )
      .build()

  private val EMPHASIS_OPEN =
    Regex("""(<(?:strong|b|em|i)(?:\s[^>]*)?>)(\s+)""", RegexOption.IGNORE_CASE)
  private val UNDERLINE_TAG = Regex("""</?u(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE)
  private val EMPHASIS_CLOSE = Regex("""(\s+)(</(?:strong|b|em|i)>)""", RegexOption.IGNORE_CASE)

  /**
   * Converts an HTML fragment (an element's `innerHTML`); the result is trimmed like turndown.
   *
   * Whitespace just inside an emphasis tag (`<strong>Position: </strong>Dev`) is moved outside
   * first, as turndown does; flexmark drops it and glues the words (`**Position:**Dev`). `<u>` is
   * dropped (turndown keeps only its text; flexmark would write `++text++`).
   */
  fun convert(html: String): String =
    converter
      .convert(
        EMPHASIS_CLOSE.replace(
          EMPHASIS_OPEN.replace(UNDERLINE_TAG.replace(html, ""), "$2$1"),
          "$2$1",
        )
      )
      .trim()
}
