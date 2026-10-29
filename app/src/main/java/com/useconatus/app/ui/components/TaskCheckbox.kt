package com.useconatus.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.priorityColor
import kotlinx.coroutines.launch

/**
 * The web's task checkbox: a 20dp square with 6dp corners and a 2dp border in the
 * priority colour, in a 48dp touch target. Checking it fills it, pops it and sends a ring
 * outwards with the web's timings. Without [onToggle] it only shows the priority and stays out
 * of TalkBack's way, since the row already reads the priority out.
 */
@Composable
fun TaskCheckbox(priority: Int, checked: Boolean, taskTitle: String, onToggle: ((Boolean) -> Unit)? = null) {
    val color = priorityColor(priority, LocalConatusColors.current.dark)
    val pop = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(checked) {
        if (!checked) {
            ring.snapTo(0f)
            return@LaunchedEffect
        }
        launch { pop.animateTo(1f, keyframes { durationMillis = 320; 1.16f at 134; 0.96f at 230 }) }
        ring.animateTo(1f, tween(360))
    }
    val haptics = LocalHapticFeedback.current
    val target = if (onToggle != null) {
        Modifier
            .toggleable(checked, role = Role.Checkbox) { on ->
                if (on) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onToggle(on)
            }
            .semantics { contentDescription = "Complete $taskTitle"; stateDescription = if (checked) "Completed" else "Open" }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(Modifier.size(48.dp).then(target), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(20.dp)
                .drawBehind {
                    val p = ring.value
                    if (p > 0f && p < 1f) {
                        val grow = (0.72f + 0.73f * p) * (1f + 12.dp.toPx() / size.width)
                        drawRoundRect(
                            color.copy(alpha = 0.5f * (1f - p)),
                            topLeft = Offset(size.width * (1 - grow) / 2, size.height * (1 - grow) / 2),
                            size = size * grow,
                            cornerRadius = CornerRadius(9.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                }
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .background(if (checked) color else Color.Transparent, RoundedCornerShape(6.dp))
                .border(2.dp, color, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White) }
    }
}
