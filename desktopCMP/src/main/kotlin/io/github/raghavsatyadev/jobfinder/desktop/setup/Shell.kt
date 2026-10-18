package io.github.raghavsatyadev.jobfinder.desktop.setup

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a script printed, and how it ended. */
data class ShellResult(val exitCode: Int, val output: String) {
  val ok: Boolean
    get() = exitCode == 0
}

/** How an elevated script ended. */
enum class Elevated {
  OK,
  /** The user said no to Windows' permission prompt. */
  DECLINED,
  FAILED,
}

/** Runs PowerShell scripts. */
interface Shell {
  suspend fun run(script: String): ShellResult

  /** Runs [script] as administrator: Windows asks the user once. */
  suspend fun runElevated(script: String): Elevated
}

/**
 * Windows PowerShell, with the script passed encoded so no quoting reaches the command line. The
 * elevated run starts a second, hidden PowerShell through `Start-Process -Verb RunAs`.
 */
class PowerShell(private val timeoutSeconds: Long = 120) : Shell {
  override suspend fun run(script: String): ShellResult =
    withContext(Dispatchers.IO) {
      val process =
        ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-EncodedCommand",
            encode(QUIET + script),
          )
          .redirectErrorStream(true)
          .start()
      process.outputStream.close()
      val output = process.inputStream.bufferedReader().readText()
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@withContext ShellResult(-1, output)
      }
      ShellResult(process.exitValue(), output.trim())
    }

  override suspend fun runElevated(script: String): Elevated {
    val result = run(elevate(script))
    return when (result.exitCode) {
      0 -> Elevated.OK
      CANCELLED -> Elevated.DECLINED
      else -> Elevated.FAILED
    }
  }

  companion object {
    /**
     * Without it, cmdlets such as `Get-CimInstance` write progress records (`#< CLIXML`) into the
     * output, after what the script printed.
     */
    private const val QUIET = "\$ProgressPreference = 'SilentlyContinue'\n"

    /** `ERROR_CANCELLED`: the permission prompt was answered with No. */
    const val CANCELLED = 1223

    /** PowerShell's `-EncodedCommand`: UTF-16LE in base64. */
    fun encode(script: String): String =
      Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))

    /** A script that runs [script] elevated and exits with its exit code, or [CANCELLED]. */
    fun elevate(script: String): String =
      """
      try {
        ${'$'}p = Start-Process -FilePath powershell.exe -Verb RunAs -WindowStyle Hidden -Wait -PassThru -ArgumentList '-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-EncodedCommand','${encode(script)}'
        exit ${'$'}p.ExitCode
      } catch {
        exit $CANCELLED
      }
      """
        .trimIndent()
  }
}

/** [text] inside single quotes for PowerShell. */
fun psQuote(text: String): String = "'" + text.replace("'", "''") + "'"
