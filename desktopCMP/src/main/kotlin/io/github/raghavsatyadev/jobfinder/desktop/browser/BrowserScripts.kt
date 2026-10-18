package io.github.raghavsatyadev.jobfinder.desktop.browser

/** JavaScript that the hidden browsers run, and the reply format of the JS bridge. */
object BrowserScripts {
  /** Name of the CefMessageRouter query function; not `cefQuery`, so pages can't spot CEF by it. */
  const val QUERY_FUNCTION = "__f2aQuery"

  /** Name of the CefMessageRouter cancel function. */
  const val CANCEL_FUNCTION = "__f2aQueryCancel"

  /**
   * Wraps the JS [expression] so its value (awaited when it is a promise, then `String()`-ed) comes
   * back through the message router as `"<id>\n<1|0>\n<value or error>"`. `null` and `undefined`
   * become an empty string.
   */
  fun evaluate(id: String, expression: String): String =
    """
    (function () {
      var send = function (ok, value) {
        window.$QUERY_FUNCTION({
          request: "$id\n" + (ok ? "1" : "0") + "\n" + value,
          persistent: false,
          onSuccess: function () {},
          onFailure: function () {}
        });
      };
      var fail = function (e) { send(false, String((e && e.stack) || e)); };
      try {
        Promise.resolve((function () { return ($expression); })()).then(function (v) {
          send(true, v === undefined || v === null ? "" : String(v));
        }, fail);
      } catch (e) {
        fail(e);
      }
    })();
    """
      .trimIndent()

  /** Parsed bridge reply; see [evaluate]. */
  data class Reply(val id: String, val ok: Boolean, val value: String)

  /** Parses a bridge request string, or returns `null` when it is not in the [evaluate] format. */
  fun parseReply(request: String): Reply? {
    val first = request.indexOf('\n')
    if (first <= 0 || request.length < first + 2) return null
    val flag = request[first + 1]
    if (flag != '1' && flag != '0') return null
    val valueStart = first + 2
    if (request.length > valueStart && request[valueStart] != '\n') return null
    val value = if (request.length > valueStart) request.substring(valueStart + 1) else ""
    return Reply(request.substring(0, first), flag == '1', value)
  }

  /**
   * Scrolls every scrollable element to its bottom once (ported from the old Electron
   * `HtmlDownloader`); infinite lists then load their next page. Returns how many elements it
   * scrolled.
   */
  const val SCROLL_ALL: String =
    """Array.from(document.querySelectorAll('*'))
      .filter(function (el) { return el.scrollHeight > el.clientHeight; })
      .map(function (el) { el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' }); return el; })
      .length"""

  /** Reads the rendered DOM. */
  const val OUTER_HTML = "document.documentElement.outerHTML"

  /**
   * Copies the React props of each LinkedIn search result into an `f2a-react-context` attribute,
   * where the LinkedIn parser reads the job ids (the old `getLinkedinReactContextBuilder`). Returns
   * how many cards it marked.
   */
  const val LINKEDIN_REACT_CONTEXT: String =
    """(function () {
      var stringify = function (obj) {
        var seen = new WeakSet();
        return JSON.stringify(obj, function (k, v) {
          if (v instanceof Window || v instanceof Element || v instanceof Document) return;
          if (typeof v === "bigint") return v.toString();
          if (v !== null && typeof v === "object") {
            if (seen.has(v)) return;
            seen.add(v);
          }
          return v;
        });
      };
      var cards = document.querySelectorAll(
        "div[componentkey='SearchResultsMainContent'] div[componentkey]");
      cards.forEach(function (el) {
        var context = Object.keys(el)
          .filter(function (k) { return k.indexOf("__reactProps") === 0; })
          .map(function (key) { return { key: key, value: stringify(el[key]) }; });
        el.setAttribute("f2a-react-context", JSON.stringify(context));
      });
      return cards.length;
    })()"""
}
