package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.server.ai.AiHealth
import io.github.raghavsatyadev.jobfinder.shared.model.AiProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.AiProblems
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/** [AiProblems] from the in-process [AiHealth]; the mute is kept in [muteFile] across restarts. */
class DesktopAiProblems(
  health: AiHealth,
  private val muteFile: Path,
  private val clock: () -> Long = System::currentTimeMillis,
) : AiProblems {
  private val log = LoggerFactory.getLogger(DesktopAiProblems::class.java)
  private val _mutedUntil = MutableStateFlow(read())

  override val problems: StateFlow<List<AiProblem>> = health.problems
  override val mutedUntil: StateFlow<Long> = _mutedUntil.asStateFlow()

  override fun muteForDay() {
    val until = clock() + DAY_MS
    _mutedUntil.value = until
    try {
      Files.createDirectories(muteFile.parent)
      Files.writeString(muteFile, until.toString())
    } catch (e: Exception) {
      log.warn("Could not keep the AI warning mute: {}", e.message)
    }
  }

  private fun read(): Long =
    try {
      if (Files.exists(muteFile)) Files.readString(muteFile).trim().toLongOrNull() ?: 0 else 0
    } catch (e: Exception) {
      0
    }

  private companion object {
    const val DAY_MS = 24 * 60 * 60 * 1000L
  }
}
