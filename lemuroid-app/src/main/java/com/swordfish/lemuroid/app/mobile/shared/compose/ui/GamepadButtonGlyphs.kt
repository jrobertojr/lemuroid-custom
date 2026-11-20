package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard console-dashboard gamepad button glyphs (inspired by Xbox / Switch / PlayStation / Steam Big Picture).
 * Replaces raw bracketed text like "[A]" or "[START]" with crisp physical controller button icons.
 */
enum class GamepadGlyph {
    A,
    B,
    X,
    Y,
    DPAD,
    DPAD_LR,
    DPAD_UD,
    L1,
    R1,
    L1_R1,
    L2,
    R2,
    START,
    SELECT,
}

@Composable
fun GamepadButtonIcon(
    glyph: GamepadGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.White,
) {
    when (glyph) {
        GamepadGlyph.A ->
            FaceButtonCircle(
                label = "A",
                fillTop = Color(0xFF34D058),
                fillBottom = Color(0xFF1F8A3B),
                borderColor = Color(0xFF9BF5B0),
                size = size,
                modifier = modifier,
            )

        GamepadGlyph.B ->
            FaceButtonCircle(
                label = "B",
                fillTop = Color(0xFFFF5252),
                fillBottom = Color(0xFFC62828),
                borderColor = Color(0xFFFFB4B4),
                size = size,
                modifier = modifier,
            )

        GamepadGlyph.X ->
            FaceButtonCircle(
                label = "X",
                fillTop = Color(0xFF409EFF),
                fillBottom = Color(0xFF1B5EBE),
                borderColor = Color(0xFFA6D4FF),
                size = size,
                modifier = modifier,
            )

        GamepadGlyph.Y ->
            FaceButtonCircle(
                label = "Y",
                fillTop = Color(0xFFFFCA28),
                fillBottom = Color(0xFFC79100),
                borderColor = Color(0xFFFFE99A),
                textColor = Color(0xFF1A1400),
                size = size,
                modifier = modifier,
            )

        GamepadGlyph.DPAD,
        GamepadGlyph.DPAD_LR,
        GamepadGlyph.DPAD_UD,
        ->
            DpadGlyphIcon(
                highlightHorizontal = glyph == GamepadGlyph.DPAD || glyph == GamepadGlyph.DPAD_LR,
                highlightVertical = glyph == GamepadGlyph.DPAD || glyph == GamepadGlyph.DPAD_UD,
                size = size,
                tint = tint,
                modifier = modifier,
            )

        GamepadGlyph.L1 ->
            BumperPillGlyph(label = "L1", height = size, modifier = modifier)

        GamepadGlyph.R1 ->
            BumperPillGlyph(label = "R1", height = size, modifier = modifier)

        GamepadGlyph.L1_R1 ->
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                BumperPillGlyph(label = "L1", height = size)
                BumperPillGlyph(label = "R1", height = size)
            }

        GamepadGlyph.L2 ->
            BumperPillGlyph(label = "L2", height = size, isTrigger = true, modifier = modifier)

        GamepadGlyph.R2 ->
            BumperPillGlyph(label = "R2", height = size, isTrigger = true, modifier = modifier)

        GamepadGlyph.START ->
            MenuSystemGlyph(isStart = true, height = size, modifier = modifier)

        GamepadGlyph.SELECT ->
            MenuSystemGlyph(isStart = false, height = size, modifier = modifier)
    }
}

@Composable
private fun FaceButtonCircle(
    label: String,
    fillTop: Color,
    fillBottom: Color,
    borderColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    val fontSp = (size.value * 0.56f).sp
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
                .border(1.2.dp, borderColor.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSp,
            lineHeight = fontSp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style =
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
        )
    }
}

