package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ConsoleRoom3DView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine
import java.util.Locale

private val RoomHudDark = Color(0xFF0A0D16)
private val RoomHudGlass = Color(0xCC0D111D)
private val RoomHudCream = Color(0xFFF3F6FF)
private val RoomHudMuted = Color(0xFFB3C0D9)
private val RoomRetroAmber = Color(0xFFFFB74D)

data class ConsoleRoomItem(
    val key: String,
    val title: String,
    val company: String,
    val gameCount: Int,
    val accentColor: Color,
)

@Composable
fun ConsoleRoomScreen(
    consoles: List<ConsoleRoomItem>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    onConsoleSelect: (ConsoleRoomItem) -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val safeSelectedIndex =
        if (consoles.isEmpty()) 0 else selectedIndex.coerceIn(0, consoles.lastIndex)
    val selectedConsole = consoles.getOrNull(safeSelectedIndex)
    val consoleKeys = remember(consoles) { consoles.map { it.key } }

    val activeAccent by animateColorAsState(
        targetValue = selectedConsole?.accentColor ?: Color(0xFF00C2FF),
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "consoleRoomAccent",
    )

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(RoomHudDark)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    val keyCode = nativeEvent.keyCode

                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_BUTTON_L1,
                                -> {
                                    if (consoles.isNotEmpty()) {
                                        val nextIndex =
                                            (safeSelectedIndex - 1 + consoles.size) % consoles.size
                                        FrontendSoundEngine.playNavigate(
                                            context,
                                            FrontendTheme.CONSOLE,
                                        )
                                        onIndexChange(nextIndex)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_BUTTON_R1,
                                -> {
                                    if (consoles.isNotEmpty()) {
                                        val nextIndex = (safeSelectedIndex + 1) % consoles.size
                                        FrontendSoundEngine.playNavigate(
                                            context,
                                            FrontendTheme.CONSOLE,
                                        )
                                        onIndexChange(nextIndex)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_BUTTON_SELECT,
                                KeyEvent.KEYCODE_BUTTON_START,
                                KeyEvent.KEYCODE_MENU,
                                -> true

                                else -> false
                            }
                        }

                        KeyEventType.KeyUp -> {
                            if (nativeEvent.isCanceled) {
                                false
                            } else {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_BUTTON_A,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                    -> {
                                        if (consoles.isNotEmpty()) {
                                            FrontendSoundEngine.playConfirm(
                                                context,
                                                FrontendTheme.CONSOLE,
                                            )
                                            onConsoleSelect(consoles[safeSelectedIndex])
                                        }
                                        true
                                    }

                                    KeyEvent.KEYCODE_BUTTON_SELECT -> {
                                        FrontendSoundEngine.playConfirm(
                                            context,
                                            FrontendTheme.CONSOLE,
                                        )
                                        onRescan()
                                        true
                                    }

                                    KeyEvent.KEYCODE_BUTTON_START,
                                    KeyEvent.KEYCODE_MENU,
                                    -> {
                                        FrontendSoundEngine.playConfirm(
                                            context,
                                            FrontendTheme.CONSOLE,
                                        )
                                        onOpenSettings()
                                        true
                                    }

                                    else -> false
                                }
                            }
                        }

                        else -> false
                    }
                },
    ) {
        // Full-screen 3D 80s/90s gamer bedroom with gliding camera
        ConsoleRoom3DView(
            consoleKeys = consoleKeys,
            selectedIndex = safeSelectedIndex,
            modifier = Modifier.fillMaxSize(),
        )

        // Subtle top & bottom gradient scrims to keep HUD legible without blocking the 3D scene
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xC8070910),
                                    Color(0x55070910),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color(0x70070910),
                                    Color(0xDF070910),
                                ),
                        ),
                    ),
        )

        // Top translucent banner + shelf progress dots
        ConsoleRoomTopBanner(
            totalConsoles = consoles.size,
            selectedIndex = safeSelectedIndex,
            accentColor = activeAccent,
            onSelectIndex = { idx ->
                if (idx != safeSelectedIndex) {
                    FrontendSoundEngine.playNavigate(context, FrontendTheme.CONSOLE)
                    onIndexChange(idx)
                }
            },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 36.dp, end = 36.dp),
        )

        // Empty shelf notice if no games/consoles found
        if (consoles.isEmpty()) {
            ConsoleRoomEmptyNotice(
                onRescan = onRescan,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Bottom floating console placard & controls legend
        ConsoleRoomBottomPlacard(
            console = selectedConsole,
            accentColor = activeAccent,
            onConfirm = {
                selectedConsole?.let {
                    FrontendSoundEngine.playConfirm(context, FrontendTheme.CONSOLE)
                    onConsoleSelect(it)
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp, start = 36.dp, end = 36.dp),
        )
    }
}

@Composable
private fun ConsoleRoomTopBanner(
    totalConsoles: Int,
    selectedIndex: Int,
    accentColor: Color,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = RoomHudGlass,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RoomRetroAmber),
                )
                Text(
                    text = "QUARTO GAMER 80/90",
                    color = RoomHudCream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                )
                if (totalConsoles > 0) {
                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "%02d / %02d",
                                selectedIndex + 1,
                                totalConsoles,
                            ),
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                    )
                }
            }

            if (totalConsoles > 1) {
                val maxVisibleDots = 16
                val visibleCount = totalConsoles.coerceAtMost(maxVisibleDots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until visibleCount) {
                        val mappedIndex =
                            if (totalConsoles <= maxVisibleDots) {
                                i
                            } else {
                                ((i.toFloat() / (visibleCount - 1)) * (totalConsoles - 1)).toInt()
                            }
                        val isSelected =
                            if (totalConsoles <= maxVisibleDots) {
                                i == selectedIndex
                            } else {
                                val normalized =
                                    ((selectedIndex.toFloat() / (totalConsoles - 1)) * (visibleCount - 1))
                                        .toInt()
                                i == normalized
                            }
                        val pillWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 7.dp,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            label = "shelfDotWidth",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .width(pillWidth)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (isSelected) {
                                            accentColor
                                        } else {
                                            RoomHudMuted.copy(alpha = 0.35f)
                                        },
                                    )
                                    .clickable { onSelectIndex(mappedIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleRoomBottomPlacard(
    console: ConsoleRoomItem?,
    accentColor: Color,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = console != null) { onConfirm() },
        shape = RoundedCornerShape(18.dp),
        color = RoomHudGlass,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (console != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val badgeColor = companyBadgeColor(console.company, accentColor)
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor.copy(alpha = 0.22f))
                                    .border(
                                        width = 1.dp,
                                        color = badgeColor.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = console.company.uppercase(Locale.ROOT),
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.6.sp,
                            )
                        }

                        Text(
                            text = console.title,
                            color = RoomHudCream,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val gameLabel =
                        if (console.gameCount == 1) {
                            "1 JOGO"
                        } else {
                            "${console.gameCount} JOGOS"
                        }
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(
                                    width = 1.dp,
                                    color = accentColor.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = gameLabel,
                            color = RoomHudCream,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    accentColor.copy(alpha = 0.45f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                    glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.DPAD_LR,
                    label = "Mover Câmera",
                    textColor = RoomHudCream,
                )
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                    glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.A,
                    label = "Ligar Console",
                    textColor = RoomHudCream,
                )
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                    glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.START,
                    label = "Opções",
                    textColor = RoomHudMuted,
                )
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                    glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.SELECT,
                    label = "Reescanear",
                    textColor = RoomHudMuted,
                )
            }
        }
    }
}

@Composable
private fun ControlHintPill(
    keyLabel: String,
    actionLabel: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = keyLabel,
                color = RoomHudCream,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = actionLabel,
            color = RoomHudMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ControlHintDot() {
    Text(
        text = "•",
        color = RoomHudMuted.copy(alpha = 0.5f),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 10.dp),
    )
}

@Composable
private fun ConsoleRoomEmptyNotice(
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable { onRescan() },
        shape = RoundedCornerShape(16.dp),
        color = RoomHudGlass,
        border = BorderStroke(1.5.dp, RoomRetroAmber.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "ESTANTE VAZIA",
                color = RoomRetroAmber,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Text(
                text = "Pressione SELECT para escanear sua coleção de ROMs.",
                color = RoomHudMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun companyBadgeColor(company: String, fallback: Color): Color {
    return when (company.uppercase(Locale.ROOT)) {
        "SONY" -> Color(0xFF3B82F6)
        "NINTENDO" -> Color(0xFFE60012)
        "SEGA" -> Color(0xFF00A8FF)
        "ARCADE" -> Color(0xFFFF9100)
        "ATARI" -> Color(0xFFFF7043)
        else -> fallback
    }
}
