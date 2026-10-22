package io.github.raghavsatyadev.jobfinder

import android.app.Application
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.client.APP_SCOPE
import io.github.raghavsatyadev.jobfinder.client.clientModule
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class JobFinderApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    initKoin(
      config = { androidContext(this@JobFinderApplication) },
      extraModules = listOf(clientModule(), androidModule()),
    )
    registerActivityLifecycleCallbacks(get<CurrentActivity>())
    get<NewJobsNotifier>().start()
    get<PhoneSync>().start()
    get<AddressKeeper>().start(get(APP_SCOPE))
  }
}
