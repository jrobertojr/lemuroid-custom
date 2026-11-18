package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.app.shared.covers.CoverFallbackInterceptor
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun LemuroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    val baseUrl = game.displayCoverUrl()
    val candidates =
        remember(baseUrl) {
            if (baseUrl != null && baseUrl.contains("/Named_Boxarts/")) {
                listOf(
                    baseUrl,
                    baseUrl.replace("/Named_Boxarts/", "/Named_Titles/"),
                    baseUrl.replace("/Named_Boxarts/", "/Named_Snaps/"),
                )
            } else {
                listOfNotNull(baseUrl)
            }
        }
    var attemptIndex by remember(baseUrl) { mutableIntStateOf(0) }
    val currentUrl = candidates.getOrNull(attemptIndex)

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(currentUrl)
                .setHeader(CoverFallbackInterceptor.HEADER_GAME_FILENAME, Uri.encode(game.fileName))
                .build(),
        contentDescription = game.displayName(),
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1.0f),
        fallback = fallbackPainter,
        error = fallbackPainter,
        onError = {
            if (attemptIndex < candidates.lastIndex) {
                attemptIndex++
            }
        },
        contentScale = ContentScale.Crop,
    )
}
