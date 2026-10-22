@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import androidx.room3.Room
import androidx.room3.RoomDatabase
import io.github.raghavsatyadev.jobfinder.cache.PhoneCacheDatabase
import io.github.raghavsatyadev.jobfinder.client.ClientPlatform
import io.github.raghavsatyadev.jobfinder.pairing.LanAddress
import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LocalLink
import io.github.raghavsatyadev.jobfinder.pairing.PairedHost
import io.github.raghavsatyadev.jobfinder.pairing.PortResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.use
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.posix.arc4random_uniform

/** [ClientPlatform] on iOS. */
class IosPlatform(private val scanner: QrScanner) : ClientPlatform {
  override val deviceName: String
    get() = UIDevice.currentDevice.name

  override val importsDir: String = appSupportDir() + "/imports"

  /**
   * The system's URL loading on Darwin. It has no hook for name lookup, so a plugin points every
   * request for [PairedHost.NAME] at the paired computer's address instead, and refuses any other
   * host or an address off the local network: the client reaches nothing but the computer.
   */
  override fun httpClient(
    paired: () -> LanIp?,
    config: HttpClientConfig<*>.() -> Unit,
  ): HttpClient =
    HttpClient(Darwin) {
      install(
        createClientPlugin("PairedHostOnly") {
          onRequest { request, _ ->
            check(request.url.host.equals(PairedHost.NAME, ignoreCase = true)) {
              "${request.url.host} is not the paired computer"
            }
            val ip = checkNotNull(paired()) { "No computer is paired" }
            check(LanAddress.isPrivate(ip.literal)) {
              "The paired address is not on the local network"
            }
            request.url.host = ip.literal
          }
        }
      )
      config()
    }

  override fun cacheDatabase(): RoomDatabase.Builder<PhoneCacheDatabase> =
    Room.databaseBuilder<PhoneCacheDatabase>(name = appSupportDir() + "/" + PhoneCacheDatabase.NAME)

  override fun secureInt(bound: Int): Int = arc4random_uniform(bound.toUInt()).toInt()

  override suspend fun scanCode(): String? = scanner.scan()

  /** iOS asks for the local network itself on the first search; nothing to wait for. */
  override suspend fun awaitLocalNetwork() = Unit

  /** iOS shows its prompt only once, so a background search never shows a sheet. */
  override fun localNetworkGranted(): Boolean = true

  override fun localLink(): LocalLink = IosLocalLink.current()

  override suspend fun probe(ip: LanIp, port: Int): PortResult =
    SelectorManager(Dispatchers.IO).use { selector ->
      try {
        withTimeout(CONNECT_TIMEOUT_MS) {
          aSocket(selector).tcp().connect(ip.literal, port).close()
        }
        PortResult.OPEN
      } catch (_: TimeoutCancellationException) {
        PortResult.TIMEOUT
      } catch (e: Exception) {
        if (e.message.orEmpty().contains("refused", ignoreCase = true)) PortResult.REFUSED
        else PortResult.TIMEOUT
      }
    }

  /** iOS has no link to the Wi-Fi page; the app's own Settings page is the nearest. */
  override fun openWifiSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
  }

  override fun log(message: String, error: Throwable?) {
    NSLog("JobFinder: %@ %@", message, error?.toString() ?: "")
  }

  private companion object {
    const val CONNECT_TIMEOUT_MS = 3_000L
  }
}

/** The app's Application Support folder, created on first use. */
fun appSupportDir(): String {
  val manager = NSFileManager.defaultManager
  val url =
    manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
  return checkNotNull(url?.path) { "No Application Support folder" }
}
