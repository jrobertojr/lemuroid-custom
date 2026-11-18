package com.swordfish.lemuroid.app.shared.covers

import android.net.Uri
import android.util.LruCache
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CoverFallbackInterceptor : Interceptor {
    const val HEADER_GAME_FILENAME = "X-Lemuroid-Game-Filename"

    private data class BoxartCandidate(
        val fileName: String,
        val tokens: Set<String>,
        val collapsed: String,
        val regionBonus: Int,
        val penalty: Int,
    )

    private val notFoundCache = LruCache<String, Boolean>(512)
    private val systemBoxartsCache = ConcurrentHashMap<String, List<BoxartCandidate>>()
    private val systemFetchLocks = ConcurrentHashMap<String, Any>()

    private val PARENS_OR_BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val DISC_SUFFIX = Regex("\\s+(?:CD|Disc|Disk)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
    private val VERSION_SUFFIX = Regex("\\s+v\\d+(?:\\.\\d+)*$", RegexOption.IGNORE_CASE)
    private val LEADING_ARTICLE = Regex("^(The|A|An)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val TRAILING_ARTICLE = Regex("^(.+),\\s*(The|A|An)$", RegexOption.IGNORE_CASE)
    private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
    private val FALLBACK_REGIONS = listOf("USA", "World", "Europe", "Japan", "Europe, Brazil")
    private val STOP_WORDS = setOf("the", "and", "for", "of", "a", "an")
    private val TOKEN_NORMALIZATIONS =
        mapOf(
            "ii" to "2",
            "iii" to "3",
            "iv" to "4",
            "vi" to "6",
            "vii" to "7",
            "viii" to "8",
            "ix" to "9",
            "bros" to "brothers",
            "vs" to "versus",
        )
    private val PENALTY_TAGS =
        listOf("(Beta", "(Proto", "(Demo", "(Sample", "(Aftermarket", "(Unl)", "(Pirate", "(Virtual Console", "(Switch Online")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()
        val rawGameFileName = originalRequest.header(HEADER_GAME_FILENAME)
        val gameFileName = rawGameFileName?.let { Uri.decode(it) }
        val cleanRequest =
            if (rawGameFileName != null) {
                originalRequest.newBuilder().removeHeader(HEADER_GAME_FILENAME).build()
            } else {
                originalRequest
            }

        val initialResponse = chain.proceed(cleanRequest)
        if (initialResponse.isSuccessful || !originalUrl.contains("thumbnails.libretro.com")) {
            return initialResponse
        }

        val cacheKey = if (gameFileName.isNullOrBlank()) originalUrl else "$originalUrl|$gameFileName"
        if (notFoundCache[cacheKey] == true) {
            return initialResponse
        }

        initialResponse.close()

        val libretroAlternates =
            listOf(
                originalUrl.replace("/Named_Boxarts/", "/Named_Titles/"),
                originalUrl.replace("/Named_Boxarts/", "/Named_Snaps/"),
            ).distinct().filter { it != originalUrl }

        for (altUrl in libretroAlternates) {
            val altResponse = tryProceed(chain, cleanRequest, altUrl)
            if (altResponse != null) {
                return altResponse
            }
        }

        val cleanTitle = extractCleanTitle(originalUrl)
        if (cleanTitle.isNotBlank()) {
            val wikiResponse = findWikipediaCover(chain, cleanRequest, cleanTitle)
            if (wikiResponse != null) {
                return wikiResponse
            }
        }

        if (!gameFileName.isNullOrBlank() && originalUrl.contains("/Named_Boxarts/")) {
            val filenameResponse = tryFilenameFallback(chain, cleanRequest, originalUrl, gameFileName, cleanTitle)
            if (filenameResponse != null) {
                return filenameResponse
            }
        }

        if (originalUrl.contains("/Named_Boxarts/")) {
            val fileCleanTitle = gameFileName?.let { cleanTitle(it.substringBeforeLast('.')) }.orEmpty()
            val indexResponse = tryLibretroIndexFallback(chain, cleanRequest, originalUrl, fileCleanTitle, cleanTitle)
            if (indexResponse != null) {
                return indexResponse
            }

            if (fileCleanTitle.isNotBlank() && !fileCleanTitle.equals(cleanTitle, ignoreCase = true)) {
                val wikiFromFile = findWikipediaCover(chain, cleanRequest, fileCleanTitle)
                if (wikiFromFile != null) {
                    return wikiFromFile
                }
            }
        }

        notFoundCache.put(cacheKey, true)
        return chain.proceed(cleanRequest)
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

    private fun findWikipediaCover(
        chain: Interceptor.Chain,
        template: Request,
        cleanTitle: String,
    ): Response? {
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
                val imgResponse = tryProceed(chain, template, thumbUrl)
                if (imgResponse != null) {
                    return imgResponse
                }
            }
        }
        return null
    }

    private fun tryFilenameFallback(
        chain: Interceptor.Chain,
        template: Request,
        originalUrl: String,
        gameFileName: String,
        urlCleanTitle: String,
    ): Response? {
        val basePrefix = originalUrl.substringBeforeLast('/') + "/"
        val fileStem = gameFileName.substringBeforeLast('.').trim()
        if (fileStem.isBlank()) return null

        val candidateNames = linkedSetOf<String>()
        candidateNames.add(sanitizeThumbName(fileStem))
        candidateNames.add(fileStem.replace(" & ", "-").let(::sanitizeThumbName))

        val fileCleanTitle = cleanTitle(fileStem)
        if (fileCleanTitle.isNotBlank()) {
            val titleForms = buildTitleVariants(fileCleanTitle)
            for (form in titleForms) {
                val sanitized = sanitizeThumbName(form)
                for (region in FALLBACK_REGIONS) {
                    candidateNames.add("$sanitized ($region)")
                }
                candidateNames.add(sanitized)
            }
        }

        val candidateUrls =
            candidateNames
                .map { "$basePrefix${Uri.encode(it)}.png" }
                .filter { it != originalUrl }

        for (candidateUrl in candidateUrls) {
            val res = tryProceed(chain, template, candidateUrl)
            if (res != null) {
                return res
            }
        }

        return null
    }

    private fun buildTitleVariants(title: String): List<String> {
        val variants = linkedSetOf<String>()
        variants.add(title)
        variants.add(title.replace(" & ", "-"))
        variants.add(title.replace(": ", " - "))

        LEADING_ARTICLE.matchEntire(title)?.let { m ->
            variants.add("${m.groupValues[2]}, ${m.groupValues[1]}")
        }
        TRAILING_ARTICLE.matchEntire(title)?.let { m ->
            variants.add("${m.groupValues[2]} ${m.groupValues[1]}")
        }

        return variants.toList()
    }

    private fun tryLibretroIndexFallback(
        chain: Interceptor.Chain,
        template: Request,
        originalUrl: String,
        fileCleanTitle: String,
        urlCleanTitle: String,
    ): Response? {
        val systemSegment =
            originalUrl
                .substringAfter("thumbnails.libretro.com/", "")
                .substringBefore("/Named_Boxarts/", "")
        if (systemSegment.isBlank()) return null

        val systemName = Uri.decode(systemSegment)
        val repoName = systemName.replace(' ', '_')
        val candidates = getOrFetchSystemBoxarts(chain, repoName)
        if (candidates.isEmpty()) return null

        val queries = listOf(fileCleanTitle, urlCleanTitle).filter { it.isNotBlank() }.distinct()
        var bestMatch: BoxartCandidate? = null
        var bestScore = Int.MIN_VALUE

        for (query in queries) {
            val queryTokens = tokenizeForMatch(query)
            if (queryTokens.isEmpty()) continue
            val queryNumeric = queryTokens.filter { it.all(Char::isDigit) }.toSet()
            val queryCollapsed = collapseAlphaNum(query)

            for (candidate in candidates) {
                val matchedCount = queryTokens.count { it in candidate.tokens }
                if (matchedCount == 0) continue

                val candNumeric = candidate.tokens.filter { it.all(Char::isDigit) }.toSet()
                if (queryNumeric != candNumeric) continue

                val extraTokens = (candidate.tokens - queryTokens).size
                val missingTokens = queryTokens.size - matchedCount
                val exactBonus = if (queryCollapsed.isNotEmpty() && queryCollapsed == candidate.collapsed) 200 else 0

                val score =
                    (matchedCount * 100) / queryTokens.size -
                        (extraTokens * 15) -
                        (missingTokens * 25) +
                        exactBonus +
                        candidate.regionBonus -
                        candidate.penalty

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = candidate
                }
            }
        }

        if (bestMatch == null || bestScore < 70) return null

        val basePrefix = originalUrl.substringBeforeLast('/') + "/"
        val matchedUrl = "$basePrefix${Uri.encode(bestMatch.fileName)}"
        if (matchedUrl == originalUrl) return null

        return tryProceed(chain, template, matchedUrl)
    }

    private fun getOrFetchSystemBoxarts(
        chain: Interceptor.Chain,
        repoName: String,
    ): List<BoxartCandidate> {
        systemBoxartsCache[repoName]?.let { return it }

        val lock = systemFetchLocks.getOrPut(repoName) { Any() }
        synchronized(lock) {
            systemBoxartsCache[repoName]?.let { return it }

            val fetched = fetchSystemBoxartsFromGitHub(chain, repoName)
            systemBoxartsCache[repoName] = fetched
            return fetched
        }
    }

    private fun fetchSystemBoxartsFromGitHub(
        chain: Interceptor.Chain,
        repoName: String,
    ): List<BoxartCandidate> {
        return try {
            val rootUrl = "https://api.github.com/repos/libretro-thumbnails/$repoName/git/trees/master"
            val rootJson = fetchJson(chain, rootUrl) ?: return emptyList()
            val rootTree = rootJson.optJSONArray("tree") ?: return emptyList()

            var boxartsSha: String? = null
            for (i in 0 until rootTree.length()) {
                val entry = rootTree.optJSONObject(i) ?: continue
                if (entry.optString("path") == "Named_Boxarts") {
                    boxartsSha = entry.optString("sha")
                    break
                }
            }
            if (boxartsSha.isNullOrBlank()) return emptyList()

            val boxartsUrl = "https://api.github.com/repos/libretro-thumbnails/$repoName/git/trees/$boxartsSha"
            val boxartsJson = fetchJson(chain, boxartsUrl) ?: return emptyList()
            val boxartsTree = boxartsJson.optJSONArray("tree") ?: return emptyList()

            val result = ArrayList<BoxartCandidate>(boxartsTree.length())
            for (i in 0 until boxartsTree.length()) {
                val entry = boxartsTree.optJSONObject(i) ?: continue
                val path = entry.optString("path")
                if (!path.endsWith(".png", ignoreCase = true)) continue

                val stem = path.removeSuffix(".png")
                val cleaned = cleanTitle(stem)
                val tokens = tokenizeForMatch(cleaned)
                if (tokens.isEmpty()) continue

                val regionBonus =
                    when {
                        stem.contains("(USA", ignoreCase = true) -> 30
                        stem.contains("(World", ignoreCase = true) -> 28
                        stem.contains("(Europe", ignoreCase = true) -> 25
                        stem.contains("(Brazil", ignoreCase = true) -> 22
                        stem.contains("(Japan", ignoreCase = true) -> 5
                        else -> 0
                    }

                var penalty = 0
                for (tag in PENALTY_TAGS) {
                    if (stem.contains(tag, ignoreCase = true)) {
                        penalty += 40
                    }
                }

                result.add(
                    BoxartCandidate(
                        fileName = path,
                        tokens = tokens,
                        collapsed = collapseAlphaNum(cleaned),
                        regionBonus = regionBonus,
                        penalty = penalty,
                    ),
                )
            }
            result
        } catch (e: Exception) {
            Timber.d(e, "Failed to fetch libretro-thumbnails index for $repoName")
            emptyList()
        }
    }

    private fun fetchJson(
        chain: Interceptor.Chain,
        url: String,
    ): JSONObject? {
        val req =
            Request.Builder()
                .url(url)
                .header("User-Agent", "LemuroidCustom/1.0")
                .header("Accept", "application/vnd.github+json")
                .build()
        return chain.proceed(req).use { res ->
            if (!res.isSuccessful) return null
            val body = res.body?.string() ?: return null
            JSONObject(body)
        }
    }

    private fun tokenizeForMatch(title: String): Set<String> {
        return title
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .map { TOKEN_NORMALIZATIONS[it] ?: it }
            .filter {
                (it.length >= 2 || (it.isNotEmpty() && it.all(Char::isDigit))) &&
                    it !in STOP_WORDS
            }
            .toSet()
    }

    private fun collapseAlphaNum(title: String): String {
        return title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    private fun sanitizeThumbName(name: String): String {
        return name.replace(THUMB_REPLACE, "_")
    }

    private fun extractCleanTitle(libretroUrl: String): String {
        val fileName = Uri.decode(libretroUrl.substringAfterLast('/'))
        return cleanTitle(fileName.removeSuffix(".png"))
    }

    private fun cleanTitle(rawName: String): String {
        return rawName
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
