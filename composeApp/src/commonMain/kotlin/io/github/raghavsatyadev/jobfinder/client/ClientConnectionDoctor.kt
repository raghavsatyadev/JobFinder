package io.github.raghavsatyadev.jobfinder.client

import io.github.raghavsatyadev.jobfinder.pairing.DoctorChecks
import io.github.raghavsatyadev.jobfinder.pairing.TokenResult
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn

/** [ConnectionDoctor] on a phone: [DoctorChecks] over the real network. */
class ClientConnectionDoctor(
  private val checks: DoctorChecks,
  private val platform: ClientPlatform,
) : ConnectionDoctor {
  private val _visible = MutableStateFlow(false)
  override val visible: StateFlow<Boolean> = _visible.asStateFlow()

  override fun show(visible: Boolean) {
    _visible.value = visible
  }

  override fun run(): Flow<List<DoctorCheck>> = checks.run().flowOn(Dispatchers.IO)

  override fun openWifiSettings() = platform.openWifiSettings()

  companion object {
    /** Whether the computer still takes [call]'s token. */
    suspend fun tokenResult(call: suspend () -> Unit): TokenResult =
      try {
        call()
        TokenResult.ACCEPTED
      } catch (e: ApiFailure) {
        if (e.code == ApiErrorCode.UNAUTHORIZED) TokenResult.REJECTED else TokenResult.ERROR
      }
  }
}
