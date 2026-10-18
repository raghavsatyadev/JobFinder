package io.github.raghavsatyadev.jobfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.raghavsatyadev.jobfinder.pairing.LocalNetworkAccess
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
  private val pairing: PhonePairing by inject()
  private val navigation =
    MutableSharedFlow<TopLevel>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val localNetwork: LocalNetworkAccess by inject()
  private val notificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
  private val localNetworkPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
      localNetwork.onAnswered()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) handle(intent)
    setContent {
      // Test tags become resource ids, so Maestro flows can find job cards.
      Box(Modifier.semantics { testTagsAsResourceId = true }) { App(navigation = navigation) }
    }
    // Back on the pairing screen opened to add a computer returns to the app.
    val closeAdding =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = pairing.showPairingScreen(false)
      }
    onBackPressedDispatcher.addCallback(this, closeAdding)
    lifecycleScope.launch { pairing.adding.collect { closeAdding.isEnabled = it } }
    // On the pairing screen, ask for the local network before looking for computers.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        combine(pairing.address, pairing.adding) { address, adding -> address == null || adding }
          .first { it }
        askForLocalNetwork()
      }
    }
    // Once paired, ask to show new-job notifications.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        pairing.address.first { it != null }
        askForNotifications()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handle(intent)
  }

  private fun handle(intent: Intent) {
    if (intent.action == ACTION_OPEN_JOBS) navigation.tryEmit(TopLevel.Jobs)
    // Any app can send this link to a debug build, so it never replaces an existing pairing.
    debugPairingCode(intent)
      ?.takeIf { pairing.address.value == null }
      ?.let { code -> lifecycleScope.launch { pairing.pair(code) } }
  }

  private fun askForLocalNetwork() {
    if (!localNetwork.needed || localNetwork.granted() || askedLocalNetwork) {
      localNetwork.onAnswered()
      return
    }
    askedLocalNetwork = true
    localNetworkPermission.launch(LocalNetworkAccess.PERMISSION)
  }

  private fun askForNotifications() {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !asked &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      asked = true
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  companion object {
    /** Opens the jobs list (the new-jobs notification). */
    const val ACTION_OPEN_JOBS = "io.github.raghavsatyadev.jobfinder.OPEN_JOBS"

    /** Asked once per process; Android stops showing the prompt after two refusals anyway. */
    private var asked = false
    private var askedLocalNetwork = false
  }
}
