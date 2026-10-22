package io.github.raghavsatyadev.jobfinder.desktop.system

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Desktop
import java.awt.Frame
import java.awt.Window
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Smooth window state changes. Minimise, maximise and restore go through `WM_SYSCOMMAND`, like the
 * system's own caption buttons, so Windows plays its own animations for them. Windows does not
 * animate a window that comes back from the tray, so [show] fades it in, and [hide] fades it out to
 * match. A fade makes the window layered only while it runs; the window is opaque and not layered
 * at rest. With animations off in Windows, or off Windows, the changes are instant.
 *
 * Call [show] and [hide] on the AWT event thread.
 */
class WindowMotion(private val window: Window) {
  /** The window's opacity as last set by a fade, 0–255. */
  @Volatile private var alpha = OPAQUE

  /** Minimises the window, with the system animation on Windows. */
  fun minimize() {
    if (!sysCommand(SC_MINIMIZE)) (window as? Frame)?.extendedState = Frame.ICONIFIED
  }

  /** Maximises the window, or restores it when maximised, with the system animation on Windows. */
  fun toggleMaximize() {
    val frame = window as? Frame ?: return
    val maximized = frame.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
    if (!sysCommand(if (maximized) SC_RESTORE else SC_MAXIMIZE)) {
      frame.extendedState = if (maximized) Frame.NORMAL else Frame.MAXIMIZED_BOTH
    }
  }

  /**
   * Brings the window forward: fades it in when hidden, restores it with the system animation when
   * minimised. The window is on screen (transparent) before this first suspends.
   */
  suspend fun show() {
    val frame = window as? Frame
    if (window.isVisible && alpha == OPAQUE) {
      if (frame != null && frame.extendedState and Frame.ICONIFIED != 0) {
        if (!sysCommand(SC_RESTORE))
          frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
      }
      window.toFront()
      return
    }
    val hwnd = if (animate()) handle() else null
    if (hwnd == null) {
      window.isVisible = true
      activateMacApp()
      window.toFront()
      return
    }
    if (!window.isVisible) setAlpha(hwnd, 0)
    window.isVisible = true
    window.toFront()
    fade(hwnd, to = OPAQUE, durationMs = SHOW_MS, easing = EmphasizedDecelerate)
  }

  /** Fades the window out, then hides it. */
  suspend fun hide() {
    val hwnd = if (window.isVisible && animate()) handle() else null
    if (hwnd != null) fade(hwnd, to = 0, durationMs = HIDE_MS, easing = StandardAccelerate)
    window.isVisible = false
    // Opaque again while hidden, so a show by any other path never shows an invisible window.
    hwnd?.let { setAlpha(it, OPAQUE) }
  }

  private suspend fun fade(hwnd: WinDef.HWND, to: Int, durationMs: Long, easing: Easing) {
    val from = alpha
    withContext(Dispatchers.IO) {
      val start = System.nanoTime()
      while (true) {
        coroutineContext.ensureActive()
        val t = ((System.nanoTime() - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
        setAlpha(hwnd, (from + (to - from) * easing.transform(t)).toInt())
        if (t >= 1f) break
        // Waits for the next composed frame, so each step lands on its own frame.
        if (Dwm.INSTANCE.DwmFlush() != 0) Thread.sleep(FALLBACK_FRAME_MS)
      }
    }
  }

  /** Sets the opacity; fully opaque also drops the layered style, so drawing runs at full speed. */
  private fun setAlpha(hwnd: WinDef.HWND, value: Int) {
    val api = Api.INSTANCE
    val style = api.GetWindowLong(hwnd, GWL_EXSTYLE)
    if (value >= OPAQUE) {
      if (style and WS_EX_LAYERED != 0) {
        api.SetLayeredWindowAttributes(hwnd, 0, OPAQUE.toByte(), LWA_ALPHA)
        api.SetWindowLong(hwnd, GWL_EXSTYLE, style and WS_EX_LAYERED.inv())
      }
    } else {
      if (style and WS_EX_LAYERED == 0) api.SetWindowLong(hwnd, GWL_EXSTYLE, style or WS_EX_LAYERED)
      api.SetLayeredWindowAttributes(hwnd, 0, value.coerceIn(0, OPAQUE).toByte(), LWA_ALPHA)
    }
    alpha = value.coerceIn(0, OPAQUE)
  }

  private fun sysCommand(command: Int): Boolean {
    val hwnd = handle() ?: return false
    return Api.INSTANCE.PostMessage(hwnd, WM_SYSCOMMAND, WinDef.WPARAM(command.toLong()), null)
  }

  /**
   * On macOS, makes the app the active one: `toFront` alone leaves a window opened from the menu
   * bar behind the app the user is in.
   */
  private fun activateMacApp() {
    if (!Platform.isMac()) return
    runCatching {
      val desktop = Desktop.getDesktop()
      if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND))
        desktop.requestForeground(true)
    }
  }

  /** The native window, created first when the window has never been shown. */
  private fun handle(): WinDef.HWND? {
    if (!Platform.isWindows()) return null
    return try {
      if (!window.isDisplayable) window.addNotify()
      WinDef.HWND(Native.getWindowPointer(window))
    } catch (e: Throwable) {
      log.warn("No native window for window motion", e)
      null
    }
  }

  /** Whether Windows' "Animation effects" setting is on. */
  private fun animate(): Boolean {
    if (!Platform.isWindows()) return false
    val on = IntByReference(1)
    return !Api.INSTANCE.SystemParametersInfo(SPI_GETCLIENTAREAANIMATION, 0, on, 0) || on.value != 0
  }

  @Suppress("FunctionName")
  private interface Api : StdCallLibrary {
    fun GetWindowLong(window: WinDef.HWND, index: Int): Int

    fun SetWindowLong(window: WinDef.HWND, index: Int, value: Int): Int

    fun SetLayeredWindowAttributes(window: WinDef.HWND, key: Int, alpha: Byte, flags: Int): Boolean

    fun PostMessage(
      window: WinDef.HWND,
      message: Int,
      wParam: WinDef.WPARAM,
      lParam: WinDef.LPARAM?,
    ): Boolean

    fun SystemParametersInfo(action: Int, param: Int, value: IntByReference, flags: Int): Boolean

    companion object {
      val INSTANCE: Api = Native.load("user32", Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
  }

  @Suppress("FunctionName")
  private interface Dwm : StdCallLibrary {
    fun DwmFlush(): Int

    companion object {
      val INSTANCE: Dwm = Native.load("dwmapi", Dwm::class.java)
    }
  }

  private companion object {
    val log = LoggerFactory.getLogger(WindowMotion::class.java)

    /** Material 3 curves: enter decelerates (emphasized), exit accelerates (standard). */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val SHOW_MS = 220L
    const val HIDE_MS = 150L
    const val FALLBACK_FRAME_MS = 8L
    const val OPAQUE = 255

    const val GWL_EXSTYLE = -20
    const val WS_EX_LAYERED = 0x00080000
    const val LWA_ALPHA = 0x2
    const val WM_SYSCOMMAND = 0x0112
    const val SC_MINIMIZE = 0xF020
    const val SC_MAXIMIZE = 0xF030
    const val SC_RESTORE = 0xF120
    const val SPI_GETCLIENTAREAANIMATION = 0x1042
  }
}
