package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.server.http.ServerEndpoint
import io.github.raghavsatyadev.jobfinder.server.http.ServerIdentity
import io.github.raghavsatyadev.jobfinder.shared.api.Discovery
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Announces the API server on the LAN over mDNS (`_jobfinder._tcp`), following [endpoint]: a new
 * address or port replaces the announcement, no address withdraws it. The TXT record carries the
 * computer's id, so a phone recognises its computer after the address changes.
 */
class LanAnnouncer(
  private val endpoint: StateFlow<ServerEndpoint?>,
  private val identity: ServerIdentity,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(LanAnnouncer::class.java)
  private var dns: JmDNS? = null

  fun start(scope: CoroutineScope) {
    scope.launch {
      endpoint.collect { current ->
        withdraw()
        if (current != null) announce(current)
      }
    }
  }

  private fun announce(at: ServerEndpoint) {
    try {
      val created = JmDNS.create(InetAddress.getByName(at.host), identity.name)
      created.registerService(serviceInfo(identity, at.port))
      dns = created
      log.info("Announced {} on {}:{}", Discovery.SERVICE_TYPE, at.host, at.port)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      // Phones can still connect by address or QR code.
      log.warn("Could not announce the server on the LAN", e)
    }
  }

  @Synchronized
  private fun withdraw() {
    val current = dns ?: return
    dns = null
    try {
      current.unregisterAllServices()
      current.close()
    } catch (e: Exception) {
      log.warn("Could not withdraw the LAN announcement", e)
    }
  }

  override fun close() = withdraw()

  companion object {
    /** The service record: the computer's name as the instance, its id in the TXT record. */
    fun serviceInfo(identity: ServerIdentity, port: Int): ServiceInfo =
      ServiceInfo.create(
        "${Discovery.SERVICE_TYPE}.local.",
        identity.name,
        port,
        0,
        0,
        mapOf(Discovery.TXT_ID to identity.id),
      )
  }
}
