package com.swordfish.touchinput.radial.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.ui.LemuroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlAnalog
import gg.padkit.ids.Id

context(PadKitScope)
@Composable
fun LemuroidControlSteeringWheel(
    modifier: Modifier = Modifier,
    id: Id.ContinuousDirection,
) {
    val theme = LocalLemuroidPadTheme.current
    var targetAngle by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    val wheelRotation by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(stiffness = 650f),
        label = "SteeringWheelRotation",
    )

    Box(
        modifier =
            modifier
                .padding(theme.padding)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val active = event.changes.firstOrNull { it.pressed }
                            if (active != null && size.width > 0) {
                                isPressed = true
                                val centerX = size.width / 2f
                                val normalized = ((active.position.x - centerX) / (size.width * 0.35f)).coerceIn(-1f, 1f)
                                targetAngle = normalized * 120f
                            } else {
                                isPressed = false
                                targetAngle = 0f
                            }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // ControlAnalog handles PadKit multi-touch & motion events without moving the wheel visual
        ControlAnalog(
            id = id,
            background = { },
            foreground = { },
        )

        LemuroidControlBackground(Modifier.fillMaxSize(0.88f))

        val rimColor = theme.foregroundFill(isPressed)
        val accentColor = theme.level3Shadow
        val hubColor = theme.foregroundFill(!isPressed)

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize(0.84f)
                    .graphicsLayer { rotationZ = wheelRotation },
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val rimStroke = radius * 0.21f
            val rimRadius = radius - rimStroke / 2f

            // Outer wheel shadow & rim
            drawCircle(
                color = accentColor,
                radius = rimRadius,
                center = center,
                style = Stroke(width = rimStroke + 4f),
            )
            drawCircle(
                color = rimColor,
                radius = rimRadius,
                center = center,
                style = Stroke(width = rimStroke),
            )

            // Top center alignment marker
            drawLine(
                color = hubColor,
                start = Offset(center.x, center.y - rimRadius - rimStroke * 0.35f),
                end = Offset(center.x, center.y - rimRadius + rimStroke * 0.35f),
                strokeWidth = rimStroke * 0.36f,
                cap = StrokeCap.Round,
            )

            // Three classic arcade steering wheel spokes
            val spokeWidth = rimStroke * 0.72f
            drawLine(
                color = rimColor,
                start = Offset(center.x - rimRadius * 0.85f, center.y),
                end = Offset(center.x + rimRadius * 0.85f, center.y),
                strokeWidth = spokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = rimColor,
                start = center,
                end = Offset(center.x, center.y + rimRadius * 0.85f),
                strokeWidth = spokeWidth,
                cap = StrokeCap.Round,
            )

            // Center hub
            drawCircle(
                color = accentColor,
                radius = radius * 0.25f,
                center = center,
            )
            drawCircle(
                color = hubColor,
                radius = radius * 0.20f,
                center = center,
            )
        }
    }
}
