@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import io.github.raghavsatyadev.jobfinder.pairing.Announced
import io.github.raghavsatyadev.jobfinder.pairing.LanDiscovery
import io.github.raghavsatyadev.jobfinder.shared.api.Discovery
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.inet_ntop
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.INET6_ADDRSTRLEN
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

/**
 * [LanDiscovery] with Bonjour (`NSNetServiceBrowser`). iOS asks the user for the local network the
 * first time; the app's Info.plist names the `_jobfinder._tcp` service it looks for.
 */
class BonjourDiscovery : LanDiscovery {
  override fun announced(): Flow<List<Announced>> = callbackFlow {
    val found = linkedMapOf<String, Announced>()
    val resolving = mutableMapOf<String, Pair<NSNetService, ResolveDelegate>>()
    fun publish() = trySend(found.values.sortedBy { it.name })
    val browserDelegate =
      BrowserDelegate(
        onFound = { service ->
          val delegate = ResolveDelegate { resolved ->
            resolving.remove(resolved.name)
            announcement(resolved)?.let {
              found[resolved.name] = it
              publish()
            }
          }
          service.delegate = delegate
          resolving[service.name] = service to delegate
          service.resolveWithTimeout(RESOLVE_TIMEOUT_S)
        },
        onLost = { service ->
          resolving.remove(service.name)?.first?.stop()
          if (found.remove(service.name) != null) publish()
        },
      )
    val browser = NSNetServiceBrowser()
    browser.delegate = browserDelegate
    trySend(emptyList())
    browser.searchForServicesOfType("${Discovery.SERVICE_TYPE}.", inDomain = "local.")
    awaitClose {
      browser.stop()
      resolving.values.forEach { it.first.stop() }
      // Keep the delegates reachable until the browser stops calling them.
      browserDelegate.hashCode()
    }
  }
    // NSNetServiceBrowser calls back on the run loop it was started on.
    .flowOn(Dispatchers.Main)

  private class BrowserDelegate(
    val onFound: (NSNetService) -> Unit,
    val onLost: (NSNetService) -> Unit,
  ) : NSObject(), NSNetServiceBrowserDelegateProtocol {
    @ObjCSignatureOverride
    override fun netServiceBrowser(
      browser: NSNetServiceBrowser,
      didFindService: NSNetService,
      moreComing: Boolean,
    ) = onFound(didFindService)

    @ObjCSignatureOverride
    override fun netServiceBrowser(
      browser: NSNetServiceBrowser,
      didRemoveService: NSNetService,
      moreComing: Boolean,
    ) = onLost(didRemoveService)
  }

  private class ResolveDelegate(val onResolved: (NSNetService) -> Unit) :
    NSObject(), NSNetServiceDelegateProtocol {
    override fun netServiceDidResolveAddress(sender: NSNetService) = onResolved(sender)
  }

  private companion object {
    const val RESOLVE_TIMEOUT_S = 5.0

    fun announcement(service: NSNetService): Announced? {
      val txt =
        service.TXTRecordData()?.let { NSNetService.dictionaryFromTXTRecordData(it) }.orEmpty()
      val attributes =
        txt.entries.associate { (key, value) ->
          key.toString() to (value as? NSData)?.toByteArray()
        }
      val addresses = service.addresses.orEmpty().mapNotNull { (it as? NSData)?.let(::addressText) }
      return Announced.of(service.name, attributes, addresses, service.port.toInt())
    }

    /** The IP of a `sockaddr` in [data], as text. */
    private fun addressText(data: NSData): String? = memScoped {
      val raw = data.bytes?.reinterpret<sockaddr>() ?: return null
      val text = allocArray<ByteVar>(INET6_ADDRSTRLEN)
      when (raw.pointed.sa_family.toInt()) {
        AF_INET -> {
          val address = raw.reinterpret<sockaddr_in>().pointed.sin_addr
          inet_ntop(AF_INET, address.ptr, text, INET6_ADDRSTRLEN.toUInt())?.toKString()
        }
        AF_INET6 -> {
          val address = raw.reinterpret<sockaddr_in6>().pointed.sin6_addr
          inet_ntop(AF_INET6, address.ptr, text, INET6_ADDRSTRLEN.toUInt())?.toKString()
        }
        else -> null
      }
    }
  }
}
