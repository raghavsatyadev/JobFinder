package io.github.raghavsatyadev.jobfinder.desktop.system

import java.awt.Desktop
import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon as AwtTrayIcon
import java.awt.desktop.AppReopenedListener
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * The app's icon in the macOS menu bar, with [menu] as its menu. A click on the Dock icon also
 * calls [onOpen], so a window closed to the menu bar comes back from either place. Notifications go
 * through Notification Center (`osascript`), which shows them under "Script Editor"; a click on one
 * does not open the app.
 */
internal class MacTrayIcon(
  private val tooltip: String,
  private val menu: List<TrayMenuItem>,
  private val onOpen: () -> Unit,
) : TrayIcon {
  private val log = LoggerFactory.getLogger(MacTrayIcon::class.java)
  @Volatile private var icon: AwtTrayIcon? = null

  override val available: Boolean
    get() = icon != null

  fun start() {
    // AWT builds menu bar items on its own thread; waiting keeps `available` right at start.
    runCatching { EventQueue.invokeAndWait(::add) }
      .onFailure { log.error("Could not create the menu bar icon", it) }
    runCatching {
      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().addAppEventListener(AppReopenedListener { open() })
      }
    }
      .onFailure { log.warn("Could not listen for Dock clicks", it) }
  }

  private fun add() {
    if (!SystemTray.isSupported()) return log.warn("No menu bar icons on this system")
    val image =
      MacTrayIcon::class.java.getResource(ICON_RESOURCE)?.let {
        Toolkit.getDefaultToolkit().getImage(it)
      } ?: return log.warn("Menu bar icon image missing")
    val popup = PopupMenu()
    menu.forEach { item ->
      popup.add(MenuItem(item.label).apply { addActionListener { run(item.label, item.action) } })
    }
    val trayIcon =
      AwtTrayIcon(image, tooltip, popup).apply {
        isImageAutoSize = true
        addActionListener { open() }
      }
    SystemTray.getSystemTray().add(trayIcon)
    icon = trayIcon
  }

  override fun notify(title: String, text: String, sound: Boolean) {
    val script = buildString {
      append("display notification ").append(quote(text))
      append(" with title ").append(quote(title))
      if (sound) append(" sound name \"default\"")
    }
    Thread(
        {
          runCatching {
            val process =
              ProcessBuilder(OSASCRIPT, "-e", script)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(NOTIFY_TIMEOUT_S, TimeUnit.SECONDS)) process.destroyForcibly()
          }
            .onFailure { log.warn("Notification '{}' failed", title, it) }
        },
        "notification",
      )
      .apply { isDaemon = true }
      .start()
  }

  override fun close() {
    val current = icon ?: return
    icon = null
    runCatching { EventQueue.invokeLater { SystemTray.getSystemTray().remove(current) } }
  }

  private fun open() = run("Opening the app from the menu bar", onOpen)

  private fun run(what: String, action: () -> Unit) =
    try {
      action()
    } catch (e: Exception) {
      log.error("{} failed", what, e)
    }

  private companion object {
    const val ICON_RESOURCE = "/tray-icon.png"
    const val OSASCRIPT = "/usr/bin/osascript"
    const val NOTIFY_TIMEOUT_S = 10L

    /** An AppleScript string literal for [text]. */
    fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }
}
