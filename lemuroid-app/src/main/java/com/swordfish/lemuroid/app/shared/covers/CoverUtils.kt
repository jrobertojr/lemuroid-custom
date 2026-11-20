package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import android.net.Uri
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.swordfish.lemuroid.common.drawable.TextDrawable
import com.swordfish.lemuroid.common.graphics.ColorUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

object CoverUtils {
    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        imageView.load(game.displayCoverUrl(), imageView.context.imageLoader) {
            crossfade(shouldCrossfade(imageView.context))
            val fallbackDrawable = getFallbackDrawable(game)
            setHeader(CoverFallbackInterceptor.HEADER_GAME_FILENAME, Uri.encode(game.fileName))
            fallback(fallbackDrawable)
            error(fallbackDrawable)
        }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        val isTV = (applicationContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val capMemory = isTV || Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        return ImageLoader.Builder(applicationContext)
            .diskCache(
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.20)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .apply {
                        if (capMemory) {
                            // A bounded cover cache leaves headroom for EGL textures and emulation.
                            maxSizeBytes(minOf(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5).toInt())
                        } else {
                            maxSizePercent(0.20)
                        }
                    }
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(CoverFallbackInterceptor)
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .build()
            }
            .crossfade(shouldCrossfade(applicationContext))
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }

    fun shouldCrossfade(context: Context): Boolean =
        PerformanceProfileManager.getActiveProfile(context) != PerformanceProfile.PERFORMANCE

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.displayName()
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.displayName().first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }
}
