package io.github.raghavsatyadev.jobfinder.desktop.dev.crawl

import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import java.awt.event.KeyEvent
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Goes through the whole app on sample data, off screen: every screen, every button, every text
 * field (clicked into, then typed in), every plain text (pressed), every list scrolled. Reports
 * each crash with the clicks that led to it and a screenshot of the screen just before.
 *
 * `gradlew :desktopCMP:crawlApp` (or `scripts/crawl-app.sh`). Arguments, all optional:
 * ```
 * out=tmp/crawl   report folder        layout=both   desktop, phone or both
 * depth=5         clicks deep          actions=1500  actions per layout
 * minutes=20      time per layout
 * ```
 *
 * Exit code 0: no crash. 1: crashes, see `<out>/report.md`.
 */
fun main(args: Array<String>) {
  val opts = args.associate { it.substringBefore('=') to it.substringAfter('=', "") }
  val out =
    File(opts["out"] ?: "tmp/crawl").apply {
      deleteRecursively()
      mkdirs()
    }
  val layouts =
    when (opts["layout"] ?: "both") {
      "desktop" -> listOf(Layout.DESKTOP)
      "phone" -> listOf(Layout.PHONE)
      else -> listOf(Layout.DESKTOP, Layout.PHONE)
    }
  val limits =
    Limits(
      depth = opts["depth"]?.toInt() ?: 5,
      actions = opts["actions"]?.toInt() ?: 1500,
      minutes = opts["minutes"]?.toInt() ?: 20,
    )
  val report = CrawlReport(out)
  val ok =
    try {
      runBlocking(Dispatchers.Main) {
        for (layout in layouts) Crawl(layout, limits, report).run()
      }
      report.write()
    } catch (e: Throwable) {
      e.printStackTrace()
      false
    }
  exitProcess(if (ok) 0 else 1)
}

enum class Layout(val width: Int, val height: Int, val density: Float, val phone: Boolean) {
  DESKTOP(1440, 900, 1f, false),
  PHONE(412, 915, 1f, true),
}

data class Limits(val depth: Int, val actions: Int, val minutes: Int)

/** One step from the app's start: a top-level destination, then targets by key. */
sealed interface Step {
  data class Nav(val to: TopLevel) : Step

  data class Act(val key: String) : Step
}

/** The text typed into every field: letters, digits, a symbol and a non-Latin letter. */
const val TYPED = "Crawl test 123 @é"

private class Crawl(val layout: Layout, val limits: Limits, val report: CrawlReport) {
  private val started = TimeSource.Monotonic.markNow()
  private var scene: CrawlScene? = null
  /** Where the scene is now; `null` when unknown (after a crash or a failed replay). */
  private var at: List<Step>? = null
  private val seenScreens = mutableSetOf<String>()
  private val tried = mutableSetOf<String>()
  private var actions = 0

  private fun outOfBudget() =
    actions >= limits.actions || started.elapsedNow() > limits.minutes.minutes

  suspend fun run() {
    println("== ${layout.name.lowercase()} ${layout.width}x${layout.height}")
    val queue = ArrayDeque<List<Step>>()
    TopLevel.entries.forEach { queue += listOf(Step.Nav(it)) }
    while (queue.isNotEmpty() && !outOfBudget()) {
      val path = queue.removeFirst()
      val screen = goTo(path) ?: continue
      if (!seenScreens.add(screen.fingerprint)) continue
      report.screen(layout, path, scene!!)
      for (target in screen.targets) {
        if (outOfBudget()) break
        // The same button on the same screen is tried once, whatever path led there.
        if (!tried.add(screen.fingerprint + "|" + target.key)) continue
        val current = goTo(path) ?: break
        if (current.fingerprint != screen.fingerprint) {
          report.drift(layout, path)
          break
        }
        val after = act(path, current, target) ?: continue
        val next = path + Step.Act(target.key)
        val moved = after.fingerprint != screen.fingerprint
        if (moved && next.size <= limits.depth + 1 && after.fingerprint !in seenScreens) {
          // A dialog or menu that opened is cheap to finish and where crashes hide: it goes first.
          if (after.layers > screen.layers) queue.addFirst(next) else queue += next
        }
        if (moved) back(path, screen)
      }
    }
    scene?.close()
    scene = null
    println(
      "== ${layout.name.lowercase()} done: $actions actions, ${seenScreens.size} screens, " +
        "${started.elapsedNow().inWholeSeconds} s"
    )
  }

  /** Does [target]; returns the screen after it, or `null` when the app crashed. */
  private suspend fun act(path: List<Step>, before: Screen, target: Target): Screen? {
    val s = scene!!
    actions++
    if (actions % 25 == 0) println("  $actions actions, ${seenScreens.size} screens")
    val shot = File(report.out, "last.png")
    s.hold()
    when (target.kind) {
      Kind.CLICK,
      Kind.TAP -> s.click(target.center)
      Kind.TYPE -> {
        s.click(target.center)
        s.type(TYPED)
      }
      Kind.SCROLL -> s.scroll(target.center, 5f)
    }
    if (s.errors.isEmpty())
      return s.targets().takeIf { s.errors.isEmpty() } ?: crashed(path, target, shot)
    return crashed(path, target, shot)
  }

  private fun crashed(path: List<Step>, target: Target, shot: File): Screen? {
    val s = scene!!
    s.saveHeld(shot)
    report.crash(layout, path, target, s.errors.toList(), shot)
    s.close()
    scene = null
    at = null
    return null
  }

  /** Back to [path]'s screen: Escape first (closes dialogs and menus), else start again. */
  private suspend fun back(path: List<Step>, screen: Screen) {
    val s = scene ?: return
    repeat(2) {
      s.press(KeyEvent.VK_ESCAPE)
      if (s.errors.isEmpty() && s.targets().fingerprint == screen.fingerprint) {
        at = path
        return
      }
    }
    at = null
  }

  /** The screen at [path], replayed from a fresh start when the scene is elsewhere. */
  private suspend fun goTo(path: List<Step>): Screen? {
    scene?.let { s -> if (at == path && s.errors.isEmpty()) return s.targets() }
    scene?.close()
    val s = CrawlScene(layout.width, layout.height, layout.density, layout.phone)
    scene = s
    Uncaught.current = s
    s.frames(20)
    for (step in path) {
      when (step) {
        is Step.Nav -> s.nav(step.to)
        is Step.Act -> {
          val target = s.targets().find(step.key)
          if (target == null) {
            report.drift(layout, path)
            at = null
            return null
          }
          when (target.kind) {
            Kind.CLICK,
            Kind.TAP -> s.click(target.center)
            Kind.TYPE -> {
              s.click(target.center)
              s.type(TYPED)
            }
            Kind.SCROLL -> s.scroll(target.center, 5f)
          }
        }
      }
      if (s.errors.isNotEmpty()) {
        report.crash(layout, path, null, s.errors.toList(), null)
        s.close()
        scene = null
        at = null
        return null
      }
    }
    at = path
    return s.targets()
  }
}

/** Errors on other threads, such as a coroutine that failed off the UI thread. */
object Uncaught {
  @Volatile var current: CrawlScene? = null

  init {
    Thread.setDefaultUncaughtExceptionHandler { _, e -> current?.errors?.add(e) }
  }
}
