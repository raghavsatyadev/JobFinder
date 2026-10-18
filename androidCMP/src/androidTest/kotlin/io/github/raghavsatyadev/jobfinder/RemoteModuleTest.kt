package io.github.raghavsatyadev.jobfinder

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

/** The app's Koin graph, as [JobFinderApplication] starts it, builds what the screens ask for. */
@RunWith(AndroidJUnit4::class)
class RemoteModuleTest {
  @Test
  fun pairingResolves() {
    assertNotNull(KoinPlatform.getKoin().get<PhonePairing>())
  }
}
