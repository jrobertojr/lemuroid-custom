package com.swordfish.lemuroid.app.shared.covers

import android.net.Uri
import android.util.LruCache
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber

object CoverFallbackInterceptor : Interceptor {
    private val notFoundCache = LruCache<String, Boolean>(512)
    private val PARENS_OR_BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val DISC_SUFFIX = Regex("\\s+(?:CD|Disc|Disk)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
    private val VERSION_SUFFIX = Regex("\\s+v\\d+(?:\\.\\d+)*$", RegexOption.IGNORE_CASE)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()

        val initialResponse = chain.proceed(originalRequest)
        if (initialResponse.isSuccessful || !originalUrl.contains("thumbnails.libretro.com")) {
            return initialResponse
        }

        if (notFoundCache[originalUrl] == true) {
            return initialResponse
        }

        initialResponse.close()

        val libretroAlternates =
            listOf(
                originalUrl.replace("/Named_Boxarts/", "/Named_Titles/"),
                originalUrl.replace("/Named_Boxarts/", "/Named_Snaps/"),
            ).distinct().filter { it != originalUrl }

        for (altUrl in libretroAlternates) {
            val altResponse = tryProceed(chain, originalRequest, altUrl)
            if (altResponse != null) {
                return altResponse
            }
        }

        val cleanTitle = extractCleanTitle(originalUrl)
        if (cleanTitle.isNotBlank()) {
            val wikiQueries =
                listOf(
                    "en.wikipedia.org" to "$cleanTitle video game",
                    "en.wikipedia.org" to cleanTitle,
                    "pt.wikipedia.org" to "$cleanTitle jogo",
                    "pt.wikipedia.org" to cleanTitle,
                )

            for ((host, query) in wikiQueries) {
                val thumbUrl = findWikipediaThumbnail(chain, host, query)
                if (thumbUrl != null) {
                    val imgResponse = tryProceed(chain, originalRequest, thumbUrl)
                    if (imgResponse != null) {
                        return imgResponse
                    }
                }
            }
        }

        notFoundCache.put(originalUrl, true)
        return chain.proceed(originalRequest)
    }

    private fun tryProceed(
        chain: Interceptor.Chain,
        template: Request,
        url: String,
    ): Response? {
        return try {
            val req = template.newBuilder().url(url).build()
            val res = chain.proceed(req)
            if (res.isSuccessful) {
                res
            } else {
                res.close()
                null
            }
        } catch (e: Exception) {
            Timber.d(e, "Fallback cover request failed for $url")
            null
        }
    }

    private fun extractCleanTitle(libretroUrl: String): String {
        val fileName = Uri.decode(libretroUrl.substringAfterLast('/'))
        return fileName
            .removeSuffix(".png")
            .replace('_', ' ')
            .replace(PARENS_OR_BRACKETS, "")
            .replace(DISC_SUFFIX, "")
            .replace(VERSION_SUFFIX, "")
            .trim(' ', '-', '_')
    }

    private fun findWikipediaThumbnail(
        chain: Interceptor.Chain,
        host: String,
        query: String,
    ): String? {
        return try {
            val encoded = Uri.encode(query)
            val apiUrl =
                "https://$host/w/api.php?action=query&generator=search" +
                    "&gsrsearch=$encoded&gsrlimit=3&prop=pageimages" +
                    "&piprop=thumbnail&pithumbsize=500&format=json"

            val req =
                Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "LemuroidCustom/1.0")
                    .build()

            chain.proceed(req).use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                var bestIndex = Int.MAX_VALUE
                var bestUrl: String? = null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val idx = page.optInt("index", Int.MAX_VALUE)
                    val thumb = page.optJSONObject("thumbnail")?.optString("source")
                    if (!thumb.isNullOrBlank() && idx < bestIndex) {
                        bestIndex = idx
                        bestUrl = thumb
                    }
                }
                bestUrl
            }
        } catch (e: Exception) {
            Timber.d(e, "Wikipedia thumbnail lookup failed for $query")
            null
        }
    }
}
