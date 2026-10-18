package io.github.raghavsatyadev.jobfinder.desktop.nativehost

import java.awt.Desktop
import java.io.File
import org.slf4j.LoggerFactory

/**
 * The browser extension shipped inside the installer. It is copied to the data folder, so the
 * folder the user picks in "Load unpacked" stays the same across updates, and an update reaches the
 * browser the next time it starts.
 */
object BundledExtension {
  private val log = LoggerFactory.getLogger(BundledExtension::class.java)

  /** Brings the copy in [dataDir] up to date; returns it, or `null` when the app has no bundle. */
  fun sync(dataDir: File): File? {
    val source =
      System.getProperty(RESOURCES_PROPERTY)
        ?.let { File(it, DIR) }
        ?.takeIf { it.resolve(MANIFEST).isFile } ?: return null
    val target = dataDir.resolve(DIR)
    try {
      if (!sameFiles(source, target)) {
        target.deleteRecursively()
        source.copyRecursively(target, overwrite = true)
        log.info("Browser extension copied to {}", target)
      }
    } catch (e: Exception) {
      log.warn("The browser extension could not be copied", e)
    }
    return target.takeIf { it.resolve(MANIFEST).isFile }
  }

  /** Opens the extension's folder in the file manager, for "Load unpacked". */
  fun open(dataDir: File) {
    val folder = sync(dataDir) ?: return
    try {
      Desktop.getDesktop().open(folder)
    } catch (e: Exception) {
      log.warn("The extension folder could not be opened", e)
    }
  }

  private fun sameFiles(a: File, b: File): Boolean {
    val left = a.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(a).path }
    val right = b.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(b).path }
    return left.keys == right.keys &&
      left.all { (path, file) -> file.readBytes().contentEquals(right.getValue(path).readBytes()) }
  }

  private const val DIR = "browser-extension"
  private const val MANIFEST = "manifest.json"
  /** Set by Compose to the app's resources folder (`appResourcesRootDir`). */
  private const val RESOURCES_PROPERTY = "compose.application.resources.dir"
}
