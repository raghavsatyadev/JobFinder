package io.github.raghavsatyadev.jobfinder.desktop.setup

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * What the setup assistant remembers between runs, one word per line in [file]: the user closed it
 * once ([seen]), opened the sign-in browser from it ([signedIn]), and the browser extension
 * connected once ([extensionLinked]). In memory only without a file.
 */
class SetupMemory(private val file: Path? = null) {
  private val flags: MutableSet<String> =
    file?.takeIf { it.exists() }?.readLines()?.map { it.trim() }?.toMutableSet() ?: mutableSetOf()

  var seen: Boolean
    get() = SEEN in flags
    set(value) = set(SEEN, value)

  var signedIn: Boolean
    get() = SIGNED_IN in flags
    set(value) = set(SIGNED_IN, value)

  var extensionLinked: Boolean
    get() = EXTENSION_LINKED in flags
    set(value) = set(EXTENSION_LINKED, value)

  @Synchronized
  private fun set(flag: String, value: Boolean) {
    if (if (value) !flags.add(flag) else !flags.remove(flag)) return
    file?.let {
      Files.createDirectories(it.parent)
      it.writeText(flags.sorted().joinToString("\n"))
    }
  }

  private companion object {
    const val SEEN = "seen"
    const val SIGNED_IN = "signed-in"
    const val EXTENSION_LINKED = "extension-linked"
  }
}
