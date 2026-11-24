package com.swordfish.lemuroid.app.shared.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph

/** Shared hierarchy without extra imagery, clocks, blur or background work. */
@Composable
fun HubDashboardHeader(
    visuals: HubThemeVisuals,
    title: String,
    subtitle: String,
    activeModule: HubModule?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VIDEOGAME",
                color = visuals.accent,
                fontSize = if (compact) 8.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                maxLines = 1,
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = if (compact) 16.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFFBEC8D6),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onBack != null) {
            val shape = RoundedCornerShape(10.dp)
            Row(
                modifier =
                    Modifier
                        .background(Color(0xD0161C28), shape)
                        .border(1.dp, visuals.cardBorder, shape)
                        .clickable(onClick = onBack)
                        .padding(horizontal = if (compact) 5.dp else 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GamepadButtonIcon(glyph = GamepadGlyph.B, modifier = Modifier.size(18.dp))
                if (!compact) Text("Central", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
