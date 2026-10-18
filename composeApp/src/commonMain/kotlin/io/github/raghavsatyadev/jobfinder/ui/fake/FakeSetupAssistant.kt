package io.github.raghavsatyadev.jobfinder.ui.fake

import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import kotlinx.coroutines.flow.MutableStateFlow

/** A [SetupAssistant] for previews and tests: shows [steps] and records what was asked. */
class FakeSetupAssistant(
  steps: List<SetupStep> = SetupStepKind.entries.map { SetupStep(it, SetupStepState.DONE) },
  override val computerName: String = "DESK-PC",
  override val modelBytes: Long = 22_000_000_000L,
) : SetupAssistant {
  override val visible = MutableStateFlow(false)
  override val steps = MutableStateFlow(steps)
  val acts = mutableListOf<SetupStepKind>()
  val fixes = mutableListOf<Set<SetupStepKind>>()
  var checks = 0

  override fun show(show: Boolean) {
    visible.value = show
  }

  override fun check() {
    checks++
  }

  override fun fix(kinds: Set<SetupStepKind>) {
    fixes += kinds
  }

  override fun markSignedIn() = Unit

  override fun act(kind: SetupStepKind) {
    acts += kind
  }
}
