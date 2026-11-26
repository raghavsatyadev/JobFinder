package io.github.raghavsatyadev.jobfinder.desktop.dev.crawl

import java.io.File

/**
 * The crawl's findings: each distinct crash once (by message and top app frame), with the path that
 * led to it, the stack trace and a screenshot from just before. Written to `<out>/report.md`.
 */
class CrawlReport(val out: File) {
  private val screenPaths = mutableListOf<String>()
  val screens: Int
    get() = screenPaths.size

  private val crashes = linkedMapOf<String, Crash>()
  private var drifts = 0

  private class Crash(
    val layout: Layout,
    val path: List<Step>,
    val target: Target?,
    val error: Throwable,
    val shot: String?,
    var times: Int = 1,
  )

  fun crash(
    layout: Layout,
    path: List<Step>,
    target: Target?,
    errors: List<Throwable>,
    shot: File?,
  ) {
    val error = errors.first()
    val id = signature(error)
    crashes[id]?.let {
      it.times++
      return
    }
    val n = crashes.size + 1
    val kept =
      shot
        ?.takeIf { it.exists() }
        ?.let { s -> File(out, "crash-$n.png").also { s.copyTo(it, true) }.name }
    crashes[id] = Crash(layout, path, target, error, kept)
    println(
      "!! CRASH $n (${layout.name.lowercase()}): ${error::class.simpleName}: ${error.message}"
    )
    println("   path: ${describe(path, target)}")
  }

  /** A screen seen for the first time, with its picture as `screen-N.png`. */
  fun screen(layout: Layout, path: List<Step>, scene: CrawlScene) {
    val name = "screen-${screenPaths.size + 1}-${layout.name.lowercase()}.png"
    scene.snap(File(out, name))
    screenPaths += "$name: ${describe(path, null)}"
  }

  /** A replay no longer found a target: the sample data or the screen changed on the way. */
  fun drift(layout: Layout, path: List<Step>) {
    drifts++
  }

  /** Writes the report; `true` when nothing crashed. */
  fun write(): Boolean {
    File(out, "last.png").delete()
    val text = buildString {
      appendLine("# App crawl")
      appendLine()
      appendLine(
        "Screens: $screens · distinct crashes: ${crashes.size} · replays that drifted: $drifts"
      )
      appendLine()
      appendLine("## Screens")
      appendLine()
      screenPaths.forEach { appendLine("- $it") }
      crashes.values.forEachIndexed { i, c ->
        appendLine()
        appendLine("## Crash ${i + 1} (${c.layout.name.lowercase()}, seen ${c.times}×)")
        appendLine()
        appendLine("Path: ${describe(c.path, c.target)}")
        c.shot?.let { appendLine("Screen before: $it") }
        appendLine()
        appendLine("```")
        appendLine(c.error.stackTraceToString().lines().take(40).joinToString("\n"))
        appendLine("```")
      }
    }
    File(out, "report.md").writeText(text)
    println(text.lineSequence().take(3).joinToString("\n"))
    println("Report: ${File(out, "report.md").absolutePath}")
    return crashes.isEmpty()
  }

  private fun describe(path: List<Step>, target: Target?): String =
    (path.map {
        when (it) {
          is Step.Nav -> "nav ${it.to}"
          is Step.Act -> it.key
        }
      } + listOfNotNull(target?.key))
      .joinToString(" → ")

  private fun signature(e: Throwable): String {
    var root = e
    while (root.cause != null && root.cause !== root) root = root.cause!!
    val frame =
      root.stackTrace.firstOrNull { it.className.startsWith("io.github.raghavsatyadev") }
        ?: root.stackTrace.firstOrNull()
    return "${root::class.qualifiedName}: ${root.message?.take(80)} @ $frame"
  }
}
