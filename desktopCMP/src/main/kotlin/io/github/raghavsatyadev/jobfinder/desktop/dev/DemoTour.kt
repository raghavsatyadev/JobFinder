@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package io.github.raghavsatyadev.jobfinder.desktop.dev

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.raghavsatyadev.jobfinder.App
import io.github.raghavsatyadev.jobfinder.fakeRepositoriesModule
import io.github.raghavsatyadev.jobfinder.initKoin
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeSetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Surface

/**
 * Records the app on the sample data, off screen, for the README: no window, no screen capture,
 * none of the user's data. Runs a tour script and pipes the frames to `ffmpeg`; `snap` steps save
 * PNG screenshots. One step per line, `#` starts a comment:
 * ```
 * size 1680 1050 1.3125     window pixels and density (first line); add "phone" for the phone app
 * theme dark                light or dark (default light)
 * nav Links                 a top-level destination (TopLevel name)
 * wait 1.5                  seconds of frames
 * move 400 300 0.6          glide the pointer to x y over seconds
 * click 400 300             glide there, then click
 * scroll 400 300 5 1.2      wheel 5 notches down (negative: up) over seconds
 * snap jobs                 save <out>/jobs.png
 * setup show                open (or hide) the setup assistant (desktop)
 * ```
 *
 * `gradlew :desktopCMP:demoTour -PtourScript=<file> -PtourOut=<dir>` writes `<out>/tour.mp4`.
 */
fun main(args: Array<String>) {
  val script = File(args.getOrNull(0) ?: error("Usage: DemoTour <script> <out dir>"))
  val out =
    File(args.getOrNull(1) ?: error("Usage: DemoTour <script> <out dir>")).apply { mkdirs() }
  val steps = script.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
  val size = steps.first().split(Regex("\\s+"))
  require(size[0] == "size") { "The first step must be size" }
  // All on the UI thread, which the screens' lifecycle expects.
  runBlocking(Dispatchers.Main) {
    val tour =
      Tour(size[1].toInt(), size[2].toInt(), size[3].toFloat(), out, size.getOrNull(4) == "phone")
    try {
      tour.run(steps.drop(1))
    } finally {
      tour.close()
    }
  }
  exitProcess(0)
}

