package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A section of the job detail the user can move or hide. */
@Serializable
enum class DetailSection {
  @SerialName("labels") LABELS,
  @SerialName("ats") ATS,
  @SerialName("description") DESCRIPTION,
  /** The company's ratings and salary. */
  @SerialName("company") COMPANY,
  @SerialName("ghost") GHOST,
  @SerialName("tips") TIPS,
  @SerialName("notes") NOTES,
}

/**
 * The job detail's sections in the user's order, and the ones hidden. Kept on the computer; the
 * phone follows it. A section the app adds later goes at the end, shown.
 */
@Serializable
data class DetailLayout(
  val order: List<DetailSection> = DEFAULT_ORDER,
  val hidden: Set<DetailSection> = emptySet(),
) {
  /** Every section once, in the user's order, new ones last. */
  fun normalized(): DetailLayout =
    copy(order = order.distinct() + DetailSection.entries.filter { it !in order })

  /** The sections to show, in order. */
  val shown: List<DetailSection>
    get() = normalized().order.filter { it !in hidden }

  companion object {
    val DEFAULT_ORDER: List<DetailSection> =
      listOf(
        DetailSection.LABELS,
        DetailSection.ATS,
        DetailSection.DESCRIPTION,
        DetailSection.COMPANY,
        DetailSection.GHOST,
        DetailSection.TIPS,
        DetailSection.NOTES,
      )

    val DEFAULT = DetailLayout()
  }
}
