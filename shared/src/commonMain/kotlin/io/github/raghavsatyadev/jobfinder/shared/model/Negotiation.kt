package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How much room the candidate has to negotiate. */
@Serializable
enum class Leverage {
  @SerialName("low") LOW,
  @SerialName("medium") MEDIUM,
  @SerialName("high") HIGH,
}

/** AI tips for negotiating one job's offer, from its salary data, reviews, age and ATS match. */
@Serializable
data class NegotiationTips(
  val leverage: Leverage,
  val summary: String,
  val tips: List<String>,
  val createdAt: Long,
)

@Serializable
enum class TipsState {
  /** None written yet; they are written only when asked. */
  @SerialName("none") NONE,
  /** Waiting for the AI model; a `TipsReady` event follows. */
  @SerialName("queued") QUEUED,
  @SerialName("ready") READY,
  /** The model gave no usable answer; asking again retries. */
  @SerialName("failed") FAILED,
  /** Only the computer can write them. */
  @SerialName("unavailable") UNAVAILABLE,
}

@Serializable data class TipsStatus(val state: TipsState, val tips: NegotiationTips? = null)
