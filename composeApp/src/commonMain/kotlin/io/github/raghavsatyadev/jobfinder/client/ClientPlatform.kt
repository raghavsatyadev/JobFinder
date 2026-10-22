package io.github.raghavsatyadev.jobfinder.client

import androidx.room3.RoomDatabase
import io.github.raghavsatyadev.jobfinder.cache.PhoneCacheDatabase
import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LocalLink
import io.github.raghavsatyadev.jobfinder.pairing.PortResult
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * What a phone app (Android, iOS) provides that common code can't: the system's network, storage,
 * camera and settings. [clientModule] builds everything else on it. Bound by the platform's module
 * together with `PairingStorage`, `LanDiscovery`, `FilePicker` and `DeviceBackupKeys`.
 */
interface ClientPlatform {
  /** This device's name as the computer's approval dialog shows it. */
  val deviceName: String

  /** Folder in the app's own storage for backups imported on the phone. */
  val importsDir: String

  /**
   * An HTTP client on the system's engine that reaches only the computer at [paired] under
   * `PairedHost.NAME`, set up by [config].
   */
  fun httpClient(paired: () -> LanIp?, config: HttpClientConfig<*>.() -> Unit): HttpClient

  /** Where the phone's copy of the computer's data lives. */
  fun cacheDatabase(): RoomDatabase.Builder<PhoneCacheDatabase>

  /** A random number in `0 until bound` from the system's secure generator. */
  fun secureInt(bound: Int): Int

  /** Scans a pairing QR code; `null` when the user cancels or there is no camera. */
  suspend fun scanCode(): String?

  /** Waits until the user answered the local network question, or returns when none is asked. */
  suspend fun awaitLocalNetwork()

  /** Whether a search of the local network may run now without a system prompt. */
  fun localNetworkGranted(): Boolean

  /** The network the phone is on, for the connection doctor. */
  fun localLink(): LocalLink

  /** A plain TCP connect to [ip]:[port]: a timeout and a refusal mean different fixes. */
  suspend fun probe(ip: LanIp, port: Int): PortResult

  fun openWifiSettings()

  fun log(message: String, error: Throwable?)
}
