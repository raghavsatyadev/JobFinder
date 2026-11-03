package io.github.raghavsatyadev.jobfinder.ui.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_export
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_export_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_keep
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_keep_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_pair
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_pair_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_phone
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_phone_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_restore
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_restore_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_backup_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_ai
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_ai_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_install
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_install_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_network
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_network_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_search
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_search_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_sign_in
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_sign_in_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_computer_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_allow
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_allow_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_jobs
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_jobs_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_pick
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_pick_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_title
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_wifi
import io.github.raghavsatyadev.jobfinder.resources.help_guide_phone_wifi_title
import io.github.raghavsatyadev.jobfinder.resources.ic_auto_awesome_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_check_circle_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_computer_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_link_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_lock_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_login_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_notifications_active_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_pin_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_restore_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_security_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_settings_backup_restore_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_smartphone_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_touch_app_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_wifi_filled
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** The device the app runs on, which decides the order of the guides. */
enum class GuideDevice {
  COMPUTER,
  PHONE,
}

/** The illustrated guides; the open one is kept by its id. */
enum class GuideId {
  COMPUTER,
  PHONE,
  BACKUP,
}

/** A step's button, shown only on the device where it works. */
@Immutable data class GuideAction(val label: StringResource, val onClick: () -> Unit)

/** The buttons the guides offer; each is `null` where that device can't do it. */
@Immutable
data class GuideActions(
  val openSetup: GuideAction? = null,
  val addComputer: GuideAction? = null,
  val checkConnection: GuideAction? = null,
)

internal enum class Frame {
  COMPUTER,
  PHONE,
}

/** A drawn device with an icon on its screen. */
@Immutable internal data class Screen(val frame: Frame, val icon: DrawableResource)

/** One or two devices; with two, a dashed line joins them, with [link] in the middle. */
@Immutable
internal data class Scene(
  val first: Screen,
  val second: Screen? = null,
  val link: DrawableResource? = null,
)

internal enum class StepAction {
  OPEN_SETUP,
  ADD_COMPUTER,
  CHECK_CONNECTION,
}

@Immutable
internal data class GuideStep(
  val title: StringResource,
  val text: StringResource,
  val scene: Scene,
  val action: StepAction? = null,
)

@Immutable
internal data class Guide(
  val id: GuideId,
  val icon: DrawableResource,
  val title: StringResource,
  val steps: ImmutableList<GuideStep>,
)

internal val COMPUTER_GUIDE =
  Guide(
    GuideId.COMPUTER,
    Res.drawable.ic_computer_filled,
    Res.string.help_guide_computer_title,
    persistentListOf(
      GuideStep(
        Res.string.help_guide_computer_install_title,
        Res.string.help_guide_computer_install,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_check_circle_filled)),
      ),
      GuideStep(
        Res.string.help_guide_computer_network_title,
        Res.string.help_guide_computer_network,
        Scene(
          Screen(Frame.COMPUTER, Res.drawable.ic_security_filled),
          Screen(Frame.PHONE, Res.drawable.ic_check_circle_filled),
          Res.drawable.ic_wifi_filled,
        ),
        StepAction.OPEN_SETUP,
      ),
      GuideStep(
        Res.string.help_guide_computer_ai_title,
        Res.string.help_guide_computer_ai,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_auto_awesome_filled)),
        StepAction.OPEN_SETUP,
      ),
      GuideStep(
        Res.string.help_guide_computer_search_title,
        Res.string.help_guide_computer_search,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_link_filled)),
      ),
      GuideStep(
        Res.string.help_guide_computer_sign_in_title,
        Res.string.help_guide_computer_sign_in,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_login_filled)),
        StepAction.OPEN_SETUP,
      ),
    ),
  )

