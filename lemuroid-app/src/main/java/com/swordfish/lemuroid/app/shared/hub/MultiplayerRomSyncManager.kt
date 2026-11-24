package com.swordfish.lemuroid.app.shared.hub

import android.content.Context
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class MultiplayerRoom(
    val id: String,
    val hostName: String,
    val gameName: String,
    val romVersionName: String,
    val gameCrc: String,
    val coreName: String,
    val coreVersion: String = "",
    val frontend: String = "",
    val consoleId: String,
    val country: String,
    val ip: String,
    val port: Int,
    val mitmIp: String = "",
    val mitmPort: Int = 55435,
    val mitmSession: String = "",
    val hostMethod: Int = 0,
    val hasPassword: Boolean,
    val hasSpectatePassword: Boolean = false,
    val connectable: Boolean = true,
    val isLan: Boolean = false,
)

data class CatalogRomMatch(
    val consoleId: String,
    val title: String,
    val filename: String,
    val downloadUrl: String,
)

data class RomDownloadProgress(
    val filename: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: String,
    val error: String? = null,
) {
    val percent: Int
        get() =
            if (totalBytes > 0L) {
                ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }
}

object MultiplayerRomSyncManager {
    private const val LAN_DISCOVERY_PORT = 55436
    private val ROM_EXTENSION =
        Regex("\\.(zip|7z|nds|gba|gbc|gb|nes|sfc|smc|bin|cue|iso|chd|pbp)$", RegexOption.IGNORE_CASE)
    private val NAME_SEPARATORS = Regex("[^a-z0-9]+")

