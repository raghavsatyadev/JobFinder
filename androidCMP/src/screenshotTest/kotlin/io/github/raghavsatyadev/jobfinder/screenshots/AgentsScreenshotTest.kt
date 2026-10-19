package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentClient
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentEndpoint
import io.github.raghavsatyadev.jobfinder.ui.settings.AgentAccessContent
import io.github.raghavsatyadev.jobfinder.ui.settings.AgentNotice

/** Settings → AI agents (MCP): the address, a setup just copied for Claude Code. */
@PreviewTest
@StatePreviews
@Composable
fun AgentsCopiedScreenshot() {
  Agents(AgentEndpoint("http://127.0.0.1:47822/mcp"), AgentNotice.COPIED to AgentClient.CLAUDE_CODE)
}

/** Another program has the port: agents can't connect, the copy buttons are off. */
@PreviewTest
@StatePreviews
@Composable
fun AgentsPortInUseScreenshot() {
  Agents(AgentEndpoint(null, portInUse = true), null)
}

@Composable
private fun Agents(endpoint: AgentEndpoint, notice: Pair<AgentNotice, AgentClient?>?) {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
      AgentAccessContent(endpoint, notice, onCopy = {}, onRotate = {})
    }
  }
}
