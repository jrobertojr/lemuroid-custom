package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoverFallbackInterceptor
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 3D hardware showcase card for Lemuroid's `THREE_D` frontend theme.
 *
 * - When [isSelected] is `true`, renders a real OpenGL ES 2.0 solid 3D hardware object
 *   ([Hardware3DRenderer]: Cartridge, Jewel Case, or MiniFliper Arcade Cabinet) inside a
 *   single reusable [GLSurfaceView] with UV-mapped cover art and continuous 360° studio rotation.
 * - When [isSelected] is `false`, renders a lightweight preview baked from the same hardware mesh
 *   with `ContentScale.Fit` cover art and a hardware badge so scrolling grids stay smooth
 *   without spawning dozens of simultaneous GL contexts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Game3DObject(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val hardwareType = remember(game.systemId) { resolveHardware3DType(game.systemId) }

    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                OpenGLHardwareShowcase(
                    game = game,
                    hardwareType = hardwareType,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MediaThumbnail(
                    game = game,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = game.displayName(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (onFavoriteToggle == null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            if (onFavoriteToggle != null) {
                IconButton(
                    onClick = { onFavoriteToggle(!game.isFavorite) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector =
                            if (game.isFavorite) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                        contentDescription = stringResource(R.string.favorites),
                        tint =
                            if (game.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenGLHardwareShowcase(
    game: Game,
    hardwareType: Hardware3DType,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profile = rememberFrontend3DProfile()
    val coverSize = if (profile == PerformanceProfile.PERFORMANCE) 256 else 512

    // Single reusable renderer & GLSurfaceView — never keyed by game.id so EGL context is never recreated.
    val renderer =
        remember {
            Hardware3DRenderer(
                hardwareType = resolveHardware3DType(game.systemId),
                shellColor = resolveHardwareShellColor(game.systemId),
                spinning = true,
                systemId = game.systemId,
                context = context.applicationContext,
            )
        }

    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    // The selected medium keeps rotating in every profile, at the clock's bounded cadence.
    Bind3DFrameClock(glSurfaceViewRef, game.fileUri, animateInPerformance = true)

    LaunchedEffect(game.fileUri, game.displayCoverUrl(), coverSize) {
        // Keep the current object and cover until navigation settles and its replacement is ready.
        delay(140L)
        withContext(Dispatchers.IO) {
            val loaded = loadCoverBitmapForGL(context = context, game = game, targetSize = coverSize)
            try {
                if (isActive) {
                    renderer.updateHardware(
                        resolveHardware3DType(game.systemId),
                        resolveHardwareShellColor(game.systemId),
                        game.systemId,
                    )
                    renderer.setCoverBitmap(loaded?.bitmap)
                    glSurfaceViewRef?.requestRender()
                }
            } finally {
                if (loaded != null && loaded.recycleAfterCopy && !loaded.bitmap.isRecycled) {
                    loaded.bitmap.recycle()
                }
            }
        }
    }

    Bind3DSurfaceLifecycle(glSurfaceViewRef)

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(Color(0xFF0D0F17))
                .border(1.dp, Color(0xFF283248), shape),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    glSurfaceViewRef = this
                }
            },
            update = { view ->
                if (glSurfaceViewRef !== view) {
                    glSurfaceViewRef = view
                }
            },
            onRelease = { view ->
                view.onPause()
                renderer.release()
                if (glSurfaceViewRef === view) {
                    glSurfaceViewRef = null
                }
            },
        )

        HardwareBadgePill(
            systemId = game.systemId,
            hardwareType = hardwareType,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
        )
    }
}

private data class LoadedCoverBitmap(
    val bitmap: Bitmap,
    val recycleAfterCopy: Boolean,
)

private suspend fun loadCoverBitmapForGL(
    context: android.content.Context,
    game: Game,
    targetSize: Int,
): LoadedCoverBitmap? {
    val baseUrl = game.displayCoverUrl()
    val candidates =
        if (baseUrl != null && baseUrl.contains("/Named_Boxarts/")) {
            listOf(
                baseUrl,
                baseUrl.replace("/Named_Boxarts/", "/Named_Titles/"),
                baseUrl.replace("/Named_Boxarts/", "/Named_Snaps/"),
            )
        } else {
            listOfNotNull(baseUrl)
        }

    val encodedFileName = Uri.encode(game.fileName)
    for (url in candidates) {
        val request =
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(targetSize, targetSize)
                .setHeader(CoverFallbackInterceptor.HEADER_GAME_FILENAME, encodedFileName)
                .build()
        val result = try {
            context.imageLoader.execute(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (result is SuccessResult) {
            val drawable = result.drawable
            val cachedBmp = (drawable as? BitmapDrawable)?.bitmap
            if (cachedBmp != null && !cachedBmp.isRecycled) {
                return LoadedCoverBitmap(bitmap = cachedBmp, recycleAfterCopy = false)
            }
            val renderedBmp =
                runCatching { drawable.toBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888) }.getOrNull()
            if (renderedBmp != null && !renderedBmp.isRecycled) {
                return LoadedCoverBitmap(bitmap = renderedBmp, recycleAfterCopy = true)
            }
        }
    }

    val fallbackBmp =
        runCatching {
            CoverUtils.getFallbackDrawable(game).toBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    return if (fallbackBmp != null && !fallbackBmp.isRecycled) {
        LoadedCoverBitmap(bitmap = fallbackBmp, recycleAfterCopy = true)
    } else {
        null
    }
}

@Composable
private fun HardwareBadgePill(
    systemId: String,
    hardwareType: Hardware3DType,
    modifier: Modifier = Modifier,
) {
    val label =
        remember(systemId, hardwareType) {
            when (hardwareType) {
                Hardware3DType.MINI_ARCADE -> "ARCADE"
                Hardware3DType.JEWEL_CASE -> systemId.uppercase()
                Hardware3DType.CARTRIDGE -> systemId.uppercase()
            }
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC0B0E17))
                .border(0.75.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFFE6EEFF),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
