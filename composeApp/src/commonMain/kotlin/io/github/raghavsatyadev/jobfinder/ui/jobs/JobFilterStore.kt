package io.github.raghavsatyadev.jobfinder.ui.jobs

import com.russhwolf.settings.Settings
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The job list's sort and sheet filters, kept on this device across restarts. */
interface JobFilterStore {
  fun load(): JobFilter

  fun save(filter: JobFilter)
}

/** Nothing kept: previews and screenshot tests. */
object NoJobFilterStore : JobFilterStore {
  override fun load(): JobFilter = JobFilter()

  override fun save(filter: JobFilter) = Unit
}

/**
 * [JobFilterStore] in the platform's small key-value store (SharedPreferences on Android, Java
 * preferences on the desktop). The search text is never kept.
 */
class SettingsJobFilterStore(private val create: () -> Settings = { Settings() }) : JobFilterStore {
  private val json = Json { ignoreUnknownKeys = true }

  // Made on first use; null where the platform store can't start (previews, screenshot tests).
  private val settings: Settings? by lazy {
    try {
      create()
    } catch (_: Exception) {
      null
    }
  }

  override fun load(): JobFilter {
    val saved = settings?.getStringOrNull(KEY) ?: return JobFilter()
    return try {
      json.decodeFromString<JobFilter>(saved).copy(search = null)
    } catch (_: SerializationException) {
      JobFilter()
    } catch (_: IllegalArgumentException) {
      JobFilter()
    }
  }

  override fun save(filter: JobFilter) {
    val kept = filter.copy(search = null)
    val settings = settings ?: return
    if (kept == JobFilter()) settings.remove(KEY)
    else settings.putString(KEY, json.encodeToString(kept))
  }

  private companion object {
    const val KEY = "jobs.filter"
  }
}
