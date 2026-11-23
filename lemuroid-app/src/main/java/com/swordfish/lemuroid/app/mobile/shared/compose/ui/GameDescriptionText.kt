package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/** Activity-scoped input: never intercepts the left stick or runs a loop while idle. */
interface DescriptionScrollHost {
    val descriptionScroll: DescriptionScrollInput
}

class DescriptionScrollInput {
    internal val velocity = MutableStateFlow(0f)
    internal val panels = mutableSetOf<ScrollState>()

    fun stop() {
        velocity.value = 0f
    }

    fun dispatch(event: MotionEvent): Boolean {
        if (!event.isFromSource(
                InputDevice.SOURCE_JOYSTICK,
            ) || event.actionMasked != MotionEvent.ACTION_MOVE || panels.isEmpty()
        ) {
            return false
        }
        val device = event.device
        val axis =
            when {
                device?.getMotionRange(MotionEvent.AXIS_RZ, event.source) != null -> MotionEvent.AXIS_RZ
                device?.getMotionRange(MotionEvent.AXIS_RY, event.source) != null -> MotionEvent.AXIS_RY
                else -> MotionEvent.AXIS_RZ
            }
        val raw = event.getAxisValue(axis)
        val deadZone = maxOf(0.18f, device?.getMotionRange(axis, event.source)?.flat ?: 0f).coerceAtMost(0.95f)
        val previous = velocity.value
        velocity.value =
            if (abs(
                    raw,
                ) <= deadZone
            ) {
                0f
            } else {
                (abs(raw) - deadZone) / (1f - deadZone) * if (raw < 0f) -1f else 1f
            }
        return velocity.value != 0f || previous != 0f
    }
}

private tailrec fun Context.descriptionInput(): DescriptionScrollInput? =
    when (this) {
        is DescriptionScrollHost -> descriptionScroll
        is ContextWrapper -> baseContext.descriptionInput()
        else -> null
    }

/** Full text in a bounded viewport; callers reserve space for their fixed actions. */
@Composable
fun GameDescriptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    style: TextStyle = LocalTextStyle.current,
    visibleLines: Int = 4,
) {
    val input = LocalContext.current.descriptionInput()
    val state = rememberScrollState()
    LaunchedEffect(text) {
        input?.stop()
        state.scrollTo(0)
    }
    val line =
        when {
            lineHeight != TextUnit.Unspecified -> lineHeight
            style.lineHeight != TextUnit.Unspecified -> style.lineHeight
            fontSize != TextUnit.Unspecified -> (fontSize.value * 1.4f).sp
            else -> 20.sp
        }
    val height = with(LocalDensity.current) { line.toDp() * visibleLines }
    val pixelsPerSecond = with(LocalDensity.current) { 240f * density }
    DisposableEffect(input, state) {
        input?.panels?.add(state)
        onDispose {
            input?.panels?.remove(state)
            input?.stop()
        }
    }
    LaunchedEffect(input, state, pixelsPerSecond) {
        input?.velocity?.map { it != 0f }?.distinctUntilChanged()?.collectLatest { moving ->
            if (moving) {
                var previous = System.nanoTime()
                while (true) {
                    delay(16)
                    val now = System.nanoTime()
                    val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                    previous = now
                    state.scrollBy((input.velocity.value) * pixelsPerSecond * seconds)
                }
            }
        }
    }
    Text(
        text = text,
        modifier = modifier.heightIn(max = height).verticalScroll(state),
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        style = style,
    )
}
