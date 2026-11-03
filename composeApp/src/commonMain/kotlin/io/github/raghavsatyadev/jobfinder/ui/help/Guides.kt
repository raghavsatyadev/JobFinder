package io.github.raghavsatyadev.jobfinder.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_collapse
import io.github.raghavsatyadev.jobfinder.resources.help_expand
import io.github.raghavsatyadev.jobfinder.resources.ic_expand_more_filled
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Illustrated steps for setting up the computer and connecting a phone. The guide for [device]
 * comes first; [openGuide] is the open one, or `null` for none. [shown] limits the guides to a
 * search's matches, which [expandAll] opens.
 */
@Composable
fun Guides(
  device: GuideDevice,
  actions: GuideActions,
  openGuide: GuideId?,
  onToggle: (GuideId) -> Unit,
  shown: List<GuideId> = guidesFor(device),
  expandAll: Boolean = false,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    shown.forEach { which ->
      GuideCard(
        guide(which),
        actions,
        expanded = expandAll || openGuide == which,
        onToggle = { onToggle(which) },
      )
    }
  }
}

@Composable
internal fun GuideCard(
  guide: Guide,
  actions: GuideActions,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  val animate = animationsEnabled()
  val motion = MaterialTheme.motionScheme
  val rotation by
    animateFloatAsState(
      if (expanded) 180f else 0f,
      if (animate) motion.fastSpatialSpec() else snap(),
      label = "guide-chevron",
    )
  val state = stringResource(if (expanded) Res.string.help_collapse else Res.string.help_expand)
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { stateDescription = state }
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        vectorResource(guide.icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        stringResource(guide.title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      Icon(
        vectorResource(Res.drawable.ic_expand_more_filled),
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = rotation },
      )
    }
    AnimatedVisibility(
      visible = expanded,
      enter =
        if (animate)
          expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        else expandVertically(snap()),
      exit =
        if (animate) shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        else shrinkVertically(snap()),
    ) {
      Column {
        guide.steps.forEachIndexed { index, step ->
          HorizontalDivider(Modifier.padding(horizontal = 16.dp))
          StepRow(index + 1, step, actionFor(step.action, actions))
        }
      }
    }
  }
}

internal fun actionFor(action: StepAction?, actions: GuideActions): GuideAction? =
  when (action) {
    StepAction.OPEN_SETUP -> actions.openSetup
    StepAction.ADD_COMPUTER -> actions.addComputer
    StepAction.CHECK_CONNECTION -> actions.checkConnection
    null -> null
  }

/** Picture beside the text when there is room, above it on a narrow phone. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun StepRow(number: Int, step: GuideStep, action: GuideAction?) {
  BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
    val wide = maxWidth >= 520.dp
    val text: @Composable () -> Unit = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          StepNumber(number)
          Text(stringResource(step.title), style = MaterialTheme.typography.titleSmall)
        }
        Text(
          stringResource(step.text),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
          FilledTonalButton(onClick = action.onClick, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(action.label))
          }
        }
      }
    }
    if (wide) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Illustration(step.scene, Modifier.width(220.dp))
        Box(Modifier.weight(1f)) { text() }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Illustration(step.scene, Modifier.fillMaxWidth())
        text()
      }
    }
  }
}

@Composable
internal fun StepNumber(number: Int) {
  Surface(
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shape = CircleShape,
    modifier = Modifier.size(28.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text("$number", style = MaterialTheme.typography.labelLarge)
    }
  }
}

/** The step's picture. Decorative: the text says the same. */
@Composable
internal fun Illustration(scene: Scene, modifier: Modifier) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = modifier.height(120.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Device(scene.first)
      if (scene.second != null) {
        Link(scene.link, Modifier.weight(1f, fill = false).width(72.dp))
        Device(scene.second)
      }
    }
  }
}

@Composable
internal fun Device(screen: Screen) {
  val outline = MaterialTheme.colorScheme.onSecondaryContainer
  val glass = MaterialTheme.colorScheme.surface
  when (screen.frame) {
    Frame.COMPUTER ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          color = glass,
          shape = RoundedCornerShape(6.dp),
          modifier =
            Modifier.size(width = 92.dp, height = 58.dp)
              .border(3.dp, outline, RoundedCornerShape(6.dp)),
        ) {
          Box(contentAlignment = Alignment.Center) { ScreenIcon(screen.icon) }
        }
        Surface(
          color = outline,
          shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
          modifier = Modifier.size(width = 112.dp, height = 7.dp),
        ) {}
      }
    Frame.PHONE ->
      Surface(
        color = glass,
        shape = RoundedCornerShape(10.dp),
        modifier =
          Modifier.size(width = 46.dp, height = 82.dp)
            .border(3.dp, outline, RoundedCornerShape(10.dp)),
      ) {
        Box(contentAlignment = Alignment.Center) { ScreenIcon(screen.icon) }
      }
  }
}

@Composable
internal fun ScreenIcon(icon: DrawableResource) {
  Icon(vectorResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun Link(icon: DrawableResource?, modifier: Modifier) {
  val color = MaterialTheme.colorScheme.onSecondaryContainer
  Box(modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
      // Dashes run outwards from the middle, so none is cut short behind the icon.
      val gap = if (icon != null) 20.dp.toPx() else 0f
      val y = size.height / 2
      val mid = size.width / 2
      val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
      for ((from, to) in listOf(mid - gap to 0f, mid + gap to size.width)) {
        drawLine(
          color,
          Offset(from, y),
          Offset(to, y),
          strokeWidth = size.height,
          cap = StrokeCap.Round,
          pathEffect = dashes,
        )
      }
    }
    if (icon != null) {
      Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
        Icon(
          vectorResource(icon),
          contentDescription = null,
          modifier = Modifier.padding(4.dp).size(20.dp),
        )
      }
    }
  }
}

/** Every guide as Markdown, steps numbered, for AI agents (the MCP server). */
internal suspend fun guidesMarkdown(): String = buildString {
  for (guide in listOf(COMPUTER_GUIDE, PHONE_GUIDE, BACKUP_GUIDE)) {
    append("### ").append(getString(guide.title)).append("\n\n")
    guide.steps.forEachIndexed { i, step ->
      append(i + 1).append(". **").append(getString(step.title)).append(".** ")
      append(getString(step.text)).append('\n')
    }
    append('\n')
  }
}