@Composable
private fun DpadGlyphIcon(
    highlightHorizontal: Boolean,
    highlightVertical: Boolean,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val arm = w * 0.34f
        val offset = (w - arm) * 0.5f
        val cr = CornerRadius(w * 0.10f, w * 0.10f)

        val baseFill = Color(0xFF2B313C)
        val hiFill = tint.copy(alpha = 0.95f)
        val dimFill = tint.copy(alpha = 0.40f)

        // Vertical bar
        drawRoundRect(
            color = if (highlightVertical) hiFill else baseFill,
            topLeft = Offset(offset, w * 0.06f),
            size = Size(arm, h * 0.88f),
            cornerRadius = cr,
        )
        // Horizontal bar
        drawRoundRect(
            color = if (highlightHorizontal) hiFill else baseFill,
            topLeft = Offset(w * 0.06f, offset),
            size = Size(w * 0.88f, arm),
            cornerRadius = cr,
        )
        // Center pivot dimple
        drawCircle(
            color = Color(0xFF141820),
            radius = arm * 0.24f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
        // Crisp outline
        drawRoundRect(
            color = dimFill,
            topLeft = Offset(offset, w * 0.06f),
            size = Size(arm, h * 0.88f),
            cornerRadius = cr,
            style = Stroke(width = 1.2f),
        )
        drawRoundRect(
            color = dimFill,
            topLeft = Offset(w * 0.06f, offset),
            size = Size(w * 0.88f, arm),
            cornerRadius = cr,
            style = Stroke(width = 1.2f),
        )
    }
}

@Composable
private fun BumperPillGlyph(
    label: String,
    height: Dp,
    modifier: Modifier = Modifier,
    isTrigger: Boolean = false,
) {
    val shape =
        if (isTrigger) {
            RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
        } else {
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        }
    val fontSp = (height.value * 0.48f).sp
    Box(
        modifier =
            modifier
                .height(height)
                .widthIn(min = height * 1.45f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF475060), Color(0xFF232833))))
                .border(1.1.dp, Color(0xFFD0D8E8).copy(alpha = 0.72f), shape)
                .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSp,
            lineHeight = fontSp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style =
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
        )
    }
}

@Composable
private fun MenuSystemGlyph(
    isStart: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A4150), Color(0xFF1F242E))))
                .border(1.1.dp, Color(0xFFCFD8E6).copy(alpha = 0.75f), shape)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(modifier = Modifier.size(height * 0.50f)) {
            val w = size.width
            val h = size.height
            if (isStart) {
                // Classic 3-line Menu/Start icon
                val stroke = (h * 0.16f).coerceAtLeast(1.4f)
                drawLine(Color.White, Offset(0f, h * 0.2f), Offset(w, h * 0.2f), stroke)
                drawLine(Color.White, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), stroke)
                drawLine(Color.White, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), stroke)
            } else {
                // Classic dual-window View/Select icon
                val stroke = (h * 0.14f).coerceAtLeast(1.3f)
                drawRect(
                    color = Color.White,
                    topLeft = Offset(0f, h * 0.12f),
                    size = Size(w * 0.62f, h * 0.56f),
                    style = Stroke(stroke),
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.36f, h * 0.34f),
                    size = Size(w * 0.62f, h * 0.56f),
                    style = Stroke(stroke),
                )
            }
        }
        val menuFontSp = (height.value * 0.44f).sp
        Text(
            text = if (isStart) "START" else "SELECT",
            color = Color.White,
            fontSize = menuFontSp,
            lineHeight = menuFontSp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style =
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
        )
    }
}

@Composable
fun GamepadHintPill(
    glyph: GamepadGlyph,
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFE5EAF2),
    iconSize: Dp = 18.dp,
    fontSizeSp: Float = 11.5f,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamepadButtonIcon(glyph = glyph, size = iconSize, tint = textColor)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GamepadHintBar(
    hints: List<Pair<GamepadGlyph, String>>,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFE5EAF2),
    containerColor: Color = Color(0xAA0C1018),
    borderColor: Color = Color.White.copy(alpha = 0.16f),
    iconSize: Dp = 18.dp,
    fontSizeSp: Float = 11.5f,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        hints.forEach { (glyph, label) ->
            GamepadHintPill(
                glyph = glyph,
                label = label,
                textColor = textColor,
                iconSize = iconSize,
                fontSizeSp = fontSizeSp,
            )
        }
    }
}
