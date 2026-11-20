package com.swordfish.lemuroid.app.tv.shared

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Provides derived artwork URLs and cached Wikipedia/metadata descriptions for Android TV layouts.
 */
object TVGameMetadataHelper {
    private const val NAMED_BOXARTS = "/Named_Boxarts/"
    private const val NAMED_SNAPS = "/Named_Snaps/"
    private const val NAMED_TITLES = "/Named_Titles/"
    private const val USER_AGENT = "LemuroidCustom/1.0"
    private const val TIMEOUT_SECONDS = 3L
    private const val MAX_EXTRACT_CHARS = 380
    private const val MIN_EXTRACT_CHARS = 45
    private const val CACHE_SIZE = 200

    private val TAG_REGEX = Regex("""\(([^)]+)\)|\[([^\]]+)]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val descriptionCache = LruCache<Int, String>(CACHE_SIZE)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Returns the Libretro screenshot URL derived from [Game.coverFrontUrl], or `null` if unavailable. */
    fun screenshotUrlFor(game: Game): String? =
        replaceBoxartPath(game.coverFrontUrl, NAMED_SNAPS)

    /** Returns the Libretro title-screen URL derived from [Game.coverFrontUrl], or `null` if unavailable. */
    fun titleScreenUrlFor(game: Game): String? =
        replaceBoxartPath(game.coverFrontUrl, NAMED_TITLES)

    /**
     * Builds a deduplicated list of available image URLs in priority order for the TV screenshot
     * slideshow: gameplay snapshot, title screen, and finally the game's display cover fallback.
     */
    fun screenshotGalleryUrlsFor(game: Game): List<String> =
        buildList {
            screenshotUrlFor(game)?.takeIf { it.isNotBlank() }?.let(::add)
            titleScreenUrlFor(game)?.takeIf { it.isNotBlank() }?.let(::add)
            game.displayCoverUrl()?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()

    /**
     * Fetches and caches a formatted description combining system/developer/region metadata with
     * a Wikipedia summary (Portuguese first, falling back to English).
     */
    suspend fun fetchGameDescription(
        context: Context,
        game: Game,
    ): String {
        descriptionCache.get(game.id)?.let { return it }

        val baseHeader = buildBaseHeader(context, game)
        val cleanTitle = cleanGameTitle(game.title)

        val extract =
            if (cleanTitle.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    fetchWikipediaExtractBySearch("pt", "$cleanTitle jogo")
                        ?: fetchWikipediaExtractBySearch("en", "$cleanTitle video game")
                }
            } else {
                null
            }

        currentCoroutineContext().ensureActive()

        val description =
            when {
                extract != null && baseHeader.isNotEmpty() -> "$baseHeader\n$extract"
                extract != null -> extract
                baseHeader.isNotEmpty() -> baseHeader
                else -> game.displayName()
            }

        descriptionCache.put(game.id, description)
        return description
    }

    private fun replaceBoxartPath(
        url: String?,
        replacement: String,
    ): String? {
        if (url == null || !url.contains(NAMED_BOXARTS)) return null
        return url.replace(NAMED_BOXARTS, replacement)
    }

    private fun buildBaseHeader(
        context: Context,
        game: Game,
    ): String {
        val parts = mutableListOf<String>()

        val system =
            try {
                GameSystem.findById(game.systemId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        system?.let {
            val systemLabel =
                context.getString(it.titleResId)
                    .ifBlank { context.getString(it.shortTitleResId) }
                    .trim()
            if (systemLabel.isNotEmpty()) {
                parts.add(systemLabel)
            }
        }

        game.developer?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }

        val tags = extractTitleTags(game.title).take(3)
        if (tags.isNotEmpty()) {
            parts.add(tags.joinToString(", "))
        }

        return parts.joinToString(" • ")
    }

    private fun extractTitleTags(title: String): List<String> =
        TAG_REGEX.findAll(title)
            .mapNotNull { match ->
                (match.groupValues[1].ifEmpty { match.groupValues[2] })
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
            .distinct()
            .toList()

    private fun cleanGameTitle(title: String): String =
        TAG_REGEX.replace(title, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private suspend fun fetchWikipediaExtractBySearch(
        lang: String,
        searchQuery: String,
    ): String? {
        val exactTitle = searchWikipediaExactTitle(lang, searchQuery) ?: return null
        return fetchWikipediaSummary(lang, exactTitle)
    }

    private suspend fun searchWikipediaExactTitle(
        lang: String,
        searchQuery: String,
    ): String? {
        currentCoroutineContext().ensureActive()
        val encodedQuery = Uri.encode(searchQuery)
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&srlimit=1&format=json"
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

        val call = httpClient.newCall(request)
        return try {
            call.execute().use { response ->
                currentCoroutineContext().ensureActive()
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val firstResult =
                    JSONObject(body)
                        .optJSONObject("query")
                        ?.optJSONArray("search")
                        ?.optJSONObject(0)
                        ?: return null
                firstResult.optString("title", "").trim().takeIf { it.isNotEmpty() }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private suspend fun fetchWikipediaSummary(
        lang: String,
        exactTitle: String,
    ): String? {
        currentCoroutineContext().ensureActive()
        val encodedTitle = Uri.encode(exactTitle)
        val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

        val call = httpClient.newCall(request)
        return try {
            call.execute().use { response ->
                currentCoroutineContext().ensureActive()
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("type", "") == "disambiguation") return null
                val extract =
                    json
                        .optString("extract", "")
                        .replace(WHITESPACE_REGEX, " ")
                        .trim()
                if (extract.length < MIN_EXTRACT_CHARS) null else capExtract(extract, MAX_EXTRACT_CHARS)
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private fun capExtract(
        text: String,
        maxLength: Int,
    ): String {
        if (text.length <= maxLength) return text

        val slice = text.take(maxLength)
        val sentenceEnd = maxOf(slice.lastIndexOf('.'), slice.lastIndexOf('!'), slice.lastIndexOf('?'))
        if (sentenceEnd >= maxLength / 2) {
            return slice.substring(0, sentenceEnd + 1).trimEnd()
        }

        val wordEnd = slice.lastIndexOf(' ')
        val cutIndex = if (wordEnd > 0) wordEnd else maxLength
        return slice.substring(0, cutIndex).trimEnd() + "..."
    }
}
