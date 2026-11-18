package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.common.compose.textUnit
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.controls.LemuroidControlButton
import com.swordfish.touchinput.radial.controls.LemuroidControlSteeringWheel
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonCoin
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonL1
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import com.swordfish.touchinput.radial.ui.LemuroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id

@Composable
fun PadKitScope.ArcadeRacingLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            LemuroidControlSteeringWheel(
                id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK),
            )
        },
        secondaryDials = {
            SecondaryButtonL1()
            SecondaryButtonCoin()
            SecondaryButtonMenuPlaceholder(settings)
        },
    )
}

@Composable
fun PadKitScope.ArcadeRacingRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.38f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GearButton(
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        id = Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                        label = "M-",
                    )
                    GearButton(
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        id = Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                        label = "M+",
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().weight(0.62f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PedalButton(
                        modifier = Modifier.weight(1f).fillMaxHeight(0.75f),
                        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                        label = "FREIO",
                        grooveCount = 3,
                    )
                    PedalButton(
                        modifier = Modifier.weight(0.85f).fillMaxHeight(1f),
                        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
                        label = "ACEL",
                        grooveCount = 5,
                    )
                }
            }
        },
        secondaryDials = {
            LemuroidControlButton(
                modifier = Modifier.radialPosition(90f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                label = "A",
            )
            LemuroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                label = "B",
            )
            SecondaryButtonStart(position = 2)
            SecondaryButtonMenu(settings)
        },
    )
}

context(PadKitScope)
@Composable
private fun GearButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String,
) {
    val theme = LocalLemuroidPadTheme.current
    ControlButton(
        modifier = modifier.padding(theme.padding),
        id = id,
        background = { LemuroidControlBackground() },
        foreground = { LemuroidButtonForeground(pressed = it, label = label, labelScale = 0.7f) },
    )
}

context(PadKitScope)
@Composable
private fun PedalButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String,
    grooveCount: Int,
) {
    val theme = LocalLemuroidPadTheme.current
    ControlButton(
        modifier = modifier,
        id = id,
        background = {
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 14.dp,
                fillColor = theme.level1Fill,
                shadowColor = theme.level1Shadow,
                shadowWidth = theme.level1ShadowWidth,
            )
        },
        foreground = { pressed ->
            val grooveColor = theme.level3Shadow
            GlassSurface(
                modifier = Modifier.fillMaxSize().padding(theme.foregroundPadding),
                cornerRadius = 12.dp,
                fillColor = theme.foregroundFill(pressed.value),
                shadowColor = theme.level3Shadow,
                shadowWidth = theme.level3ShadowWidth,
            ) {
                val pedalScope = this
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
                        val step = size.height / (grooveCount + 1)
                        for (i in 1..grooveCount) {
                            val y = step * i
                            drawLine(
                                color = grooveColor.copy(alpha = 0.35f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    Text(
                        text = label,
                        color = theme.icons(pressed.value),
                        fontSize = (minOf(pedalScope.maxWidth, pedalScope.maxHeight) * 0.24f).textUnit(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    )
}
