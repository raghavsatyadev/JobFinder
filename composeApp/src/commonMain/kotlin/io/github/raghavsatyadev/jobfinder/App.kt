package io.github.raghavsatyadev.jobfinder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_loading
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.navigation.AppShell
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import io.github.raghavsatyadev.jobfinder.ui.pairing.ConnectionBanner
import io.github.raghavsatyadev.jobfinder.ui.pairing.ConnectionDoctorDialog
import io.github.raghavsatyadev.jobfinder.ui.pairing.PairingScreen
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.PhoneCopy
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.setup.SetupDialog
import io.github.raghavsatyadev.jobfinder.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/**
 * The whole UI, the same on desktop and Android. Koin must be started first. [navigation] switches
 * to a top-level destination from outside the UI (a click on a notification). On Android the
 * pairing screen shows instead until the phone is paired with a computer, or while adding one. On
 * desktop the setup assistant shows over the app while it is open.
 */
@Composable
fun App(navigation: Flow<TopLevel> = emptyFlow()) {
  val koin = getKoin()
  val pairing = remember(koin) { koin.getOrNull<PhonePairing>() }
  val doctor = remember(koin) { koin.getOrNull<ConnectionDoctor>() }
  val copy = remember(koin) { koin.getOrNull<PhoneCopy>() }
  val setup = remember(koin) { koin.getOrNull<SetupAssistant>() }
  AppTheme {
    if (pairing == null) {
      AppShell(navigation = navigation)
      if (setup != null) {
        val setupVisible by setup.visible.collectAsStateWithLifecycle()
        if (setupVisible) SetupDialog()
      }
    } else {
      val address by pairing.address.collectAsStateWithLifecycle()
      val adding by pairing.adding.collectAsStateWithLifecycle()
      AnimatedContent(address != null && !adding, label = "pairing") { paired ->
        if (paired) PairedApp(pairing, doctor, copy, navigation) else PairingScreen()
      }
    }
  }
}

/**
 * The app on a paired phone: a one-line banner when the computer can't be reached, a thin bar on
 * top while the phone reads from it, and the doctor.
 */
@Composable
private fun PairedApp(
  pairing: PhonePairing,
  doctor: ConnectionDoctor?,
  copy: PhoneCopy?,
  navigation: Flow<TopLevel>,
) {
  val connected by pairing.connected.collectAsStateWithLifecycle()
  val computers by pairing.computers.collectAsStateWithLifecycle()
  val doctorVisible by
    (doctor?.visible ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
  // The event stream takes a moment to open; only then is "not connected" news.
  var unreachable by remember { mutableStateOf(false) }
  LaunchedEffect(connected) {
    unreachable = false
    if (!connected) {
      delay(BANNER_DELAY_MS)
      unreachable = true
    }
  }
  val showBanner = unreachable && doctor != null
  Column {
    AnimatedVisibility(showBanner) {
      ConnectionBanner(
        computer = computers.firstOrNull { it.active }?.label.orEmpty(),
        onFix = { doctor?.show(true) },
      )
    }
    Box(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier) {
      // Each computer gets fresh screens: no list or setting from the one before stays on show.
      key(computers.firstOrNull { it.active }?.key) {
        val screens = remember { ComputerScreens() }
        DisposableEffect(screens) { onDispose { screens.viewModelStore.clear() } }
        CompositionLocalProvider(
          LocalViewModelStoreOwner provides screens,
          LocalComputerOffline provides unreachable,
        ) {
          AppShell(navigation = navigation)
        }
      }
      if (copy != null) {
        CopyBusyBar(
          copy,
          Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
        )
      }
    }
  }
  if (doctorVisible) ConnectionDoctorDialog()
}

/**
 * A thin bar while the phone reads from the computer; the screens below already show the phone's
 * copy. Shows only once a read takes [BUSY_BAR_DELAY_MS], so quick ones do not flash it.
 */
@Composable
private fun CopyBusyBar(copy: PhoneCopy, modifier: Modifier = Modifier) {
  val busy by copy.busy.collectAsStateWithLifecycle()
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(busy) {
    if (busy) delay(BUSY_BAR_DELAY_MS)
    visible = busy
  }
  AnimatedVisibility(visible, modifier.fillMaxWidth(), enter = fadeIn(), exit = fadeOut()) {
    val description = stringResource(Res.string.common_loading)
    LinearProgressIndicator(
      Modifier.fillMaxWidth().height(3.dp).semantics { contentDescription = description },
      gapSize = 0.dp,
    )
  }
}

/** The view models of one computer's screens. */
private class ComputerScreens : ViewModelStoreOwner {
  override val viewModelStore = ViewModelStore()
}

private const val BANNER_DELAY_MS = 6_000L
private const val BUSY_BAR_DELAY_MS = 300L
