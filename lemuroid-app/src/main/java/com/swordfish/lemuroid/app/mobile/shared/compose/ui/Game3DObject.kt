package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * - When [isSelected] is `false`, renders a lightweight high-polish beveled 3D card preview
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
    val shellColorRgba = remember(game.systemId) { resolveHardwareShellColor(game.systemId) }
    val shellColor =
        remember(shellColorRgba) {
            Color(
                red = shellColorRgba[0],
                green = shellColorRgba[1],
                blue = shellColorRgba[2],
                alpha = shellColorRgba[3],
            )
        }

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
                Beveled3DHardwareThumbnail(
                    game = game,
                    hardwareType = hardwareType,
                    shellColor = shellColor,
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
    val lifecycleOwner = LocalLifecycleOwner.current
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
    Bind3DFrameClock(glSurfaceViewRef, game.fileUri)

    LaunchedEffect(profile) {
        renderer.setSpinning(profile != PerformanceProfile.PERFORMANCE)
        glSurfaceViewRef?.requestRender()
    }

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

    DisposableEffect(lifecycleOwner, glSurfaceViewRef) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                    Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
        }
    }

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
private fun Beveled3DHardwareThumbnail(
    game: Game,
    hardwareType: Hardware3DType,
    shellColor: Color,
    modifier: Modifier = Modifier,
) {
    val sideBevelColor =
        remember(hardwareType, shellColor) {
            when (hardwareType) {
                Hardware3DType.JEWEL_CASE -> Color(0xFFB5C0D2)
                Hardware3DType.MINI_ARCADE -> Color(0xFF151924)
                Hardware3DType.CARTRIDGE -> shellColor.darken(0.70f)
            }
        }
    val bottomBevelColor =
        remember(hardwareType, shellColor) {
            when (hardwareType) {
                Hardware3DType.JEWEL_CASE -> Color(0xFF707A8C)
                Hardware3DType.MINI_ARCADE -> Color(0xFF0A0C12)
                Hardware3DType.CARTRIDGE -> shellColor.darken(0.50f)
            }
        }
    val trimAccent =
        remember(hardwareType, game.systemId) {
            when (hardwareType) {
                Hardware3DType.MINI_ARCADE -> Color(0xFFE53935)
                Hardware3DType.JEWEL_CASE ->
                    when (game.systemId) {
                        "psx" -> Color(0xFF2962FF)
                        "psp" -> Color(0xFF00B0FF)
                        "3ds" -> Color(0xFFD50000)
                        "gc" -> Color(0xFF7C4DFF)
                        else -> Color(0xFF607D8B)
                    }
                Hardware3DType.CARTRIDGE -> shellColor.lighten(1.18f)
            }
        }

    val cardShape =
        when (hardwareType) {
            Hardware3DType.JEWEL_CASE -> RoundedCornerShape(6.dp)
            Hardware3DType.MINI_ARCADE -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
            Hardware3DType.CARTRIDGE -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        }

    val rightPath = remember { Path() }
    val bottomPath = remember { Path() }

    Box(modifier = modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)) {
        // Soft floor shadow + thick isometric 3D right/bottom bevel
        Canvas(modifier = Modifier.fillMaxSize()) {
            val depthX = 9.dp.toPx()
            val depthY = 8.dp.toPx()
            val faceW = size.width - depthX
            val faceH = size.height - depthY

            // Ambient drop shadow
            drawOval(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(size.width * 0.52f, size.height - 2.dp.toPx()),
                        radius = size.width * 0.50f,
                    ),
                topLeft = Offset(size.width * 0.06f, size.height - 9.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width * 0.88f, 10.dp.toPx()),
            )

            // Right 3D extruded bevel face
            rightPath.reset()
            rightPath.moveTo(faceW, 4.dp.toPx())
            rightPath.lineTo(size.width, 4.dp.toPx() + depthY * 0.72f)
            rightPath.lineTo(size.width, size.height - 2.dp.toPx())
            rightPath.lineTo(faceW, faceH)
            rightPath.close()
            drawPath(path = rightPath, color = sideBevelColor)

            // Bottom 3D extruded bevel face
            bottomPath.reset()
            bottomPath.moveTo(4.dp.toPx(), faceH)
            bottomPath.lineTo(faceW, faceH)
            bottomPath.lineTo(size.width, size.height - 2.dp.toPx())
            bottomPath.lineTo(4.dp.toPx() + depthX * 0.85f, size.height - 2.dp.toPx())
            bottomPath.close()
            drawPath(path = bottomPath, color = bottomBevelColor)

            // Mini arcade T-molding side stripe
            if (hardwareType == Hardware3DType.MINI_ARCADE) {
                drawLine(
                    color = trimAccent.copy(alpha = 0.9f),
                    start = Offset(size.width - 1.5.dp.toPx(), 10.dp.toPx()),
                    end = Offset(size.width - 1.5.dp.toPx(), size.height - 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        // Front hardware face
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(end = 9.dp, bottom = 8.dp)
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                when (hardwareType) {
                                    Hardware3DType.JEWEL_CASE -> listOf(Color(0xFF1A1F2C), Color(0xFF0E1118))
                                    Hardware3DType.MINI_ARCADE -> listOf(Color(0xFF282E40), Color(0xFF141824))
                                    Hardware3DType.CARTRIDGE -> listOf(shellColor.lighten(1.12f), shellColor.darken(0.86f))
                                },
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), cardShape),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top hardware accent header (marquee / jewel lip / cartridge grip)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (hardwareType == Hardware3DType.MINI_ARCADE) 10.dp else 6.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            trimAccent.copy(alpha = 0.85f),
                                            trimAccent.copy(alpha = 0.35f),
                                        ),
                                ),
                            ),
                )

                // Recessed cover art container with ContentScale.Fit
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(
                                horizontal = if (hardwareType == Hardware3DType.CARTRIDGE) 7.dp else 5.dp,
                                vertical = 5.dp,
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0C0F16))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
                ) {
                    LemuroidGameImage(
                        game = game,
                        contentScale = ContentScale.Fit,
                        preserveAspect = true,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Subtle diagonal studio specular sheen
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors =
                                            listOf(
                                                Color.White.copy(alpha = 0.16f),
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.05f),
                                            ),
                                        start = Offset.Zero,
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                                    ),
                                ),
                    )
                }
            }

            HardwareBadgePill(
                systemId = game.systemId,
                hardwareType = hardwareType,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 6.dp),
            )
        }
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

private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

private fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
