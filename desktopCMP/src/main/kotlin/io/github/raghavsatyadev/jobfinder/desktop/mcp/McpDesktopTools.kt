package io.github.raghavsatyadev.jobfinder.desktop.mcp

import io.github.raghavsatyadev.jobfinder.server.mcp.McpSchema
import io.github.raghavsatyadev.jobfinder.server.mcp.McpServerHost
import io.github.raghavsatyadev.jobfinder.server.mcp.McpTool
import io.github.raghavsatyadev.jobfinder.server.mcp.McpToolError
import io.github.raghavsatyadev.jobfinder.server.mcp.optStr
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInPage
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.Koin

/**
 * MCP tools only the desktop app has: phone pairing, agent access, site sign-ins and local models.
 * Each resolves what it uses from [koin] when called, since the MCP server is built before them. No
 * token, pairing code or cookie is ever returned.
 */
class McpDesktopTools(private val koin: Koin, private val models: McpLocalModelTools) {
  fun tools(): List<McpTool> = access() + signIns() + models.tools()

  private fun features(): DesktopFeatures =
    koin.getOrNull() ?: throw McpToolError("This needs the desktop app.")

  private fun agent(): AgentAccess =
    koin.getOrNull() ?: throw McpToolError("Agent access isn't available in this app.")

  private fun access() = buildList {
    add(
      McpTool(
        "get_phone_pairing",
        "Whether the phone API is on and the address phones pair with. The pairing code holds the pairing token, so it is never shown: the user scans it in Settings → Phone.",
        readOnly = true,
      ) {
        val pairing = features().pairing.value
        buildJsonObject {
          put("on", pairing != null)
          put("baseUrl", pairing?.baseUrl)
        }
      }
    )
    add(
      McpTool(
        "rotate_pairing_token",
        "Makes a new pairing token: every paired phone is unpaired and must scan the new code in Settings → Phone. The API server restarts. Ask the user first.",
        destructive = true,
      ) {
        val features = features()
        features.rotatePairingToken()
        buildJsonObject {
          put("rotated", true)
          put("baseUrl", features.pairing.value?.baseUrl)
          put("next", "Phones must pair again: scan the new code in Settings → Phone.")
        }
      }
    )
    add(
      McpTool(
        "get_agent_access",
        "The MCP server AI agents use: its address and whether its port is taken. It is always on while the app runs; there is no switch to turn it off.",
        readOnly = true,
      ) {
        // The host itself: a fresh AgentAccess still holds its first, empty value.
        val endpoint =
          koin.getOrNull<McpServerHost>()?.endpoint?.value
            ?: throw McpToolError("Agent access isn't available in this app.")
        buildJsonObject {
          put("url", endpoint.url)
          put("portInUse", endpoint.portInUse)
          put("alwaysOn", true)
        }
      }
    )
    add(
      McpTool(
        "rotate_agent_token",
        "Makes a new agent token at once. Every agent, this one too, loses access until the user copies the new setup from Settings → AI agents. The new token is never shown here. Ask the user first.",
        destructive = true,
      ) {
        agent().rotateToken()
        "Rotated. This agent's token no longer works: the user copies the new setup from Settings → AI agents."
      }
    )
  }

  private fun signIns() = buildList {
    add(
      McpTool(
        "check_sign_ins",
        "Looks once at each saved search's job site and says which show the scanner full results and which want a sign-in, with a page to sign in on.",
        readOnly = true,
      ) {
        when (val check = features().checkSignIns()) {
          null -> throw McpToolError("Couldn't check the sign-ins just now.")
          SignInCheck.NoSearches -> "No searches yet, so there is no site to sign in to."
          is SignInCheck.Checked ->
            buildJsonObject {
              put("ready", pages(check.ready))
              put("needed", pages(check.needed))
            }
        }
      }
    )
    add(
      McpTool(
        "open_sign_in",
        "Opens the app's sign-in browser on a job site's page so the user signs in themselves; the app keeps the sign-in for scans. Never sign in for the user.",
        McpSchema.obj(
          "url" to McpSchema.string("An http(s) page, usually one check_sign_ins gave.")
        ),
      ) {
        val url = it.optStr("url")?.let(::webUrl)
        withContext(Dispatchers.Main) { features().openLoginBrowser(url) }
        "The sign-in browser is open${url?.let { u -> " on $u" }.orEmpty()}. The user signs in there and closes it."
      }
    )
    add(
      McpTool(
        "copy_browser_sign_ins",
        "Copies the user's job board sign-ins from their browser through the JobFinder Helper extension (its \"Share my job board sign-ins\" switch must be on). Replaces the app's saved sign-ins for those boards; only counts are returned.",
      ) {
        when (val copy = features().copyBrowserSignIns()) {
          is SignInCopy.Copied ->
            buildJsonObject {
              put("copied", copy.cookies)
              put("missing", JsonArray(copy.missing.map(::JsonPrimitive)))
            }
          SignInCopy.NoExtension ->
            throw McpToolError("JobFinder Helper isn't installed or connected in the browser.")
          SignInCopy.SharingOff ->
            throw McpToolError(
              "Turn on \"Share my job board sign-ins\" in JobFinder Helper, then try again."
            )
          is SignInCopy.Failed -> throw McpToolError(copy.message)
        }
      }
    )
  }

  private fun pages(pages: List<SignInPage>) = buildJsonArray {
    pages.forEach { p ->
      add(
        buildJsonObject {
          put("site", p.site)
          put("url", p.url)
        }
      )
    }
  }

  private fun webUrl(url: String): String {
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    if (scheme != "http" && scheme != "https") throw McpToolError("Use an http(s) address.")
    return url
  }
}
