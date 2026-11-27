package io.github.raghavsatyadev.jobfinder

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import io.github.raghavsatyadev.jobfinder.ui.fake.DemoData
import io.github.raghavsatyadev.jobfinder.ui.sample.SampleMode
import org.koin.core.module.Module

/**
 * Sample mode on Android: a saved switch read once at start. Turning it on or off restarts the
 * process, so the app is either all sample data or all a real computer's, never a mix.
 */
class AndroidSampleMode(private val context: Context) : SampleMode {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override val active: Boolean = prefs.getBoolean(KEY, false)

  override fun start() = switchTo(true)

  override fun exit() = switchTo(false)

  private fun switchTo(on: Boolean) {
    // Written now, not later: the process ends below.
    prefs.edit(commit = true) { putBoolean(KEY, on) }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
  }

  private companion object {
    const val PREFS = "sample_mode"
    const val KEY = "active"
  }
}

/** Every repository on sample jobs that live only in memory; nothing talks to the network. */
fun sampleModule(): Module = fakeRepositoriesModule(DemoData.backend())
