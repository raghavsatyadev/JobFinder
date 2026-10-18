package io.github.raghavsatyadev.jobfinder.pairing

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn

/** [LocalLink] from Android's link properties: its own addresses with prefix lengths. */
data class AndroidLocalLink(
  override val onLan: Boolean,
  val prefixes: List<Pair<InetAddress, Int>>,
) : LocalLink {
  override fun contains(ip: LanIp): Boolean = contains(ip.inet())

  fun contains(ip: InetAddress): Boolean = prefixes.any { (own, length) ->
    val a = own.address
    val b = ip.address
    if (a.size != b.size) return@any false
    val full = length / BITS
    val rest = length % BITS
    (0 until full).all { a[it] == b[it] } &&
      (rest == 0 ||
        (a[full].toInt() xor b[full].toInt()) and (MASK shl (BITS - rest)) and MASK == 0)
  }

  private companion object {
    const val BITS = 8
    const val MASK = 0xFF
  }
}

/** [ConnectionDoctor] on Android: [DoctorChecks] over the real network. */
class AndroidConnectionDoctor(
  private val context: Context,
  private val checks: DoctorChecks,
  private val activities: CurrentActivity,
) : ConnectionDoctor {
  private val _visible = MutableStateFlow(false)
  override val visible: StateFlow<Boolean> = _visible.asStateFlow()

  override fun show(visible: Boolean) {
    _visible.value = visible
  }

  override fun run(): Flow<List<DoctorCheck>> = checks.run().flowOn(Dispatchers.IO)

  override fun openWifiSettings() {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
    val activity = activities.current
    if (activity != null) {
      activity.startActivity(intent)
    } else {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  companion object {
    private const val CONNECT_TIMEOUT_MS = 3_000

    /** The active network, from [ConnectivityManager]. */
    fun localLink(context: Context): LocalLink {
      val manager = context.getSystemService(ConnectivityManager::class.java)
      val network = manager?.activeNetwork ?: return AndroidLocalLink(false, emptyList())
      val caps = manager.getNetworkCapabilities(network)
      val onLan =
        caps != null &&
          (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
      val prefixes =
        manager.getLinkProperties(network)?.linkAddresses.orEmpty().map {
          it.address to it.prefixLength
        }
      return AndroidLocalLink(onLan, prefixes)
    }

    /** A plain TCP connect: a timeout and a refusal mean different fixes. */
    fun probe(ip: LanIp, port: Int): PortResult =
      try {
        Socket().use { it.connect(InetSocketAddress(ip.inet(), port), CONNECT_TIMEOUT_MS) }
        PortResult.OPEN
      } catch (_: SocketTimeoutException) {
        PortResult.TIMEOUT
      } catch (_: NoRouteToHostException) {
        PortResult.TIMEOUT
      } catch (e: ConnectException) {
        if (e.message.orEmpty().contains("ECONNREFUSED")) PortResult.REFUSED else PortResult.TIMEOUT
      } catch (_: java.io.IOException) {
        PortResult.TIMEOUT
      }

    /** Whether the computer still takes [call]'s token. */
    suspend fun tokenResult(call: suspend () -> Unit): TokenResult =
      try {
        call()
        TokenResult.ACCEPTED
      } catch (e: ApiFailure) {
        if (e.code == ApiErrorCode.UNAUTHORIZED) TokenResult.REJECTED else TokenResult.ERROR
      }
  }
}

/** The address as Java's type; an IP literal, so nothing is looked up. */
fun LanIp.inet(): InetAddress = InetAddress.getByName(literal)
