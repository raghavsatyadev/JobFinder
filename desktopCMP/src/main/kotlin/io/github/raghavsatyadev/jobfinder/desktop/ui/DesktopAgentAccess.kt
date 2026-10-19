package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.server.mcp.McpServerHost
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [AgentAccess] on the in-process [McpServerHost]. */
class DesktopAgentAccess(
  private val host: McpServerHost,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AgentAccess {
  override val endpoint: StateFlow<AgentEndpoint> =
    host.endpoint
      .map { AgentEndpoint(it.url, it.portInUse) }
      .stateIn(scope, SharingStarted.Eagerly, AgentEndpoint(null))

  override fun token(): String = host.token()

  override fun rotateToken() {
    host.rotateToken()
  }
}
