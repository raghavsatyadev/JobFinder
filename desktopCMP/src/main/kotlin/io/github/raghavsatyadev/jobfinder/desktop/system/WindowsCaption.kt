package io.github.raghavsatyadev.jobfinder.desktop.system

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import java.util.concurrent.CopyOnWriteArraySet
import org.slf4j.LoggerFactory

/**
 * Lets the app draw its own title bar on Windows while Windows keeps everything else: the drop
 * shadow, rounded corners, resizing from every edge, dragging, Aero Snap, double-click to maximise
 * and the right-click window menu.
 *
 * It hooks the window's message handler. `WM_NCCALCSIZE` gives the caption strip to the app (the
 * side and bottom resize borders stay native). `WM_NCHITTEST` reports the app's title bar as the
 * caption, except for [buttonsWidthPx] at the right, where the app's own buttons sit. A maximised
 * window fills the screen exactly, so the top-right screen corner is on the app's close button.
 *
 * AWT draws the content in a child window that covers the whole client area, and Windows asks the
 * child first which part of the window the mouse is on. Each such child is hooked too and answers
 * `HTTRANSPARENT` wherever the frame has a non-client answer, so Windows asks the frame.
 */
class WindowsCaption private constructor(private val hwnd: WinDef.HWND) {
  /** Height of the app's title bar, in physical pixels. */
  @Volatile var captionHeightPx = 0

  /** Width of the app's window buttons at the right end of the title bar, in physical pixels. */
  @Volatile var buttonsWidthPx = 0

  private var previous: Pointer? = null

  // Kept in a field: JNA callbacks must stay reachable while Windows can call them.
  private val proc = WinUser.WindowProc { window, message, wParam, lParam ->
    handle(window, message, wParam, lParam)
  }

  private val children = CopyOnWriteArraySet<ChildHook>()

