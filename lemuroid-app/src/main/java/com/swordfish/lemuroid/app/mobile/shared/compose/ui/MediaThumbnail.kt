package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Same mesh as the live showcase, baked once. A scrolling row never creates extra GL contexts. */
@Composable
internal fun MediaThumbnail(game: Game, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = remember(game.systemId) {
        when (game.systemId) {
            "fbneo", "mame2003plus" -> "arcade"
            "scd" -> "segacd"
            "genesis" -> "md"
            else -> game.systemId
        }
    }
    val spec = MediaPreviewGeometry.bySystem[key]
    if (spec == null) {
        LemuroidGameImage(game = game, modifier = modifier, contentScale = ContentScale.Fit, preserveAspect = true)
        return
    }
    val labelShape = remember(spec.ring) {
        GenericShape { size, _ ->
            if (spec.ring) {
                fillType = PathFillType.EvenOdd
                addOval(Rect(0f, 0f, size.width, size.height))
                val inner = 0.10f / 0.66f
                addOval(Rect(size.width * (1f - inner) / 2f, size.height * (1f - inner) / 2f,
                    size.width * (1f + inner) / 2f, size.height * (1f + inner) / 2f))
            } else {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
        }
    }
    val request = remember(context, key) {
        ImageRequest.Builder(context).data("file:///android_asset/media_previews/$key.png")
            .size(256).crossfade(false).build()
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                preserveAspect = true,
                modifier = Modifier.offset(x = maxWidth * spec.left, y = maxHeight * spec.top)
                    .size(maxWidth * (spec.right - spec.left), maxHeight * (spec.bottom - spec.top))
                    .clip(labelShape).background(Color(0xFF15171B)),
            )
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        }
    }
}
