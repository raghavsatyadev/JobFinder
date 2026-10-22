package io.github.raghavsatyadev.jobfinder

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.raghavsatyadev.jobfinder.cache.PhoneCacheDatabase
import io.github.raghavsatyadev.jobfinder.client.APP_SCOPE
import io.github.raghavsatyadev.jobfinder.client.ClientPlatform
import io.github.raghavsatyadev.jobfinder.pairing.AndroidConnectionProbe
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import io.github.raghavsatyadev.jobfinder.pairing.LanDiscovery
import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LocalLink
import io.github.raghavsatyadev.jobfinder.pairing.LocalNetworkAccess
import io.github.raghavsatyadev.jobfinder.pairing.NsdDiscovery
import io.github.raghavsatyadev.jobfinder.pairing.PairedHostDns
import io.github.raghavsatyadev.jobfinder.pairing.PairingStorage
import io.github.raghavsatyadev.jobfinder.pairing.PairingStore
import io.github.raghavsatyadev.jobfinder.pairing.PortResult
import io.github.raghavsatyadev.jobfinder.pairing.QrCodeScanner
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Backups imported on the phone, in the app's files, until the computer has them. */
private const val IMPORTS_DIR = "imports"
private const val BACKUP_KEY_FILE = "backup-key"
private const val PAIRING_PREFS = "pairing"

/** [ClientPlatform] on Android. */
class AndroidPlatform(
  private val context: Context,
  private val activities: CurrentActivity,
  private val scanner: QrCodeScanner,
  private val localNetwork: LocalNetworkAccess,
) : ClientPlatform {
  private val random = SecureRandom()

  override val deviceName: String
    get() =
      Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf {
        it.isNotBlank()
      } ?: "${Build.MANUFACTURER} ${Build.MODEL}"

  override val importsDir: String = context.filesDir.resolve(IMPORTS_DIR).absolutePath

  /** OkHttp resolving only the paired computer, through [PairedHostDns]. */
  override fun httpClient(
    paired: () -> LanIp?,
    config: HttpClientConfig<*>.() -> Unit,
  ): HttpClient =
    HttpClient(OkHttp) {
      engine { config { dns(PairedHostDns(paired)) } }
      config()
    }

  override fun cacheDatabase(): RoomDatabase.Builder<PhoneCacheDatabase> =
    Room.databaseBuilder<PhoneCacheDatabase>(
      context,
      context.getDatabasePath(PhoneCacheDatabase.NAME).absolutePath,
    )

  override fun secureInt(bound: Int): Int = random.nextInt(bound)

  override suspend fun scanCode(): String? = scanner.scan()

  override suspend fun awaitLocalNetwork() = localNetwork.awaitAnswer()

  override fun localNetworkGranted(): Boolean = localNetwork.granted()

  override fun localLink(): LocalLink = AndroidConnectionProbe.localLink(context)

  override suspend fun probe(ip: LanIp, port: Int): PortResult =
    withContext(Dispatchers.IO) { AndroidConnectionProbe.probe(ip, port) }

  override fun openWifiSettings() {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
    val activity = activities.current
    if (activity != null) {
      activity.startActivity(intent)
    } else {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  override fun log(message: String, error: Throwable?) {
    Log.w("PhoneSync", message, error)
  }
}

/** Android's half of the phone app: the system services [clientModule] builds on. */
fun androidModule(): Module = module {
  single { CurrentActivity() }
  single { QrCodeScanner(get()) }
  single { LocalNetworkAccess(get()) }
  single<ClientPlatform> { AndroidPlatform(get(), get(), get(), get()) }
  single<PairingStorage> {
    PairingStore(
      SharedPreferencesSettings(
        get<Context>().getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE)
      )
    )
  }
  single<FilePicker> { AndroidFilePicker(get()) }
  single<LanDiscovery> { NsdDiscovery(get<Context>().getSystemService(NsdManager::class.java)) }
  single<DeviceBackupKeys> {
    AndroidDeviceBackupKeys(get<Context>().noBackupFilesDir.resolve(BACKUP_KEY_FILE))
  }
  single { NewJobsNotifier(get(), get(), get(APP_SCOPE)) }
}