  private fun install(): Boolean {
    previous = Api.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, proc)
    if (previous == null) return false
    live += this
    hookChildren()
    // Makes Windows ask for the new client area at once.
    Api.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FRAME_ONLY)
    return true
  }

  private fun handle(
    window: WinDef.HWND,
    message: Int,
    wParam: WinDef.WPARAM,
    lParam: WinDef.LPARAM,
  ): WinDef.LRESULT =
    when (message) {
      WM_NCCALCSIZE ->
        if (wParam.toLong() != 0L) clientArea(window, wParam, lParam)
        else forward(window, message, wParam, lParam)
      WM_NCHITTEST -> hitTest(window, wParam, lParam)
      WM_NCDESTROY -> forward(window, message, wParam, lParam).also { live -= this }
      else -> forward(window, message, wParam, lParam)
    }

  /** Hooks the direct children AWT made on this window's thread (CEF's windows are not). */
  private fun hookChildren() {
    val thread = Api.INSTANCE.GetWindowThreadProcessId(hwnd, null)
    val found = mutableListOf<WinDef.HWND>()
    Api.INSTANCE.EnumChildWindows(
      hwnd,
      { child, _ ->
        if (
          Api.INSTANCE.GetParent(child) == hwnd &&
            Api.INSTANCE.GetWindowThreadProcessId(child, null) == thread
        ) {
          found += child
        }
        true
      },
      null,
    )
    found.forEach { child -> ChildHook(child).takeIf { it.install() }?.let(children::add) }
  }

  /** Sends the child's title-bar and resize points on to the frame. */
  private inner class ChildHook(private val child: WinDef.HWND) {
    private var previous: Pointer? = null

    private val proc = WinUser.WindowProc { window, message, wParam, lParam ->
      when (message) {
        WM_NCHITTEST ->
          if (hitTest(hwnd, wParam, lParam).toInt() != HTCLIENT) {
            WinDef.LRESULT(HTTRANSPARENT.toLong())
          } else {
            forward(window, message, wParam, lParam)
          }
        WM_NCDESTROY -> forward(window, message, wParam, lParam).also { children -= this }
        else -> forward(window, message, wParam, lParam)
      }
    }

    fun install(): Boolean {
      previous = Api.INSTANCE.SetWindowLongPtr(child, GWLP_WNDPROC, proc)
      return previous != null
    }

    private fun forward(
      window: WinDef.HWND,
      message: Int,
      wParam: WinDef.WPARAM,
      lParam: WinDef.LPARAM,
    ): WinDef.LRESULT = Api.INSTANCE.CallWindowProc(previous!!, window, message, wParam, lParam)
  }

  /** The default client area, but reaching up to the window's top edge. */
  private fun clientArea(
    window: WinDef.HWND,
    wParam: WinDef.WPARAM,
    lParam: WinDef.LPARAM,
  ): WinDef.LRESULT {
    // The first RECT of NCCALCSIZE_PARAMS: left, top, right, bottom.
    val rect = Pointer(lParam.toLong())
    val top = rect.getInt(4)
    val result = forward(window, WM_NCCALCSIZE, wParam, lParam)
    // A maximised window hangs its resize border off the screen; keep the content on screen.
    rect.setInt(4, if (Api.INSTANCE.IsZoomed(window)) top + frameHeight(window) else top)
    return result
  }

  private fun hitTest(
    window: WinDef.HWND,
    wParam: WinDef.WPARAM,
    lParam: WinDef.LPARAM,
  ): WinDef.LRESULT {
    val native = forward(window, WM_NCHITTEST, wParam, lParam)
    if (native.toInt() != HTCLIENT) return native
    val point =
      WinDef.POINT(
        lParam.toInt().toShort().toInt(),
        (lParam.toInt() shr Short.SIZE_BITS).toShort().toInt(),
      )
    Api.INSTANCE.ScreenToClient(window, point)
    val client = WinDef.RECT()
    Api.INSTANCE.GetClientRect(window, client)
    val maximized = Api.INSTANCE.IsZoomed(window)
    val code =
      when {
        !maximized && point.y < frameHeight(window) ->
          when {
            point.x < frameHeight(window) -> HTTOPLEFT
            point.x >= client.right - frameHeight(window) -> HTTOPRIGHT
            else -> HTTOP
          }
        point.y < captionHeightPx && point.x < client.right - buttonsWidthPx -> HTCAPTION
        else -> HTCLIENT
      }
    return WinDef.LRESULT(code.toLong())
  }

  private fun frameHeight(window: WinDef.HWND): Int {
    val dpi = Api.INSTANCE.GetDpiForWindow(window)
    return Api.INSTANCE.GetSystemMetricsForDpi(SM_CYFRAME, dpi) +
      Api.INSTANCE.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
  }

  private fun forward(
    window: WinDef.HWND,
    message: Int,
    wParam: WinDef.WPARAM,
    lParam: WinDef.LPARAM,
  ): WinDef.LRESULT = Api.INSTANCE.CallWindowProc(previous!!, window, message, wParam, lParam)

  @Suppress("FunctionName")
  private interface Api : StdCallLibrary {
    fun SetWindowLongPtr(window: WinDef.HWND, index: Int, proc: WinUser.WindowProc): Pointer?

    fun CallWindowProc(
      previous: Pointer,
      window: WinDef.HWND,
      message: Int,
      wParam: WinDef.WPARAM,
      lParam: WinDef.LPARAM,
    ): WinDef.LRESULT

    fun SetWindowPos(
      window: WinDef.HWND,
      after: WinDef.HWND?,
      x: Int,
      y: Int,
      width: Int,
      height: Int,
      flags: Int,
    ): Boolean

    fun IsZoomed(window: WinDef.HWND): Boolean

    fun EnumChildWindows(
      parent: WinDef.HWND,
      callback: WinUser.WNDENUMPROC,
      data: Pointer?,
    ): Boolean

    fun GetParent(window: WinDef.HWND): WinDef.HWND?

    fun GetWindowThreadProcessId(window: WinDef.HWND, process: Pointer?): Int

    fun ScreenToClient(window: WinDef.HWND, point: WinDef.POINT): Boolean

    fun GetClientRect(window: WinDef.HWND, rect: WinDef.RECT): Boolean

    fun GetDpiForWindow(window: WinDef.HWND): Int

    fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int

    companion object {
      val INSTANCE: Api = Native.load("user32", Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(WindowsCaption::class.java)

    /** Hooked windows; keeps each hook reachable until its window is destroyed. */
    private val live = CopyOnWriteArraySet<WindowsCaption>()

    /**
     * Hooks [window], which must be displayable. Returns `null` off Windows or when the hook fails;
     * the window then keeps the system title bar.
     */
    fun install(window: Window): WindowsCaption? {
      if (!Platform.isWindows() || !window.isDisplayable) return null
      return try {
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        WindowsCaption(hwnd).takeIf { it.install() }
      } catch (e: Throwable) {
        log.warn("Could not draw the app's own title bar", e)
        null
      }
    }

    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCDESTROY = 0x0082
    private const val HTTRANSPARENT = -1
    private const val HTCLIENT = 1
    private const val HTCAPTION = 2
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val SM_CYFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_FRAME_ONLY =
      SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED
  }
}
