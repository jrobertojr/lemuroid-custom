package com.swordfish.lemuroid.app.shared.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

enum class ConsoleBezelMode(val prefValue: String) {
    DISABLED("disabled"),
    AUTO("auto"),
    SUBTLE("subtle"),
    ;

    companion object {
        const val PREF_KEY = "pref_key_console_bezel_mode"

        fun fromPrefValue(value: String?): ConsoleBezelMode {
            return values().firstOrNull { it.prefValue == value } ?: AUTO
        }
    }
}

private data class ConsoleBezelStyle(
    val label: String,
    val subtitle: String,
    val baseDark: Color,
    val baseMid: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val nativeAspectRatio: Float,
    val pattern: BezelPattern,
    val remoteOverlayUrl: String?,
)

private enum class BezelPattern {
    SNES_STRIPES,
    PLAYSTATION_SYMBOLS,
    N64_QUAD,
    SEGA_GRID,
    NES_CLASSIC,
    GAMEBOY_SHELL,
    ARCADE_NEON,
    WOODGRAIN,
}

private const val LIBRETRO_BORDERS_BASE =
    "https://raw.githubusercontent.com/libretro/common-overlays/master/borders"

private fun resolveBezelStyle(systemId: String): ConsoleBezelStyle =
    when (systemId.lowercase()) {
        "snes" ->
            ConsoleBezelStyle(
                label = "SUPER NINTENDO",
                subtitle = "16-BIT ENTERTAINMENT SYSTEM",
                baseDark = Color(0xFF171622),
                baseMid = Color(0xFF28263A),
                accentPrimary = Color(0xFF6B5CE7),
                accentSecondary = Color(0xFFE63946),
                accentTertiary = Color(0xFFFFCA3A),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.SNES_STRIPES,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/snes.png",
            )
        "psx" ->
            ConsoleBezelStyle(
                label = "PLAYSTATION",
                subtitle = "SONY COMPUTER ENTERTAINMENT",
                baseDark = Color(0xFF12151C),
                baseMid = Color(0xFF202531),
                accentPrimary = Color(0xFF0070D1),
                accentSecondary = Color(0xFF00C896),
                accentTertiary = Color(0xFFFF3B6B),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.PLAYSTATION_SYMBOLS,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/psx.png",
            )
        "n64" ->
            ConsoleBezelStyle(
                label = "NINTENDO 64",
                subtitle = "64-BIT REALITY",
                baseDark = Color(0xFF121419),
                baseMid = Color(0xFF1F242D),
                accentPrimary = Color(0xFF1E88E5),
                accentSecondary = Color(0xFF43A047),
                accentTertiary = Color(0xFFE53935),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.N64_QUAD,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/n64.png",
            )
        "md", "genesis" ->
            ConsoleBezelStyle(
                label = "MEGA DRIVE",
                subtitle = "SEGA 16-BIT HIGH DEFINITION",
                baseDark = Color(0xFF0C1018),
                baseMid = Color(0xFF151E2E),
                accentPrimary = Color(0xFF0088FF),
                accentSecondary = Color(0xFF00E5FF),
                accentTertiary = Color(0xFFE53935),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.SEGA_GRID,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/genesis.png",
            )
        "sms" ->
            ConsoleBezelStyle(
                label = "MASTER SYSTEM",
                subtitle = "SEGA POWER BASE",
                baseDark = Color(0xFF121014),
                baseMid = Color(0xFF1E1A22),
                accentPrimary = Color(0xFFE53935),
                accentSecondary = Color(0xFF2979FF),
                accentTertiary = Color(0xFFE0E0E0),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.SEGA_GRID,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/sms.png",
            )
        "segacd" ->
            ConsoleBezelStyle(
                label = "SEGA CD",
                subtitle = "COMPACT DISC SYSTEM",
                baseDark = Color(0xFF0B111C),
                baseMid = Color(0xFF162238),
                accentPrimary = Color(0xFF00B0FF),
                accentSecondary = Color(0xFFFF3D00),
                accentTertiary = Color(0xFFB0BEC5),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.SEGA_GRID,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/segacd.png",
            )
        "nes" ->
            ConsoleBezelStyle(
                label = "NINTENDO",
                subtitle = "ENTERTAINMENT SYSTEM",
                baseDark = Color(0xFF151517),
                baseMid = Color(0xFF25252A),
                accentPrimary = Color(0xFFD50000),
                accentSecondary = Color(0xFFBDBDBD),
                accentTertiary = Color(0xFF424242),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.NES_CLASSIC,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/nes.png",
            )
        "gba" ->
            ConsoleBezelStyle(
                label = "GAME BOY ADVANCE",
                subtitle = "32-BIT PORTABLE",
                baseDark = Color(0xFF17132E),
                baseMid = Color(0xFF2B235A),
                accentPrimary = Color(0xFF7C4DFF),
                accentSecondary = Color(0xFFB388FF),
                accentTertiary = Color(0xFF00E5FF),
                nativeAspectRatio = 3f / 2f,
                pattern = BezelPattern.GAMEBOY_SHELL,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/gba.png",
            )
        "gb", "gbc" ->
            ConsoleBezelStyle(
                label = if (systemId.equals("gbc", ignoreCase = true)) "GAME BOY COLOR" else "GAME BOY",
                subtitle = "DOT MATRIX WITH STEREO SOUND",
                baseDark = Color(0xFF1A1C19),
                baseMid = Color(0xFF2D312C),
                accentPrimary = Color(0xFF8BC34A),
                accentSecondary = Color(0xFFE53935),
                accentTertiary = Color(0xFF7E57C2),
                nativeAspectRatio = 10f / 9f,
                pattern = BezelPattern.GAMEBOY_SHELL,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/${systemId.lowercase()}.png",
            )
        "gg" ->
            ConsoleBezelStyle(
                label = "GAME GEAR",
                subtitle = "SEGA PORTABLE COLOR",
                baseDark = Color(0xFF121318),
                baseMid = Color(0xFF20232C),
                accentPrimary = Color(0xFF2979FF),
                accentSecondary = Color(0xFF00E676),
                accentTertiary = Color(0xFFFF1744),
                nativeAspectRatio = 10f / 9f,
                pattern = BezelPattern.SEGA_GRID,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/gg.png",
            )
        "nds" ->
            ConsoleBezelStyle(
                label = "NINTENDO DS",
                subtitle = "DUAL SCREEN SYSTEM",
                baseDark = Color(0xFF13161D),
                baseMid = Color(0xFF232834),
                accentPrimary = Color(0xFF00C3E3),
                accentSecondary = Color(0xFFE60012),
                accentTertiary = Color(0xFFECEFF1),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.NES_CLASSIC,
                remoteOverlayUrl = null,
            )
        "fbneo", "mame2003plus", "arcade" ->
            ConsoleBezelStyle(
                label = "ARCADE",
                subtitle = "INSERT COIN • PLAYER 1",
                baseDark = Color(0xFF10081F),
                baseMid = Color(0xFF21103B),
                accentPrimary = Color(0xFFFF1F8E),
                accentSecondary = Color(0xFF00F0FF),
                accentTertiary = Color(0xFFFFC107),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.ARCADE_NEON,
                remoteOverlayUrl = "$LIBRETRO_BORDERS_BASE/1080p/arcade.png",
            )
        "psp" ->
            ConsoleBezelStyle(
                label = "PSP",
                subtitle = "PLAYSTATION PORTABLE",
                baseDark = Color(0xFF10131A),
                baseMid = Color(0xFF1B212E),
                accentPrimary = Color(0xFF0070D1),
                accentSecondary = Color(0xFF40C4FF),
                accentTertiary = Color(0xFFE0E0E0),
                nativeAspectRatio = 16f / 9f,
                pattern = BezelPattern.PLAYSTATION_SYMBOLS,
                remoteOverlayUrl = null,
            )
        else ->
            ConsoleBezelStyle(
                label = systemId.uppercase(),
                subtitle = "RETRO SYSTEM",
                baseDark = Color(0xFF1B120C),
                baseMid = Color(0xFF2F2015),
                accentPrimary = Color(0xFFFF9800),
                accentSecondary = Color(0xFFFFC107),
                accentTertiary = Color(0xFF8D6E63),
                nativeAspectRatio = 4f / 3f,
                pattern = BezelPattern.WOODGRAIN,
                remoteOverlayUrl = null,
            )
    }

