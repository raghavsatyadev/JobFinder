package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.runtime.Immutable
import com.russhwolf.settings.Settings
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The order of the Searches list. */
@Serializable
enum class LinkSort {
  @SerialName("newest") NEWEST,
  @SerialName("oldest") OLDEST,
  @SerialName("name") NAME,
  /** Checked most recently first. */
  @SerialName("last_checked") LAST_CHECKED,
  /** Most failed scans in a row first: the ones that need a look. */
  @SerialName("failing") FAILING,
}

/** How the Searches list is narrowed, ordered and grouped. The search text is never kept. */
@Immutable
@Serializable
data class LinksView(
  val query: String = "",
  /** Boards to show; empty shows every board. */
  val siteIds: Set<Long> = emptySet(),
  val brokenOnly: Boolean = false,
  val sort: LinkSort = LinkSort.NEWEST,
  /** One section per board, boards by name. */
  val groupByBoard: Boolean = false,
) {
  val filtered: Boolean
    get() = query.isNotBlank() || siteIds.isNotEmpty() || brokenOnly

  /** The same order and grouping, without the text and chips that hide searches. */
  fun cleared(): LinksView = copy(query = "", siteIds = emptySet(), brokenOnly = false)
}

/** A board's searches, or every search when the list is not grouped ([siteId] `null`). */
@Immutable data class LinkGroup(val site: Site?, val siteId: Long?, val links: List<Link>)

/** [links] narrowed and ordered by this view, in one group, or one per board. */
fun LinksView.arrange(links: List<Link>, sites: Map<Long, Site>): List<LinkGroup> {
  val text = query.trim().lowercase()
  val shown =
    links
      .filter { siteIds.isEmpty() || it.siteId in siteIds }
      .filter { !brokenOnly || it.isBroken }
      .filter {
        text.isEmpty() ||
          it.title.lowercase().contains(text) ||
          it.url.lowercase().contains(text) ||
          sites[it.siteId]?.name?.lowercase()?.contains(text) == true
      }
      .sortedWith(sort.comparator())
  if (!groupByBoard) return listOf(LinkGroup(null, null, shown))
  return shown
    .groupBy { it.siteId }
    .map { (siteId, group) -> LinkGroup(sites[siteId], siteId, group) }
    .sortedBy { it.site?.name?.lowercase() ?: LAST }
}

private const val LAST = "￿"

private fun LinkSort.comparator(): Comparator<Link> =
  when (this) {
    LinkSort.NEWEST -> compareByDescending<Link> { it.createdAt }.thenByDescending { it.id }
    LinkSort.OLDEST -> compareBy<Link> { it.createdAt }.thenBy { it.id }
    LinkSort.NAME -> compareBy<Link> { it.title.lowercase() }.thenBy { it.id }
    LinkSort.LAST_CHECKED -> compareByDescending<Link> { it.lastScrapedAt }.thenBy { it.id }
    LinkSort.FAILING ->
      compareByDescending<Link> { it.scrapeFailureCount }.thenByDescending { it.createdAt }
  }

/** Keeps the Searches view between app starts, on this device. */
interface LinksViewStore {
  fun load(): LinksView

  fun save(view: LinksView)
}

/** Nothing kept: previews and screenshot tests. */
object NoLinksViewStore : LinksViewStore {
  override fun load(): LinksView = LinksView()

  override fun save(view: LinksView) = Unit
}

/** [LinksViewStore] in the platform's key-value store, like the jobs filter. */
class SettingsLinksViewStore(private val create: () -> Settings = { Settings() }) : LinksViewStore {
  private val json = Json { ignoreUnknownKeys = true }

  // Made on first use; null where the platform store can't start (previews, screenshot tests).
  private val settings: Settings? by lazy {
    try {
      create()
    } catch (_: Exception) {
      null
    }
  }

  override fun load(): LinksView {
    val saved = settings?.getStringOrNull(KEY) ?: return LinksView()
    return try {
      json.decodeFromString<LinksView>(saved).copy(query = "")
    } catch (_: SerializationException) {
      LinksView()
    } catch (_: IllegalArgumentException) {
      LinksView()
    }
  }

  override fun save(view: LinksView) {
    val kept = view.copy(query = "")
    val settings = settings ?: return
    if (kept == LinksView()) settings.remove(KEY)
    else settings.putString(KEY, json.encodeToString(kept))
  }

  private companion object {
    const val KEY = "links.view"
  }
}
