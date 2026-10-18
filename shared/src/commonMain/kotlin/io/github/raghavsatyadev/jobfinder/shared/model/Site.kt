package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** A supported job board. Rows are seeded with the app; the user does not edit them. */
@Serializable
data class Site(
  val id: Long,
  val name: String,
  val provider: SiteProvider,
  /** Origins that identify the board, for example `https://www.linkedin.com`. */
  val urls: List<String>,
  /** Query parameters stripped from search URLs before they are saved. */
  val queryParamsToRemove: List<String> = emptyList(),
  /** URL paths that are not job searches (for example `/` or `/feed/`). */
  val blacklistedPaths: List<String> = emptyList(),
  /**
   * Bundled logo in `composeApp` resources, without the `site_` prefix and the extension:
   * `linkedin` is `drawable/site_linkedin.png`.
   */
  val logoKey: String,
  /** The scanner skips deprecated boards. */
  val deprecated: Boolean = false,
  /** Job descriptions can load in a cookie-less context (LinkedIn). */
  val incognitoSupport: Boolean = false,
  val createdAt: Long,
)
