package io.github.raghavsatyadev.jobfinder.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import io.github.raghavsatyadev.jobfinder.App
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.client.APP_SCOPE
import io.github.raghavsatyadev.jobfinder.client.ClientPlatform
import io.github.raghavsatyadev.jobfinder.client.clientModule
import io.github.raghavsatyadev.jobfinder.initKoin
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.LanDiscovery
import io.github.raghavsatyadev.jobfinder.pairing.PairingStorage
import io.github.raghavsatyadev.jobfinder.pairing.PairingStore
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** The pairings' own defaults suite, apart from anything else the app keeps. */
private const val PAIRING_SUITE = "pairing"

/** iOS's half of the phone app: the system services [clientModule] builds on. */
fun iosModule(): Module = module {
  single { QrScanner() }
  single<ClientPlatform> { IosPlatform(get()) }
  single<PairingStorage> { PairingStore(NSUserDefaultsSettings(NSUserDefaults(PAIRING_SUITE))) }
  single<FilePicker> { IosFilePicker() }
  single<LanDiscovery> { BonjourDiscovery() }
  single<DeviceBackupKeys> { KeychainBackupKeys() }
  single { NewJobsNotifier(get(), get(APP_SCOPE)) }
}

private var started = false

/** Held here: the notification center keeps only a weak reference to its delegate. */
private val foregroundNotifications = ForegroundNotifications()

/** Shows notifications while the app is open too; iOS hides them by default then. */
private class ForegroundNotifications : NSObject(), UNUserNotificationCenterDelegateProtocol {
  override fun userNotificationCenter(
    center: UNUserNotificationCenter,
    willPresentNotification: UNNotification,
    withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
  ) {
    withCompletionHandler(
      UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound
    )
  }
}

/** Starts Koin and the background work once per process. */
private fun start() {
  if (started) return
  started = true
  initKoin(extraModules = listOf(clientModule(), iosModule()))
  val koin = KoinPlatform.getKoin()
  UNUserNotificationCenter.currentNotificationCenter().delegate = foregroundNotifications
  koin.get<NewJobsNotifier>().start()
  koin.get<PhoneSync>().start()
  koin.get<AddressKeeper>().start(koin.get(APP_SCOPE))
}

/** The app's screen, for the Swift entry point. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
  start()
  return ComposeUIViewController { App() }
}
