package io.github.raghavsatyadev.jobfinder.server.http

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/**
 * Whether a phone works with this computer: [paired] turns `true` on the first request with the
 * pairing token and stays so (in [file]) until the token is replaced. The setup assistant's last
 * step follows it.
 */
class PairedPhones(private val file: Path? = null) {
  private val log = LoggerFactory.getLogger(PairedPhones::class.java)
  private val _paired = MutableStateFlow(file?.exists() == true)

  val paired: StateFlow<Boolean> = _paired.asStateFlow()

  /** A request came with the right token. */
  fun seen() {
    if (_paired.value) return
    _paired.value = true
    write { f ->
      Files.createDirectories(f.parent)
      f.writeText("paired")
    }
  }

  /** The token was replaced: every phone has to pair again. */
  fun forget() {
    _paired.value = false
    write { it.deleteIfExists() }
  }

  private fun write(block: (Path) -> Unit) {
    val f = file ?: return
    try {
      block(f)
    } catch (e: Exception) {
      log.warn("Could not update {}", f, e)
    }
  }
}
