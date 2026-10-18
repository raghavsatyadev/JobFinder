package io.github.raghavsatyadev.jobfinder

import android.content.Intent

/**
 * Debug builds only: `f2a-debug://pair?code=<pairing code>` pairs without the camera, for Maestro.
 * The code goes through the same checks as a scanned one (private address, the computer answers).
 */
fun debugPairingCode(intent: Intent): String? =
  intent.data
    ?.takeIf {
      intent.action == Intent.ACTION_VIEW && it.scheme == "f2a-debug" && it.host == "pair"
    }
    ?.getQueryParameter("code")
    ?.takeIf { it.isNotBlank() }
