package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupPage
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What "Set up everything" will do, as the confirm dialog lists it. */
enum class SetupPlanItem {
  /** One Windows permission prompt for the network and the firewall. */
  PERMISSION,
  INSTALL_OLLAMA,
  START_OLLAMA,
  DOWNLOAD_MODEL,
}

@Immutable
data class SetupUiState(
  val steps: List<SetupStep> = emptyList(),
  val computerName: String = "",
  val modelBytes: Long = 0,
  /** The pairing QR for the phone step, once the server has an address. */
  val pairing: PairingInfo? = null,
  /** The confirm dialog is open. */
  val confirming: Boolean = false,
  /** This computer's memory, for offline or online first; `null` until read. */
  val profile: PcProfile? = null,
  /** The page on screen. */
  val page: SetupPage = SetupPage.AI,
  /** `true` once the extension connected: sign-ins can be copied from the user's browser. */
  val canCopySignIns: Boolean = false,
  val copyingSignIns: Boolean = false,
  /** How the last copy of the browser's sign-ins went. */
  val signInCopy: SignInCopy? = null,
) {
  val allDone: Boolean
    get() = steps.isNotEmpty() && steps.all { it.kind.optional || it.state == SetupStepState.DONE }

  /** The steps on [page], in its order. */
  val pageSteps: List<SetupStep>
    get() = page.kinds.mapNotNull { kind -> steps.firstOrNull { it.kind == kind } }

  /** Every step on [page] is done (optional ones too, so the page earns its check mark). */
  fun pageDone(page: SetupPage): Boolean =
    page.kinds.all { kind -> steps.firstOrNull { it.kind == kind }?.state == SetupStepState.DONE }

  /** What the page's fix button would do now; empty when there is nothing for it. */
  val plan: List<SetupPlanItem>
    get() {
      val todo =
        pageSteps
          .filter {
            it.kind.automatic &&
              it.problem != SetupProblem.NEEDS_SERVER &&
              (it.state == SetupStepState.TODO || it.state == SetupStepState.FAILED)
          }
          .associateBy { it.kind }
      return buildList {
        if (SetupStepKind.NETWORK in todo || SetupStepKind.FIREWALL in todo) {
          add(SetupPlanItem.PERMISSION)
        }
        when (todo[SetupStepKind.OLLAMA]?.problem) {
          null -> Unit
          SetupProblem.OLLAMA_STOPPED -> add(SetupPlanItem.START_OLLAMA)
          else -> add(SetupPlanItem.INSTALL_OLLAMA)
        }
        if (SetupStepKind.MODEL in todo) add(SetupPlanItem.DOWNLOAD_MODEL)
      }
    }
}

sealed interface SetupUiEvent {
  /** Opens the confirm dialog for the page's automatic steps. */
  data object FixPage : SetupUiEvent

  data class GoTo(val page: SetupPage) : SetupUiEvent

  data object CopySignIns : SetupUiEvent

  data object Confirm : SetupUiEvent

  data object Cancel : SetupUiEvent

  data class Act(val kind: SetupStepKind) : SetupUiEvent

  data object CheckAgain : SetupUiEvent

  data object Close : SetupUiEvent
}

/** The setup assistant's screen: its steps, the confirm dialog and the pairing QR. */
class SetupViewModel(
  private val assistant: SetupAssistant,
  private val desktop: DesktopFeatures? = null,
  models: LocalModels? = null,
) : ViewModel() {
  private val confirming = MutableStateFlow(false)
  private val profile = MutableStateFlow<PcProfile?>(null)
  private val local = MutableStateFlow(LocalState())

  /** What only this screen keeps: the page and the sign-in copy. */
  private data class LocalState(
    val page: SetupPage = SetupPage.AI,
    val copying: Boolean = false,
    val copy: SignInCopy? = null,
  )

  init {
    if (models != null) {
      viewModelScope.launch {
        profile.value =
          try {
            models.profile()
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            null
          }
      }
    }
  }

  val state: StateFlow<SetupUiState> =
    combine(assistant.steps, desktop?.pairing ?: flowOf(null), confirming, profile, local) {
        steps,
        qr,
        confirm,
        pc,
        here ->
        SetupUiState(
          steps,
          assistant.computerName,
          assistant.modelBytes,
          qr,
          confirm,
          pc,
          page = here.page,
          canCopySignIns =
            desktop != null &&
              steps.any { it.kind == SetupStepKind.EXTENSION && it.state == SetupStepState.DONE },
          copyingSignIns = here.copying,
          signInCopy = here.copy,
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SetupUiState(
          assistant.steps.value,
          assistant.computerName,
          assistant.modelBytes,
          desktop?.pairing?.value,
        ),
      )

  fun onEvent(event: SetupUiEvent) {
    when (event) {
      SetupUiEvent.FixPage -> confirming.value = state.value.plan.isNotEmpty()
      SetupUiEvent.Confirm -> {
        confirming.value = false
        assistant.fix(state.value.page.kinds.toSet())
      }
      is SetupUiEvent.GoTo -> {
        local.update { it.copy(page = event.page) }
        assistant.check()
      }
      SetupUiEvent.CopySignIns -> copySignIns()
      SetupUiEvent.Cancel -> confirming.value = false
      is SetupUiEvent.Act -> assistant.act(event.kind)
      SetupUiEvent.CheckAgain -> assistant.check()
      SetupUiEvent.Close -> assistant.show(false)
    }
  }

  private fun copySignIns() {
    val desktop = desktop ?: return
    if (local.value.copying) return
    local.update { it.copy(copying = true, copy = null) }
    viewModelScope.launch {
      val result =
        try {
          desktop.copyBrowserSignIns()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          SignInCopy.Failed(e.message.orEmpty())
        }
      if (result is SignInCopy.Copied && result.cookies > 0) assistant.markSignedIn()
      local.update { it.copy(copying = false, copy = result) }
    }
  }
}

/** [bytes] in gigabytes with one decimal, as `21.7`. */
fun gigabytes(bytes: Long): String {
  val tenths = (bytes + 50_000_000) / 100_000_000
  return "${tenths / 10}.${tenths % 10}"
}
