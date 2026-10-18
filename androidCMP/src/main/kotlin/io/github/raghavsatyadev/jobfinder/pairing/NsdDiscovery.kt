package io.github.raghavsatyadev.jobfinder.pairing

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.raghavsatyadev.jobfinder.shared.api.Discovery
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** [LanDiscovery] with Android's [NsdManager]; it handles multicast, so no extra permission. */
class NsdDiscovery(private val nsd: NsdManager) : LanDiscovery {
  // Before Android 14 the manager resolves one service at a time.
  private val resolving = Mutex()

  override fun announced(): Flow<List<Announced>> = callbackFlow {
    val found = ConcurrentHashMap<String, Announced>()
    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
          launch {
            val announced = resolve(info) ?: return@launch
            found[info.serviceName] = announced
            trySend(found.values.sortedBy { it.name })
          }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
          if (found.remove(info.serviceName) != null) trySend(found.values.sortedBy { it.name })
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onDiscoveryStopped(serviceType: String) = Unit
      }
    trySend(emptyList())
    nsd.discoverServices(Discovery.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
  }

  private suspend fun resolve(info: NsdServiceInfo): Announced? =
    withTimeoutOrNull(RESOLVE_TIMEOUT) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        resolveWithCallback(info)
      } else {
        resolving.withLock { resolveLegacy(info) }
      }
    }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  private suspend fun resolveWithCallback(info: NsdServiceInfo): Announced? {
    val result = CompletableDeferred<Announced?>()
    val callback =
      object : NsdManager.ServiceInfoCallback {
        override fun onServiceUpdated(resolved: NsdServiceInfo) {
          Announced.of(
              resolved.serviceName,
              resolved.attributes,
              resolved.hostAddresses.mapNotNull { it.hostAddress },
              resolved.port,
            )
            ?.let(result::complete)
        }

        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
          result.complete(null)
        }

        override fun onServiceLost() = Unit

        override fun onServiceInfoCallbackUnregistered() = Unit
      }
    nsd.registerServiceInfoCallback(info, EXECUTOR, callback)
    return try {
      result.await()
    } finally {
      runCatching { nsd.unregisterServiceInfoCallback(callback) }
    }
  }

  @Suppress("DEPRECATION")
  private suspend fun resolveLegacy(info: NsdServiceInfo): Announced? =
    suspendCancellableCoroutine { continuation ->
      nsd.resolveService(
        info,
        object : NsdManager.ResolveListener {
          override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            if (continuation.isActive) continuation.resume(null)
          }

          override fun onServiceResolved(resolved: NsdServiceInfo) {
            val announced =
              Announced.of(
                resolved.serviceName,
                resolved.attributes,
                listOfNotNull(resolved.host?.hostAddress),
                resolved.port,
              )
            if (continuation.isActive) continuation.resume(announced)
          }
        },
      )
    }

  private companion object {
    val RESOLVE_TIMEOUT = 5.seconds
    val EXECUTOR = Executors.newSingleThreadExecutor()
  }
}