    /**
     * Fetches live rooms from the selected server preset (RetroArch public lobby API for Internet
     * presets, or local Wi-Fi UDP broadcast discovery for [MultiplayerServerPreset.LOCAL_LAN]).
     */
    suspend fun fetchRooms(server: MultiplayerServerPreset): Result<List<MultiplayerRoom>> =
        withContext(Dispatchers.IO) {
            val lanRooms = discoverLanRooms()
            if (server.isLan) {
                return@withContext Result.success(lanRooms)
            }
            try {
                val conn =
                    (URL(server.lobbyListUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 12_000
                        setRequestProperty("User-Agent", "RetroArch/1.19.1")
                        setRequestProperty("Accept", "application/json")
                    }
                val body =
                    try {
                        if (conn.responseCode !in 200..299) {
                            throw IllegalStateException("Servidor respondeu HTTP ${conn.responseCode}")
                        }
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        conn.disconnect()
                    }
                currentCoroutineContext().ensureActive()
                val publicRooms = parseRetroArchLobbyJson(body)
                Result.success((lanRooms + publicRooms).distinctBy { it.id })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (lanRooms.isNotEmpty()) {
                    Result.success(lanRooms)
                } else {
                    Result.failure(error)
                }
            }
        }

    private fun JSONObject.optCleanString(
        key: String,
        fallback: String = "",
    ): String {
        if (isNull(key)) return fallback
        val v = optString(key, "").trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun JSONObject.optFlag(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean {
        if (isNull(key)) return defaultValue
        val raw = opt(key)
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> defaultValue
        }
    }

    private fun parseRetroArchLobbyJson(jsonText: String): List<MultiplayerRoom> {
        val arr = JSONArray(jsonText)
        val rooms = mutableListOf<MultiplayerRoom>()
        for (i in 0 until arr.length()) {
            val wrapper = arr.optJSONObject(i) ?: continue
            val fields = wrapper.optJSONObject("fields") ?: wrapper
            val gameName = fields.optCleanString("game_name")
            val coreName = fields.optCleanString("core_name")
            if (gameName.isBlank()) continue
            val consoleId = inferConsoleIdFromCore(coreName)
            val connectable = fields.optFlag("connectable", true)
            val hasPassword = fields.optFlag("has_password", false)
            val hasSpectatePassword = fields.optFlag("has_spectate_password", false)
            rooms +=
                MultiplayerRoom(
                    id = wrapper.optCleanString("pk", fields.optCleanString("id", "${i}_${gameName.hashCode()}")),
                    hostName = fields.optCleanString("username", "Jogador"),
                    gameName = gameName,
                    romVersionName = gameName,
                    gameCrc = fields.optCleanString("game_crc"),
                    coreName = coreName,
                    coreVersion = fields.optCleanString("core_version"),
                    frontend = fields.optCleanString("frontend"),
                    consoleId = consoleId,
                    country = fields.optCleanString("country", "--"),
                    ip = fields.optCleanString("ip"),
                    port = fields.optInt("port", 55435),
                    mitmIp = fields.optCleanString("mitm_ip"),
                    mitmPort = fields.optInt("mitm_port", 55435),
                    mitmSession = fields.optCleanString("mitm_session"),
                    hostMethod = fields.optInt("host_method", 0),
                    hasPassword = hasPassword,
                    hasSpectatePassword = hasSpectatePassword,
                    connectable = connectable,
                    isLan = false,
                )
        }
        return rooms
            .distinctBy { it.id }
            .filter { it.connectable || it.mitmSession.isNotBlank() }
            .sortedWith(
                compareBy<MultiplayerRoom> { it.hasPassword || it.hasSpectatePassword }
                    .thenBy { it.mitmSession.isBlank() }
                    .thenBy { it.country.lowercase(Locale.ROOT) != "br" },
            )
            .take(50)
    }

    private fun discoverLanRooms(): List<MultiplayerRoom> {
        val found = mutableListOf<MultiplayerRoom>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 700
                val probe = "LEMUROID_HUB_DISCOVER".toByteArray()
                val packet =
                    DatagramPacket(
                        probe,
                        probe.size,
                        InetAddress.getByName("255.255.255.255"),
                        LAN_DISCOVERY_PORT,
                    )
                socket.send(packet)

                val buf = ByteArray(1024)
                val deadline = System.currentTimeMillis() + 750L
                while (System.currentTimeMillis() < deadline) {
                    val resp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length)
                        val obj = JSONObject(text)
                        found +=
                            MultiplayerRoom(
                                id = obj.optString("id", resp.address.hostAddress ?: "lan"),
                                hostName = obj.optString("host", "Host LAN"),
                                gameName = obj.optString("game", "Jogo LAN"),
                                romVersionName = obj.optString("version", obj.optString("game", "")),
                                gameCrc = obj.optString("crc", ""),
                                coreName = obj.optString("core", "FBNeo"),
                                consoleId = obj.optString("consoleId", "mame"),
                                country = "LAN",
                                ip = resp.address.hostAddress.orEmpty(),
                                port = obj.optInt("port", 55435),
                                hasPassword = false,
                                isLan = true,
                            )
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }
        return found
    }

    fun inferConsoleIdFromCore(coreName: String): String {
        val lower = coreName.lowercase(Locale.ROOT)
        return when {
            "fbneo" in lower || "fbalpha" in lower || "mame" in lower || "finalburn" in lower -> "mame"
            "snes" in lower || "bsnes" in lower || "mesen-s" in lower -> "snes"
            "fceumm" in lower || "nestopia" in lower || "mesen" in lower || "quicknes" in lower -> "nes"
            "genesis" in lower || "picodrive" in lower || "megadrive" in lower -> "md"
            "mgba" in lower || "vba" in lower || "gpsp" in lower -> "gba"
            "gambatte" in lower || "sameboy" in lower -> "gbc"
            "pcsx" in lower || "swanstation" in lower || "beetle psx" in lower -> "psx"
            "mupen" in lower || "parallel" in lower -> "n64"
            "ppsspp" in lower -> "psp"
            "desmume" in lower || "melonds" in lower || "noods" in lower || "dsperate" in lower -> "nds"
            "stella" in lower -> "atari2600"
            "dosbox" in lower -> "dos"
            "pce" in lower || "mednafen_pce" in lower -> "pce"
            else -> ""
        }
    }

    /**
     * Finds a local candidate by system and complete filename. This does not verify the host CRC.
     */
    fun findMatchingLocalGame(
        localGames: List<Game>,
        room: MultiplayerRoom,
    ): Game? {
        val normRoom = normalizeName(room.romVersionName)
        return localGames.firstOrNull { g ->
            val normFile = normalizeName(g.fileName)
            val normTitle = normalizeName(g.title)
            mapSystemToCatalogId(g.systemId) == mapSystemToCatalogId(room.consoleId) &&
                (normFile == normRoom || normTitle == normRoom)
        }
    }

    /** Index once per library update, rather than scanning the library for every visible row. */
    fun matchingRoomIds(
        localGames: List<Game>,
        rooms: List<MultiplayerRoom>,
    ): Set<String> {
        val names = HashSet<Pair<String, String>>(localGames.size * 2)
        localGames.forEach { game ->
            val system = mapSystemToCatalogId(game.systemId)
            names.add(system to normalizeName(game.fileName))
            names.add(system to normalizeName(game.title))
        }
        return rooms.filter { room ->
            (mapSystemToCatalogId(room.consoleId) to normalizeName(room.romVersionName)) in names
        }.mapTo(HashSet()) { it.id }
    }

    /**
     * Looks up a downloadable ROM in the bundled Locadora catalog (`assets/locadoura/data/`)
     * matching [room.consoleId] and [room.romVersionName] / [room.gameName].
     */
    suspend fun findCatalogMatch(
        context: Context,
        room: MultiplayerRoom,
    ): CatalogRomMatch? =
        withContext(Dispatchers.IO) {
            val catalogConsoleId = mapSystemToCatalogId(room.consoleId)
            val dataFile =
                if (catalogConsoleId == "mame") {
                    "locadoura/data/games.json"
                } else {
                    "locadoura/data/consoles/$catalogConsoleId.json"
                }
            runCatching {
                val jsonText = context.assets.open(dataFile).bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                val baseUrl = root.optString("baseUrl", "")
                val gamesArr = root.optJSONArray("games") ?: return@runCatching null
                val targetNorm = normalizeName(room.romVersionName.ifBlank { room.gameName })

                var bestMatch: CatalogRomMatch? = null
                for (i in 0 until gamesArr.length()) {
                    val g = gamesArr.optJSONObject(i) ?: continue
                    val title = g.optString("title", g.optString("name", ""))
                    val rom = g.optString("rom", "")
                    val file = g.optString("file", "")
                    val candidateName = file.ifBlank { if (rom.isNotBlank()) "$rom.zip" else title }
                    val candNorm = normalizeName(title.ifBlank { candidateName })

                    val exact = candNorm == targetNorm || normalizeName(candidateName) == targetNorm
                    if (exact) {
                        val url =
                            if (catalogConsoleId == "mame" && rom.isNotBlank()) {
                                baseUrl + URLEncoder.encode("$rom.zip", "UTF-8").replace("+", "%20")
                            } else {
                                val encodedPath =
                                    file.split("/").joinToString("/") { part ->
                                        URLEncoder.encode(part, "UTF-8").replace("+", "%20")
                                    }
                                baseUrl + encodedPath
                            }
                        val filename = candidateName.substringAfterLast('/').ifBlank { "${room.gameName}.zip" }
                        bestMatch =
                            CatalogRomMatch(
                                consoleId = room.consoleId,
                                title = title.ifBlank { filename },
                                filename = filename,
                                downloadUrl = url,
                            )
                        if (exact) break
                    }
                }
                currentCoroutineContext().ensureActive()
                bestMatch
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        }

    /**
     * Downloads the host's exact ROM version directly into Lemuroid's global SAF console folder,
     * reporting live progress and scheduling a library rescan upon completion.
     */
    suspend fun downloadRomToGlobalFolder(
        context: Context,
        match: CatalogRomMatch,
        onProgress: (RomDownloadProgress) -> Unit,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var partialFile: androidx.documentfile.provider.DocumentFile? = null
            var committed = false

            suspend fun report(progress: RomDownloadProgress) {
                withContext(Dispatchers.Main.immediate) { onProgress(progress) }
            }
            try {
                require(match.filename.isNotBlank() && '/' !in match.filename && '\\' !in match.filename) {
                    "Nome de arquivo inválido."
                }
                val targetDir =
                    HubConfigManager.resolveWritableConsoleDir(context, match.consoleId)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Configure a pasta global de jogos na Tela Inicial antes de baixar."),
                        )

                check(targetDir.findFile(match.filename) == null) {
                    "O arquivo já existe na pasta global e foi preservado."
                }
                report(RomDownloadProgress(match.filename, 0L, 0L, "Conectando..."))
                val targetFile =
                    targetDir.createFile("application/octet-stream", ".${match.filename}.${System.nanoTime()}.part")
                        ?: return@withContext Result.failure(
                            IllegalStateException("Não foi possível criar ${match.filename} na pasta global."),
                        )

                partialFile = targetFile
                conn =
                    (URL(match.downloadUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 20_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "LemuroidHub/1.0")
                    }
                val code = conn.responseCode
                if (code !in 200..299) {
                    targetFile.delete()
                    return@withContext Result.failure(IllegalStateException("Erro HTTP $code ao baixar ROM."))
                }

                val total = conn.contentLengthLong.coerceAtLeast(0L)
                var downloaded = 0L
                var lastUpdate = 0L

                conn.inputStream.use { input ->
                    val stream =
                        context.contentResolver.openOutputStream(targetFile.uri)
                            ?: throw IllegalStateException("Não foi possível gravar na pasta global.")
                    stream.use { output ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val r = input.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                            downloaded += r
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 150L) {
                                lastUpdate = now
                                report(
                                    RomDownloadProgress(
                                        filename = match.filename,
                                        bytesDownloaded = downloaded,
                                        totalBytes = total,
                                        status = "Baixando para pasta global...",
                                    ),
                                )
                            }
                        }
                        output.flush()
                    }
                }

                currentCoroutineContext().ensureActive()
                check(downloaded > 0L && (total <= 0L || downloaded == total)) {
                    "Download incompleto; nenhum jogo existente foi substituído."
                }
                check(targetDir.findFile(match.filename) == null && targetFile.renameTo(match.filename)) {
                    "Não foi possível concluir o arquivo; os jogos existentes foram preservados."
                }
                check(
                    targetFile.name == match.filename,
                ) { "O armazenamento alterou o nome do arquivo; download cancelado." }
                committed = true
                LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
                report(
                    RomDownloadProgress(
                        filename = match.filename,
                        bytesDownloaded = downloaded,
                        totalBytes = total.coerceAtLeast(downloaded),
                        status = "Concluído! Atualizando biblioteca...",
                    ),
                )
                Result.success(match.filename)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (!committed) runCatching { partialFile?.delete() }
                runCatching { conn?.disconnect() }
            }
        }

    private fun mapSystemToCatalogId(consoleId: String): String =
        when (consoleId.lowercase(Locale.ROOT)) {
            "fbneo", "mame2003plus", "arcade", "mame" -> "mame"
            "md", "genesis" -> "genesis"
            else -> consoleId.lowercase(Locale.ROOT)
        }

    private fun normalizeName(raw: String): String =
        raw.replace(ROM_EXTENSION, "")
            .lowercase(Locale.ROOT)
            .replace(NAME_SEPARATORS, "")
            .trim()
}
