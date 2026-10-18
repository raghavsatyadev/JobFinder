package io.github.raghavsatyadev.jobfinder

import android.app.Application
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named

class JobFinderApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    initKoin(
      config = { androidContext(this@JobFinderApplication) },
      extraModules = listOf(remoteModule()),
    )
    registerActivityLifecycleCallbacks(get<CurrentActivity>())
    get<NewJobsNotifier>().start()
    get<PhoneSync>().start()
    get<AddressKeeper>().start(get(named("appScope")))
  }
}
