package com.swordfish.lemuroid.app.shared.hub

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.swordfish.lemuroid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class GitHubReleaseInfo(
    val tagName: String,
    val title: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val assetSizeBytes: Long,
    val isNewer: Boolean,
)

object GitHubReleaseUpdater {
    private const val REPO_OWNER = "jrobertojr"
    private const val REPO_NAME = "lemuroid-custom"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    @Volatile
    var cachedRelease: GitHubReleaseInfo? = null
        private set

    fun currentInstalledVersion(): String =
        BuildConfig.VERSION_NAME.substringBefore('-').trim()

    suspend fun checkLatestRelease(context: Context): Result<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn =
                    (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 10000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "VideoGame-Updater/${BuildConfig.VERSION_NAME}")
                    }
                val body =
                    try {
                        val code = conn.responseCode
                        if (code == 404) {
                            throw IllegalStateException("Nenhum release publicado ainda no GitHub.")
                        }
                        if (code !in 200..299) {
                            throw IllegalStateException("GitHub respondeu HTTP $code")
                        }
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        conn.disconnect()
                    }

                val root = JSONObject(body)
                val tag = root.optString("tag_name", "").trim()
                val title = root.optString("name", tag).ifBlank { tag }
                val notes = root.optString("body", "").trim()
                val assets = root.optJSONArray("assets")

                val apkAssets = mutableListOf<Triple<String, String, Long>>()
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name", "")
                        val url = a.optString("browser_download_url", "")
                        val size = a.optLong("size", 0L)
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            apkAssets += Triple(name, url, size)
                        }
                    }
                }

                val bestAsset = pickBestApkForDevice(apkAssets)
                val installed = currentInstalledVersion()
                val isNewer = isRemoteVersionNewer(tag, installed)
                val info =
                    GitHubReleaseInfo(
                        tagName = tag.ifBlank { installed },
                        title = title,
                        notes = notes,
                        assetName = bestAsset?.first.orEmpty(),
                        downloadUrl = bestAsset?.second.orEmpty(),
                        assetSizeBytes = bestAsset?.third ?: 0L,
                        isNewer = isNewer && bestAsset != null,
                    )
                cachedRelease = info
                Timber.i(
                    "GitHubReleaseUpdater: installed=%s latest=%s isNewer=%s asset=%s",
                    installed,
                    info.tagName,
                    info.isNewer,
                    info.assetName,
                )
                info
            }
        }

    suspend fun downloadAndLaunchInstaller(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (percent: Int, message: String) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (release.downloadUrl.isBlank()) {
                    throw IllegalStateException("Nenhum APK compatível encontrado neste release.")
                }
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Clean old cached APKs to protect 2 GB TV storage
                updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

                val safeName = release.assetName.ifBlank { "VideoGame-${release.tagName}.apk" }
                val outFile = File(updatesDir, safeName)

                withContext(Dispatchers.Main) {
                    onProgress(0, "Baixando atualização ${release.tagName}...")
                }

                val conn =
                    (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "VideoGame-Updater/${BuildConfig.VERSION_NAME}")
                    }
                try {
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("Falha ao baixar APK (HTTP ${conn.responseCode})")
                    }
                    val total = conn.contentLengthLong.takeIf { it > 0L } ?: release.assetSizeBytes
                    var downloaded = 0L
                    var lastPct = -1
                    conn.inputStream.use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                downloaded += read
                                val pct =
                                    if (total > 0L) {
                                        ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                    } else {
                                        0
                                    }
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) {
                                        onProgress(pct, "Baixando ${release.tagName}... $pct%")
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                val apkUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.updater.fileprovider",
                        outFile,
                    )
                val installIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                withContext(Dispatchers.Main) {
                    onProgress(100, "Abrindo instalador da versão ${release.tagName}...")
                    context.startActivity(installIntent)
                }
            }
        }

    private fun pickBestApkForDevice(
        assets: List<Triple<String, String, Long>>,
    ): Triple<String, String, Long>? {
        if (assets.isEmpty()) return null
        val abis = Build.SUPPORTED_ABIS.map { it.lowercase(Locale.ROOT) }
        val prefersArm64 = abis.any { it.contains("arm64") }
        val prefersArm32 = !prefersArm64 && abis.any { it.contains("armeabi-v7a") }

        if (prefersArm64) {
            assets.firstOrNull {
                val n = it.first.lowercase(Locale.ROOT)
                "arm64" in n || "64bit" in n
            }?.let { return it }
        }
        if (prefersArm32) {
            assets.firstOrNull {
                val n = it.first.lowercase(Locale.ROOT)
                "armeabi-v7a" in n || "armv7" in n || "32bit" in n
            }?.let { return it }
        }
        return assets.firstOrNull { "universal" in it.first.lowercase(Locale.ROOT) }
            ?: assets.firstOrNull()
    }

    fun isRemoteVersionNewer(
        remoteTag: String,
        localVersion: String,
    ): Boolean {
        val rParts = parseVersionParts(remoteTag)
        val lParts = parseVersionParts(localVersion)
        val len = maxOf(rParts.size, lParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        return clean.split('.').mapNotNull { it.toIntOrNull() }
    }
}