internal val PHONE_GUIDE =
  Guide(
    GuideId.PHONE,
    Res.drawable.ic_smartphone_filled,
    Res.string.help_guide_phone_title,
    persistentListOf(
      GuideStep(
        Res.string.help_guide_phone_wifi_title,
        Res.string.help_guide_phone_wifi,
        Scene(
          Screen(Frame.COMPUTER, Res.drawable.ic_wifi_filled),
          Screen(Frame.PHONE, Res.drawable.ic_wifi_filled),
          Res.drawable.ic_wifi_filled,
        ),
        StepAction.CHECK_CONNECTION,
      ),
      GuideStep(
        Res.string.help_guide_phone_pick_title,
        Res.string.help_guide_phone_pick,
        Scene(Screen(Frame.PHONE, Res.drawable.ic_touch_app_filled)),
        StepAction.ADD_COMPUTER,
      ),
      GuideStep(
        Res.string.help_guide_phone_allow_title,
        Res.string.help_guide_phone_allow,
        Scene(
          Screen(Frame.PHONE, Res.drawable.ic_pin_filled),
          Screen(Frame.COMPUTER, Res.drawable.ic_pin_filled),
          Res.drawable.ic_check_circle_filled,
        ),
      ),
      GuideStep(
        Res.string.help_guide_phone_jobs_title,
        Res.string.help_guide_phone_jobs,
        Scene(Screen(Frame.PHONE, Res.drawable.ic_notifications_active_filled)),
      ),
    ),
  )

internal val BACKUP_GUIDE =
  Guide(
    GuideId.BACKUP,
    Res.drawable.ic_settings_backup_restore_filled,
    Res.string.help_guide_backup_title,
    persistentListOf(
      GuideStep(
        Res.string.help_guide_backup_export_title,
        Res.string.help_guide_backup_export,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_settings_backup_restore_filled)),
      ),
      GuideStep(
        Res.string.help_guide_backup_keep_title,
        Res.string.help_guide_backup_keep,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_lock_filled)),
      ),
      GuideStep(
        Res.string.help_guide_backup_restore_title,
        Res.string.help_guide_backup_restore,
        Scene(Screen(Frame.COMPUTER, Res.drawable.ic_restore_filled)),
        StepAction.OPEN_SETUP,
      ),
      GuideStep(
        Res.string.help_guide_backup_pair_title,
        Res.string.help_guide_backup_pair,
        Scene(
          Screen(Frame.PHONE, Res.drawable.ic_pin_filled),
          Screen(Frame.COMPUTER, Res.drawable.ic_pin_filled),
          Res.drawable.ic_check_circle_filled,
        ),
        StepAction.ADD_COMPUTER,
      ),
      GuideStep(
        Res.string.help_guide_backup_phone_title,
        Res.string.help_guide_backup_phone,
        Scene(
          Screen(Frame.PHONE, Res.drawable.ic_settings_backup_restore_filled),
          Screen(Frame.COMPUTER, Res.drawable.ic_check_circle_filled),
          Res.drawable.ic_wifi_filled,
        ),
      ),
    ),
  )

/** The guides in order for [device]: its own first, then the other device's, then backups. */
internal fun guidesFor(device: GuideDevice): List<GuideId> =
  if (device == GuideDevice.COMPUTER) listOf(GuideId.COMPUTER, GuideId.PHONE, GuideId.BACKUP)
  else listOf(GuideId.PHONE, GuideId.COMPUTER, GuideId.BACKUP)

/** The guide [device] opens first. */
internal fun ownGuide(device: GuideDevice): GuideId =
  if (device == GuideDevice.COMPUTER) GuideId.COMPUTER else GuideId.PHONE

internal fun guide(id: GuideId): Guide =
  when (id) {
    GuideId.COMPUTER -> COMPUTER_GUIDE
    GuideId.PHONE -> PHONE_GUIDE
    GuideId.BACKUP -> BACKUP_GUIDE
  }

/** The guides for [device] whose title or steps contain [query]; all of them for a blank one. */
@Composable
internal fun matchingGuides(device: GuideDevice, query: String): List<GuideId> =
  guidesFor(device).filter { id ->
    val guide = guide(id)
    helpMatches(query, guide.title) ||
      guide.steps.any { helpMatches(query, it.title) || helpMatches(query, it.text) }
  }
