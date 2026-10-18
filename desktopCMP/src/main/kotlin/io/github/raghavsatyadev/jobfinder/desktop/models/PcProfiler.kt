package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.desktop.setup.Shell
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads this PC's memory and graphics card through PowerShell. The card's memory comes from the
 * display driver's registry key (`HardwareInformation.qwMemorySize`), which, unlike WMI's
 * `AdapterRAM`, is not capped at 4 GB; the largest card counts.
 */
class PcProfiler(private val shell: Shell, private val modelsDir: () -> File = ::ollamaModelsDir) {
  suspend fun profile(): PcProfile {
    val result = runCatching { shell.run(SCRIPT) }.getOrNull()
    val parsed = result?.takeIf { it.ok }?.output?.let(::parse)
    val free = runCatching {
      modelsDir().let { it.takeIf(File::exists) ?: it.parentFile }
    }
      .getOrNull()
      ?.usableSpace
      ?.takeIf { it > 0 }
    return PcProfile(
      ramGb = parsed?.ramBytes?.let { it / GB },
      gpuName = parsed?.gpuName,
      vramGb = parsed?.vramBytes?.let { it / GB },
      freeDiskGb = free?.let { it / GB },
    )
  }

  class Parsed(val ramBytes: Long?, val gpuName: String?, val vramBytes: Long?)

  companion object {
    /** Binary gigabytes, as Windows shows them: a 64 GB PC reads 64, not 68. */
    private const val GB = 1_073_741_824.0

    val SCRIPT =
      """
      ${'$'}ram = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
      ${'$'}gpus = Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}\0*' -ErrorAction SilentlyContinue |
        Where-Object { ${'$'}_.'HardwareInformation.qwMemorySize' } |
        ForEach-Object { @{ name = ${'$'}_.DriverDesc; vram = [long]${'$'}_.'HardwareInformation.qwMemorySize' } }
      @{ ram = [long]${'$'}ram; gpus = @(${'$'}gpus) } | ConvertTo-Json -Compress -Depth 3
      """
        .trimIndent()

    fun parse(output: String): Parsed? = runCatching {
      // The JSON line; anything else PowerShell wrote around it is skipped.
      val line = output.lines().map(String::trim).last { it.startsWith("{") }
      val json = Json.parseToJsonElement(line) as JsonObject
      val gpus =
        (json["gpus"] as? JsonArray)
          .orEmpty()
          .mapNotNull { it as? JsonObject }
          .mapNotNull { gpu ->
            val vram = gpu["vram"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            gpu["name"]?.jsonPrimitive?.contentOrNull to vram
          }
      val best = gpus.maxByOrNull { it.second }
      Parsed(json["ram"]?.jsonPrimitive?.longOrNull, best?.first, best?.second)
    }
      .getOrNull()
  }
}

/** Where Ollama keeps models: `OLLAMA_MODELS`, or `~/.ollama/models`. */
fun ollamaModelsDir(): File =
  System.getenv("OLLAMA_MODELS")?.takeIf { it.isNotBlank() }?.let(::File)
    ?: File(System.getProperty("user.home"), ".ollama/models")