private class Tour(
  val width: Int,
  val height: Int,
  density: Float,
  val out: File,
  phone: Boolean,
) {
  private val backend = DemoData.backend()
  private val setup = FakeSetupAssistant()
  private val navigation = MutableSharedFlow<TopLevel>(extraBufferCapacity = 4)
  private val owners = Owners()
  private val dark = mutableStateOf(false)
  private val scene: ImageComposeScene
  private val ffmpeg: Process
  private var frame = 0L
  private var pointer = Offset(width / 2f, height / 2f)

  init {
    val desktop = if (phone) emptyList() else listOf(demoDesktopModule(setup))
    initKoin(extraModules = listOf(fakeRepositoriesModule(backend), demoStoresModule()) + desktop)
    scene =
      // Effects go through the UI thread's queue. With the default (Unconfined) a string resource
      // that starts loading inside an effect waits in the thread's own queue, and the render that
      // waits for it never returns.
      ImageComposeScene(width, height, Density(density), Dispatchers.Main) {
        CompositionLocalProvider(
          LocalViewModelStoreOwner provides owners,
          LocalLifecycleOwner provides owners,
          LocalSystemTheme provides if (dark.value) SystemTheme.Dark else SystemTheme.Light,
        ) {
          App(navigation)
        }
      }
    ffmpeg =
      ProcessBuilder(
          "ffmpeg",
          "-v",
          "error",
          "-y",
          "-f",
          "rawvideo",
          "-pix_fmt",
          "bgra",
          "-s",
          "${width}x$height",
          "-r",
          "$FPS",
          "-i",
          "-",
          "-c:v",
          "libx264",
          "-crf",
          "24",
          "-preset",
          "slow",
          "-pix_fmt",
          "yuv420p",
          "-movflags",
          "+faststart",
          File(out, "tour.mp4").path,
        )
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .start()
  }

  suspend fun run(steps: List<String>) {
    for (line in steps) {
      val a = line.split(Regex("\\s+"))
      println("> $line")
      when (a[0]) {
        "theme" -> dark.value = a[1] == "dark"
        "nav" -> navigation.emit(TopLevel.valueOf(a[1]))
        "wait" -> frames(a[1].toDouble())
        "move" -> glide(Offset(a[1].toFloat(), a[2].toFloat()), a.getOrNull(3)?.toDouble() ?: 0.5)
        "click" -> {
          glide(Offset(a[1].toFloat(), a[2].toFloat()), a.getOrNull(3)?.toDouble() ?: 0.5)
          send(PointerEventType.Press, button = PointerButton.Primary)
          frames(0.08, pressed = true)
          send(PointerEventType.Release, button = PointerButton.Primary)
          frames(0.1)
        }
        "scroll" -> {
          glide(Offset(a[1].toFloat(), a[2].toFloat()), 0.3)
          val notches = a[3].toFloat()
          val total = (a.getOrNull(4)?.toDouble() ?: 1.0) * FPS
          repeat(total.toInt()) {
            scene.sendPointerEvent(
              PointerEventType.Scroll,
              pointer,
              scrollDelta = Offset(0f, notches / total.toFloat()),
            )
            frames(1.0 / FPS)
          }
        }
        "snap" -> snap(a[1])
        "setup" -> setup.show(a[1] == "show")
        else -> error("Unknown step: $line")
      }
    }
  }

  private suspend fun glide(to: Offset, seconds: Double) {
    val from = pointer
    val count = (seconds * FPS).toInt().coerceAtLeast(1)
    for (i in 1..count) {
      val t = i.toFloat() / count
      val eased = t * t * (3 - 2 * t)
      pointer = from + (to - from) * eased
      send(PointerEventType.Move)
      frames(1.0 / FPS)
    }
  }

  private fun send(type: PointerEventType, button: PointerButton? = null) {
    scene.sendPointerEvent(
      type,
      pointer,
      buttons =
        if (type == PointerEventType.Press) PointerButtons(isPrimaryPressed = true) else null,
      button = button,
    )
  }

  /** Renders [seconds] of frames; other work on the UI thread runs between them. */
  private suspend fun frames(seconds: Double, pressed: Boolean = false) {
    repeat((seconds * FPS).toInt().coerceAtLeast(1)) {
      delay(4)
      val image = scene.render(frame * 1_000_000_000L / FPS)
      frame++
      ffmpeg.outputStream.write(pixels(withPointer(image, pressed)))
    }
  }

  private fun snap(name: String) {
    val image = scene.render(frame * 1_000_000_000L / FPS)
    File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
  }

  /** The frame with a mouse pointer drawn where the pointer is. */
  private fun withPointer(image: Image, pressed: Boolean): Image {
    val surface = Surface.makeRasterN32Premul(width, height)
    val canvas = surface.canvas
    canvas.drawImage(image, 0f, 0f)
    val r = if (pressed) 11f else 9f
    canvas.drawCircle(
      pointer.x,
      pointer.y,
      r,
      Paint().apply { color = Color.makeARGB(90, 40, 40, 40) },
    )
    canvas.drawCircle(
      pointer.x,
      pointer.y,
      r,
      Paint().apply {
        color = Color.WHITE
        mode = PaintMode.STROKE
        strokeWidth = 2f
      },
    )
    return surface.makeImageSnapshot()
  }

  private fun pixels(image: Image): ByteArray {
    val bitmap = Bitmap.makeFromImage(image)
    return bitmap.readPixels() ?: error("No pixels")
  }

  fun close() {
    scene.close()
    ffmpeg.outputStream.close()
    ffmpeg.waitFor()
  }

  companion object {
    const val FPS = 30
  }
}

/** A resumed lifecycle and one ViewModel store for the whole tour. */
private class Owners : ViewModelStoreOwner, LifecycleOwner {
  override val viewModelStore = ViewModelStore()
  private val registry = LifecycleRegistry.createUnsafe(this)
  override val lifecycle: Lifecycle = registry

  init {
    registry.currentState = Lifecycle.State.RESUMED
  }
}
