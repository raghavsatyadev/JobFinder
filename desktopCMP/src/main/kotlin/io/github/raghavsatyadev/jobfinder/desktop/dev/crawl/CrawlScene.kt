@file:OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)

package io.github.raghavsatyadev.jobfinder.desktop.dev.crawl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.raghavsatyadev.jobfinder.App
import io.github.raghavsatyadev.jobfinder.desktop.dev.DemoData
import io.github.raghavsatyadev.jobfinder.desktop.dev.demoDesktopModule
import io.github.raghavsatyadev.jobfinder.desktop.dev.demoStoresModule
import io.github.raghavsatyadev.jobfinder.fakeRepositoriesModule
import io.github.raghavsatyadev.jobfinder.initKoin
import io.github.raghavsatyadev.jobfinder.shared.model.JobReportResult
import io.github.raghavsatyadev.jobfinder.shared.repository.JobReportRepository
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeSetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The whole app, off screen, on fresh sample data. Input goes in as real mouse presses and key
 * events, the way a window passes them on, so bugs on those paths (text selection, focus) show up.
 * Errors thrown while rendering or handling input, and errors that reach the uncaught-exception
 * handler, land in [errors].
 */
class CrawlScene(val width: Int, val height: Int, density: Float, phone: Boolean) : AutoCloseable {
  val errors: MutableList<Throwable> = java.util.Collections.synchronizedList(mutableListOf())
  /** Links the app tried to open; nothing leaves the process. */
  val openedLinks = mutableListOf<String>()
  private val navigation = MutableSharedFlow<TopLevel>(extraBufferCapacity = 4)
  private val owners = Owners()
  private val keySource = Canvas()
  private var nanos = 0L
  private val scene: ImageComposeScene
  private val data: List<Module>

  init {
    // Koin starts once per process: a restart loses the navigation entries. Each scene swaps in
    // fresh sample data instead, so no state leaks from the scene before.
    if (GlobalContext.getOrNull() == null) initKoin()
    val desktop = if (phone) emptyList() else listOf(demoDesktopModule(FakeSetupAssistant()))
    data =
      listOf(fakeRepositoriesModule(DemoData.backend()), demoStoresModule(), crawlModule) + desktop
    loadKoinModules(data)
    scene =
      ImageComposeScene(width, height, Density(density), Dispatchers.Main) {
        CompositionLocalProvider(
          LocalViewModelStoreOwner provides owners,
          LocalLifecycleOwner provides owners,
          LocalSystemTheme provides SystemTheme.Light,
          LocalUriHandler provides
            object : UriHandler {
              override fun openUri(uri: String) {
                openedLinks += uri
              }
            },
          LocalClipboardManager provides MemoryClipboard(),
        ) {
          App(navigation)
        }
      }
  }

  /** Lets the app settle: [count] frames, 50 ms of animation each, with the UI thread's work. */
  suspend fun frames(count: Int = 10) {
    repeat(count) {
      delay(8)
      guard { scene.render(nanos).close() }
      nanos += 50_000_000L
    }
  }

  suspend fun nav(to: TopLevel) {
    navigation.emit(to)
    frames(14)
  }

  /** A real mouse click: move, press, release. */
  suspend fun click(at: Offset) {
    guard { scene.sendPointerEvent(PointerEventType.Move, at) }
    frames(1)
    guard {
      scene.sendPointerEvent(
        PointerEventType.Press,
        at,
        buttons = PointerButtons(isPrimaryPressed = true),
        button = PointerButton.Primary,
      )
    }
    frames(1)
    guard { scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary) }
    frames(10)
  }

  suspend fun scroll(at: Offset, notches: Float) {
    guard { scene.sendPointerEvent(PointerEventType.Move, at) }
    repeat(5) {
      guard {
        scene.sendPointerEvent(PointerEventType.Scroll, at, scrollDelta = Offset(0f, notches / 5))
      }
      frames(1)
    }
    frames(6)
  }

  /** Types [text] as key presses into whatever has focus. */
  suspend fun type(text: String) {
    for (c in text) {
      val code = AwtKeyEvent.getExtendedKeyCodeForChar(c.code)
      key(KeyEventType.KeyDown, code, c.code)
      key(KeyEventType.KeyUp, code, c.code)
    }
    frames(4)
  }

  /** Presses and releases one key, such as Escape. */
  suspend fun press(code: Int) {
    key(KeyEventType.KeyDown, code, 0)
    key(KeyEventType.KeyUp, code, 0)
    frames(10)
  }

  private fun key(type: KeyEventType, code: Int, char: Int) {
    val awt =
      AwtKeyEvent(
        keySource,
        if (type == KeyEventType.KeyDown) AwtKeyEvent.KEY_PRESSED else AwtKeyEvent.KEY_RELEASED,
        System.currentTimeMillis(),
        0,
        code,
        if (char == 0) AwtKeyEvent.CHAR_UNDEFINED else char.toChar(),
        AwtKeyEvent.KEY_LOCATION_STANDARD,
      )
    val event = KeyEvent(Key(code), type, codePoint = char, nativeEvent = awt)
    guard { scene.sendKeyEvent(event) }
  }

  /** What can be done on screen now; see [SemanticsTargets]. */
  fun targets(): Screen =
    guard { SemanticsTargets.read(scene.semanticsOwners, width, height) } ?: Screen.EMPTY

  private var held: org.jetbrains.skia.Image? = null

  /** Keeps a picture of the screen as it is now, to save with [saveHeld] if the next step fails. */
  fun hold() {
    guard { scene.render(nanos) }
      ?.let { image ->
        held?.close()
        held = image
      }
  }

  fun saveHeld(file: File) {
    held?.let { file.writeBytes(it.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
  }

  fun snap(file: File) {
    guard {
      scene.render(nanos).use { file.writeBytes(it.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }
  }

  private inline fun <T> guard(block: () -> T): T? =
    try {
      block()
    } catch (e: Throwable) {
      errors += e
      null
    }

  override fun close() {
    held?.close()
    held = null
    runCatching { scene.close() }
    owners.viewModelStore.clear()
    unloadKoinModules(data)
  }

  private companion object {
    /** "Shouldn't be in New" answers at once, so the crawler reaches the dialog's every state. */
    val crawlModule = module {
      single<JobReportRepository> {
        object : JobReportRepository {
          override suspend fun reportJob(jobId: Long, note: String?) =
            JobReportResult(userRight = false, reason = "Crawler: it fits your filter.")
        }
      }
    }
  }
}

private class MemoryClipboard : ClipboardManager {
  private var text: AnnotatedString? = null

  override fun getText(): AnnotatedString? = text

  override fun setText(annotatedString: AnnotatedString) {
    text = annotatedString
  }
}

/** A resumed lifecycle and one ViewModel store for the whole scene. */
private class Owners : ViewModelStoreOwner, LifecycleOwner {
  override val viewModelStore = ViewModelStore()
  private val registry = LifecycleRegistry.createUnsafe(this)
  override val lifecycle: Lifecycle = registry

  init {
    registry.currentState = Lifecycle.State.RESUMED
  }
}