/**
 * Lightweight, zero-FPS-impact console bezel overlay rendered strictly on the
 * left and right pillarbox bars outside the game's native aspect ratio.
 */
@Composable
fun ConsoleBezelOverlay(
    systemId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mode =
        remember {
            val prefs = SharedPreferencesHelper.getSharedPreferences(context)
            ConsoleBezelMode.fromPrefValue(
                prefs.getString(ConsoleBezelMode.PREF_KEY, ConsoleBezelMode.AUTO.prefValue),
            )
        }

    if (mode == ConsoleBezelMode.DISABLED) return

    val style = remember(systemId) { resolveBezelStyle(systemId) }
    val overlayAlpha = if (mode == ConsoleBezelMode.SUBTLE) 0.55f else 1.0f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        if (screenW <= screenH || screenH <= 0f) return@BoxWithConstraints

        val gameWidthPx = screenH * style.nativeAspectRatio
        val sideWidthPx = ((screenW - gameWidthPx) / 2f).coerceAtLeast(0f)
        val density = LocalDensity.current
        val sideWidthDp = with(density) { sideWidthPx.toDp() }

        // Skip if there are no meaningful sidebars (e.g. widescreen PSP or 4:3 display)
        if (sideWidthDp < 24.dp) return@BoxWithConstraints

        BezelSidePanel(
            style = style,
            isLeft = true,
            width = sideWidthDp,
            overlayAlpha = overlayAlpha,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        BezelSidePanel(
            style = style,
            isLeft = false,
            width = sideWidthDp,
            overlayAlpha = overlayAlpha,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun BezelSidePanel(
    style: ConsoleBezelStyle,
    isLeft: Boolean,
    width: Dp,
    overlayAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier =
            modifier
                .width(width)
                .fillMaxHeight()
                .alpha(overlayAlpha)
                .background(
                    Brush.horizontalGradient(
                        colors =
                            if (isLeft) {
                                listOf(style.baseDark, style.baseMid, Color(0xFF090A0D))
                            } else {
                                listOf(Color(0xFF090A0D), style.baseMid, style.baseDark)
                            },
                    ),
                ),
    ) {
        // Optional cached Libretro overlay texture (lightweight 360x720 decode, blends cleanly if online)
        style.remoteOverlayUrl?.let { url ->
            AsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(360, 720)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(0.32f),
            )
        }

        // Built-in console-authentic vector bezel art (works 100% offline with zero RAM/FPS cost)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (style.pattern) {
                BezelPattern.SNES_STRIPES -> {
                    val stripeX = if (isLeft) w * 0.24f else w * 0.76f
                    val colors = listOf(Color(0xFFE63946), Color(0xFFFFCA3A), Color(0xFF2EC4B6), Color(0xFF3A86FF))
                    colors.forEachIndexed { idx, c ->
                        val offset = (idx - 1.5f) * 9f
                        drawLine(
                            color = c.copy(alpha = 0.65f),
                            start = Offset(stripeX + offset, h * 0.08f),
                            end = Offset(stripeX + offset, h * 0.92f),
                            strokeWidth = 4.5f,
                        )
                    }
                }

                BezelPattern.PLAYSTATION_SYMBOLS -> {
                    val cx = w * 0.5f
                    val r = (w * 0.14f).coerceIn(10f, 22f)
                    val positions = listOf(0.22f, 0.36f, 0.64f, 0.78f)
                    // Triangle (green)
                    val triY = h * positions[0]
                    val tri =
                        Path().apply {
                            moveTo(cx, triY - r)
                            lineTo(cx + r, triY + r * 0.85f)
                            lineTo(cx - r, triY + r * 0.85f)
                            close()
                        }
                    drawPath(tri, color = Color(0xFF00E676).copy(alpha = 0.45f), style = Stroke(3f))
                    // Circle (red)
                    drawCircle(
                        color = Color(0xFFFF3B6B).copy(alpha = 0.45f),
                        radius = r,
                        center = Offset(cx, h * positions[1]),
                        style = Stroke(3f),
                    )
                    // Cross (blue)
                    val xY = h * positions[2]
                    drawLine(
                        color = Color(0xFF2979FF).copy(alpha = 0.50f),
                        start = Offset(cx - r * 0.8f, xY - r * 0.8f),
                        end = Offset(cx + r * 0.8f, xY + r * 0.8f),
                        strokeWidth = 3f,
                    )
                    drawLine(
                        color = Color(0xFF2979FF).copy(alpha = 0.50f),
                        start = Offset(cx + r * 0.8f, xY - r * 0.8f),
                        end = Offset(cx - r * 0.8f, xY + r * 0.8f),
                        strokeWidth = 3f,
                    )
                    // Square (pink)
                    val sqY = h * positions[3]
                    drawRect(
                        color = Color(0xFFE040FB).copy(alpha = 0.45f),
                        topLeft = Offset(cx - r * 0.8f, sqY - r * 0.8f),
                        size = Size(r * 1.6f, r * 1.6f),
                        style = Stroke(3f),
                    )
                }

                BezelPattern.N64_QUAD -> {
                    val colors = listOf(Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFFCA28), Color(0xFFE53935))
                    val barW = (w * 0.08f).coerceAtLeast(5f)
                    colors.forEachIndexed { idx, c ->
                        val x = if (isLeft) w * 0.16f + idx * (barW + 4f) else w * 0.84f - (idx + 1) * (barW + 4f)
                        drawRect(
                            color = c.copy(alpha = 0.52f),
                            topLeft = Offset(x, h * 0.12f),
                            size = Size(barW, h * 0.76f),
                        )
                    }
                }

                BezelPattern.SEGA_GRID -> {
                    val step = 28f
                    var x = 0f
                    while (x <= w) {
                        drawLine(
                            color = style.accentPrimary.copy(alpha = 0.16f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f,
                        )
                        x += step
                    }
                    var y = 0f
                    while (y <= h) {
                        drawLine(
                            color = style.accentPrimary.copy(alpha = 0.16f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                        )
                        y += step
                    }
                }

                BezelPattern.NES_CLASSIC -> {
                    val stripeX = if (isLeft) w * 0.25f else w * 0.75f
                    drawRect(
                        color = style.accentPrimary.copy(alpha = 0.75f),
                        topLeft = Offset(stripeX - 5f, 0f),
                        size = Size(10f, h),
                    )
                    drawRect(
                        color = style.accentSecondary.copy(alpha = 0.28f),
                        topLeft = Offset(stripeX + 12f, 0f),
                        size = Size(4f, h),
                    )
                }

                BezelPattern.GAMEBOY_SHELL -> {
                    // Subtle handheld battery LED on left bezel
                    if (isLeft) {
                        drawCircle(
                            color = Color(0xFFFF1744),
                            radius = 6f,
                            center = Offset(w * 0.5f, h * 0.22f),
                        )
                        drawCircle(
                            color = Color(0xFFFF1744).copy(alpha = 0.30f),
                            radius = 14f,
                            center = Offset(w * 0.5f, h * 0.22f),
                        )
                    } else {
                        // Subtle speaker grille dots on right bezel
                        for (row in 0..4) {
                            for (col in 0..2) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    radius = 3.5f,
                                    center = Offset(w * 0.36f + col * 14f, h * 0.70f + row * 14f),
                                )
                            }
                        }
                    }
                }

                BezelPattern.ARCADE_NEON -> {
                    val edgeX = if (isLeft) w - 10f else 10f
                    drawLine(
                        color = style.accentPrimary.copy(alpha = 0.75f),
                        start = Offset(edgeX, 0f),
                        end = Offset(edgeX, h),
                        strokeWidth = 4f,
                    )
                    drawLine(
                        color = style.accentSecondary.copy(alpha = 0.55f),
                        start = Offset(if (isLeft) edgeX - 10f else edgeX + 10f, h * 0.08f),
                        end = Offset(if (isLeft) edgeX - 10f else edgeX + 10f, h * 0.92f),
                        strokeWidth = 2.5f,
                    )
                }

                BezelPattern.WOODGRAIN -> {
                    for (i in 0..10) {
                        val x = w * (i / 10f)
                        drawLine(
                            color = style.accentPrimary.copy(alpha = 0.09f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 3f,
                        )
                    }
                }
            }

            // Crisp dark physical inner CRT bezel border framing the game screen
            val innerEdgeX = if (isLeft) w - 3f else 3f
            drawLine(
                color = style.accentPrimary.copy(alpha = 0.45f),
                start = Offset(innerEdgeX, 0f),
                end = Offset(innerEdgeX, h),
                strokeWidth = 2.5f,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.85f),
                start = Offset(if (isLeft) w - 1f else 1f, 0f),
                end = Offset(if (isLeft) w - 1f else 1f, h),
                strokeWidth = 2f,
            )
        }

        // Rotated console nameplate label along the outer edge
        Text(
            text = style.label,
            color = Color.White.copy(alpha = 0.26f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            maxLines = 1,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        rotationZ = if (isLeft) -90f else 90f
                    },
        )
    }
}
