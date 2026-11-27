package io.github.raghavsatyadev.jobfinder

import android.app.Application
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.client.APP_SCOPE
import io.github.raghavsatyadev.jobfinder.client.clientModule
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import io.github.raghavsatyadev.jobfinder.ui.sample.SampleMode
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class JobFinderApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    val sample = AndroidSampleMode(this)
    // Sample mode leaves out the computer client: no pairing, no sync, no LAN discovery.
    val data = if (sample.active) sampleModule() else clientModule()
    initKoin(
      config = { androidContext(this@JobFinderApplication) },
      extraModules = listOf(data, androidModule(), module { single<SampleMode> { sample } }),
    )
    registerActivityLifecycleCallbacks(get<CurrentActivity>())
    if (sample.active) return
    get<NewJobsNotifier>().start()
    get<PhoneSync>().start()
    get<AddressKeeper>().start(get(APP_SCOPE))
  }
}
