package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.swordfish.lemuroid.R

/** Stable keys stay independent from the original, generated artwork bundled with each theme. */
@DrawableRes
fun FrontendTheme.artworkResource(): Int =
    when (this) {
        FrontendTheme.DEFAULT -> R.drawable.theme_art_essential
        FrontendTheme.LOCADORA -> R.drawable.theme_art_locadora
        FrontendTheme.NETFLIX -> R.drawable.theme_art_cinema
        FrontendTheme.CONSOLE -> R.drawable.theme_art_console
        FrontendTheme.TV -> R.drawable.theme_art_crt
        FrontendTheme.RETRO_80_90 -> R.drawable.theme_art_synthwave
        FrontendTheme.ROCKSTAR -> R.drawable.hard_rock_backstage
        FrontendTheme.THRASH_METAL -> R.drawable.theme_art_thrash
        FrontendTheme.THREE_D -> R.drawable.theme_art_showcase
    }

/** Static, decoded only by the visible composition; no slideshow or per-frame bitmap work. */
@Composable
fun Modifier.themeArtwork(theme: FrontendTheme): Modifier =
    clipToBounds().background(Color(0xFF08090E)).paint(
        painterResource(theme.artworkResource()),
        sizeToIntrinsics = false,
        contentScale = ContentScale.Crop,
        alpha = 0.78f,
    )

@Composable
fun GeneratedThemeBackdrop(
    theme: FrontendTheme,
    modifier: Modifier = Modifier,
) {
    Box(modifier.themeArtwork(theme))
}
