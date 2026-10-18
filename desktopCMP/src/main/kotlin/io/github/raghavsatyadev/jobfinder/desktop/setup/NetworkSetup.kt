package io.github.raghavsatyadev.jobfinder.desktop.setup

/** What Windows calls the network the API server is on. */
enum class NetworkCategory {
  PRIVATE,
  PUBLIC,
  DOMAIN,
}

/** Whether phones get through the firewall. */
enum class FirewallState {
  OPEN,
  NO_RULE,
  /** An inbound Block rule for this app wins over any allow rule. */
  BLOCKED,
}

/** The network checks and their fix, as the setup assistant uses them. */
interface WindowsNetwork {
  /** The category of the network holding [ip], or `null` when Windows does not say. */
  suspend fun category(ip: String): NetworkCategory?

  /** Whether phones reach [port], or `null` when the check failed. */
  suspend fun firewall(port: Int): FirewallState?

  /**
   * Makes the network holding [ip] Private when [makePrivate], and opens the firewall for [port]
   * and mDNS to the local subnet when [openFirewall]. One permission prompt.
   */
  suspend fun fix(ip: String, port: Int, makePrivate: Boolean, openFirewall: Boolean): Elevated
}

/**
 * The Windows side of reaching the API server from a phone: the network profile and the firewall.
 * Checks need no permission; [fix] makes every change in one elevated script.
 */
class NetworkSetup(private val shell: Shell, private val program: String? = currentProgram()) :
  WindowsNetwork {
  override suspend fun category(ip: String): NetworkCategory? {
    val result =
      shell.run(
        "(Get-NetConnectionProfile -InterfaceIndex " +
          "(Get-NetIPAddress -IPAddress ${psQuote(ip)} -ErrorAction Stop).InterfaceIndex " +
          "-ErrorAction Stop).NetworkCategory"
      )
    if (!result.ok) return null
    return parseCategory(result.output)
  }

  override suspend fun firewall(port: Int): FirewallState? {
    val result = shell.run(firewallCheck(port, program))
    if (!result.ok) return null
    return parseFirewall(result.output)
  }

  override suspend fun fix(
    ip: String,
    port: Int,
    makePrivate: Boolean,
    openFirewall: Boolean,
  ): Elevated = shell.runElevated(fixScript(ip, port, program, makePrivate, openFirewall))

  companion object {
    const val API_RULE = "JobFinder-API"
    const val MDNS_RULE = "JobFinder-mDNS"
    private const val MDNS_PORT = 5353

    /** The running executable: the packaged `JobFinder.exe`, or `java.exe` in dev runs. */
    fun currentProgram(): String? = ProcessHandle.current().info().command().orElse(null)

    fun parseCategory(output: String): NetworkCategory? =
      when (output.lines().lastOrNull { it.isNotBlank() }?.trim()) {
        "Private" -> NetworkCategory.PRIVATE
        "Public" -> NetworkCategory.PUBLIC
        "DomainAuthenticated" -> NetworkCategory.DOMAIN
        else -> null
      }

    /** Reads the `allow=` and `blocked=` lines of [firewallCheck]. */
    fun parseFirewall(output: String): FirewallState? {
      val values =
        output
          .lines()
          .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
          .associate { (k, v) -> k.trim() to v.trim() }
      val blocked = values["blocked"]?.toIntOrNull() ?: return null
      val allowed = values["allow"]?.equals("True", ignoreCase = true) ?: return null
      return when {
        blocked > 0 -> FirewallState.BLOCKED
        allowed -> FirewallState.OPEN
        else -> FirewallState.NO_RULE
      }
    }

    fun firewallCheck(port: Int, program: String?): String {
      val blocked =
        if (program == null) "0"
        else
          "@(Get-NetFirewallApplicationFilter -Program ${psQuote(program)} " +
            "-ErrorAction SilentlyContinue | Get-NetFirewallRule | Where-Object { " +
            "\$_.Enabled -eq 'True' -and \$_.Action -eq 'Block' -and " +
            "\$_.Direction -eq 'Inbound' }).Count"
      return """
        ${'$'}rule = Get-NetFirewallRule -Name ${psQuote(API_RULE)} -ErrorAction SilentlyContinue | Where-Object { ${'$'}_.Enabled -eq 'True' -and ${'$'}_.Action -eq 'Allow' }
        ${'$'}ports = if (${'$'}rule) { @((${'$'}rule | Get-NetFirewallPortFilter).LocalPort) } else { @() }
        "allow=" + (${'$'}ports -contains '$port')
        "blocked=" + ($blocked)
        """
        .trimIndent()
    }

    fun fixScript(
      ip: String,
      port: Int,
      program: String?,
      makePrivate: Boolean,
      openFirewall: Boolean,
    ): String = buildString {
      appendLine("\$ErrorActionPreference = 'Stop'")
      if (makePrivate) {
        appendLine(
          "Set-NetConnectionProfile -InterfaceIndex " +
            "(Get-NetIPAddress -IPAddress ${psQuote(ip)}).InterfaceIndex -NetworkCategory Private"
        )
      }
      if (openFirewall) {
        appendLine(
          "Remove-NetFirewallRule -Name ${psQuote(API_RULE)},${psQuote(MDNS_RULE)} " +
            "-ErrorAction SilentlyContinue"
        )
        appendLine(
          "New-NetFirewallRule -Name ${psQuote(API_RULE)} " +
            "-DisplayName 'JobFinder: phones on this network' -Direction Inbound -Action Allow " +
            "-Protocol TCP -LocalPort $port -Profile Private,Domain -RemoteAddress LocalSubnet " +
            "| Out-Null"
        )
        appendLine(
          "New-NetFirewallRule -Name ${psQuote(MDNS_RULE)} " +
            "-DisplayName 'JobFinder: found by phones' -Direction Inbound -Action Allow " +
            "-Protocol UDP -LocalPort $MDNS_PORT -Profile Private,Domain " +
            "-RemoteAddress LocalSubnet | Out-Null"
        )
        if (program != null) {
          appendLine(
            "Get-NetFirewallApplicationFilter -Program ${psQuote(program)} " +
              "-ErrorAction SilentlyContinue | Get-NetFirewallRule | Where-Object { " +
              "\$_.Action -eq 'Block' -and \$_.Direction -eq 'Inbound' } | Remove-NetFirewallRule"
          )
        }
      }
      // PowerShell exits 1 when the last command reported an error, even a silenced one (no
      // block rule to remove); a real failure stopped the script before this line.
      appendLine("exit 0")
    }
  }
}
