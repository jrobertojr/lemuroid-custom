package com.swordfish.lemuroid.app.shared.hub

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.local.StorageAccessFrameworkProvider

/**
 * Unified server presets for the Multiplayer module.
 * [DEFAULT_SAO_PAULO] is the initial discovery preset; listing a room does not establish a netplay session.
 */
enum class MultiplayerServerPreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val lobbyListUrl: String,
    val relayHost: String,
    val relayPort: Int,
    val mitmHandle: String = "",
    val isLan: Boolean = false,
) {
    DEFAULT_SAO_PAULO(
        id = "default_sao_paulo",
        displayName = "Servidor Padrão — Brasil (São Paulo)",
        subtitle = "Relay público oficial • América do Sul",
        lobbyListUrl = "http://lobby.libretro.com/list/",
        relayHost = "southamerica-east1.relay.retroarch.com",
        relayPort = 55435,
        mitmHandle = "saopaulo",
    ),
    SECONDARY_USA(
        id = "secondary_usa",
        displayName = "Servidor Secundário — EUA",
        subtitle = "Relay público oficial • América do Norte",
        lobbyListUrl = "http://lobby.libretro.com/list/",
        relayHost = "us-east1.relay.retroarch.com",
        relayPort = 55435,
        mitmHandle = "nyc",
    ),
    SECONDARY_EUROPE(
        id = "secondary_europe",
        displayName = "Servidor Europa — Madrid",
        subtitle = "Relay público oficial • Europa",
        lobbyListUrl = "http://lobby.libretro.com/list/",
        relayHost = "europe-southwest1.relay.retroarch.com",
        relayPort = 55435,
        mitmHandle = "madrid",
    ),
    LOCAL_LAN(
        id = "local_lan",
        displayName = "Rede Local (Wi-Fi / LAN)",
        subtitle = "Descoberta direta na mesma rede Wi-Fi",
        lobbyListUrl = "",
        relayHost = "",
        relayPort = 55435,
        mitmHandle = "",
        isLan = true,
    ),
    ;

    companion object {
        val DEFAULT: MultiplayerServerPreset = DEFAULT_SAO_PAULO

        fun fromId(id: String?): MultiplayerServerPreset =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class HubThemeVisuals(
    val theme: FrontendTheme,
    val displayName: String,
    val subtitle: String,
    val accent: Color,
    val secondaryAccent: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
    val webThemeKey: String,
    val webBgHex: String,
    val webSurfaceHex: String,
    val webCardHex: String,
    val webAccentHex: String,
    val webAccent2Hex: String,
)

object HubConfigManager {
    private const val PREF_KEY_MP_SERVER = "pref_key_multiplayer_server_preset"
    private const val PREF_KEY_CONSOLE_FOLDER_PREFIX = "pref_key_hub_console_folder_"

    @Volatile
    private var indexedGamesSnapshot: List<com.swordfish.lemuroid.lib.library.db.entity.Game> = emptyList()

    fun updateIndexedGamesSnapshot(games: List<com.swordfish.lemuroid.lib.library.db.entity.Game>) {
        indexedGamesSnapshot = games
    }

    fun getIndexedGamesSnapshot(): List<com.swordfish.lemuroid.lib.library.db.entity.Game> = indexedGamesSnapshot

    fun getActiveTheme(context: Context): FrontendTheme {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val key = prefs.getString(context.getString(R.string.pref_key_frontend_theme), null)
        return FrontendTheme.fromKey(key)
    }

    fun setActiveTheme(
        context: Context,
        theme: FrontendTheme,
    ) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit()
            .putString(context.getString(R.string.pref_key_frontend_theme), theme.key)
            .commit()
    }

    fun getGlobalRomFolderUri(context: Context): String? {
        val key = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        val fromHarmony =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(key, null)
                ?.takeIf { it.isNotBlank() }
        if (fromHarmony != null) return fromHarmony

        val fromDefault =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, null)
                ?.takeIf { it.isNotBlank() }
        if (fromDefault != null) return fromDefault

        // Fallback: infer the exact SAF tree URI directly from Lemuroid's indexed games!
        for (game in indexedGamesSnapshot) {
            val raw = game.fileUri
            val docIdx = raw.indexOf("/document/")
            if (docIdx > 0 && raw.contains("/tree/")) {
                return raw.substring(0, docIdx)
            }
        }
        return null
    }

    fun getReadableGlobalRomFolder(context: Context): String {
        val raw = getGlobalRomFolderUri(context)
        if (!raw.isNullOrBlank()) {
            return readableTreePath(raw)
        }
        return "Nenhuma pasta configurada"
    }

    fun getConsoleFolderUri(
        context: Context,
        consoleId: String,
    ): String? {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        return prefs.getString("$PREF_KEY_CONSOLE_FOLDER_PREFIX${consoleId.lowercase()}", null)
            ?.takeIf { it.isNotBlank() }
            ?: getGlobalRomFolderUri(context)
    }

    fun setConsoleFolderUri(
        context: Context,
        consoleId: String,
        uri: String,
    ) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit()
            .putString("$PREF_KEY_CONSOLE_FOLDER_PREFIX${consoleId.lowercase()}", uri)
            .apply()
    }

    fun clearConsoleFolderUri(
        context: Context,
        consoleId: String,
    ) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit()
            .remove("$PREF_KEY_CONSOLE_FOLDER_PREFIX${consoleId.lowercase()}")
            .apply()
    }

    fun hasCustomConsoleFolderUri(
        context: Context,
        consoleId: String,
    ): Boolean {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        return !prefs.getString("$PREF_KEY_CONSOLE_FOLDER_PREFIX${consoleId.lowercase()}", null)
            .isNullOrBlank()
    }

    /**
     * Returns a unified per-console folder plan combining:
     * 1. Custom user override (if explicitly set)
     * 2. Exact subfolder already identified by Lemuroid's scanner (default priority!)
     * 3. Suggested uppercase short-code subfolder following the user's library convention
     *    (automatically created inside the global root on first download).
     */
    fun getConsoleFolderPlan(
        context: Context,
        consoleIds: List<String> = DEFAULT_KNOWN_CONSOLES,
    ): Map<String, Map<String, String>> {
        val rootPath = getReadableGlobalRomFolder(context).trimEnd('/')
        val discovered = getDiscoveredConsoleFolders(context)
        val result = LinkedHashMap<String, Map<String, String>>()
        for (cid in consoleIds) {
            val key = cid.lowercase()
            val discoveredRel = canonicalConsoleKeys(key).firstNotNullOfOrNull { discovered[it] }
            val suggested = mapConsoleToFolderName(key)
            val isCustom = hasCustomConsoleFolderUri(context, key)
            val (folder, fullPath, source) =
                when {
                    isCustom -> {
                        val customUri = getConsoleFolderUri(context, key).orEmpty()
                        val readable = readableTreePath(customUri)
                        Triple(readable.substringAfterLast('/'), readable, "custom")
                    }
                    !discoveredRel.isNullOrBlank() -> {
                        Triple(discoveredRel, "$rootPath/$discoveredRel", "detected")
                    }
                    else -> {
                        Triple(suggested, "$rootPath/$suggested", "suggested")
                    }
                }
            result[key] =
                mapOf(
                    "folder" to folder,
                    "fullPath" to fullPath,
                    "source" to source,
                    "suggested" to suggested,
                )
        }
        return result
    }

    private fun readableTreePath(raw: String): String {
        val uri = Uri.parse(raw)
        val last = Uri.decode(uri.lastPathSegment ?: raw)
        val colonIdx = last.lastIndexOf(':')
        return if (colonIdx >= 0 && colonIdx + 1 < last.length) {
            last.substring(colonIdx + 1)
        } else {
            last
        }
    }

    private val DEFAULT_KNOWN_CONSOLES =
        listOf(
            "mame",
            "nes",
            "snes",
            "n64",
            "gb",
            "gbc",
            "gba",
            "nds",
            "megadrive",
            "md",
            "genesis",
            "mastersystem",
            "sms",
            "gg",
            "segacd",
            "psx",
            "psp",
            "pce",
            "atari2600",
            "atari7800",
            "lynx",
            "dos",
        )

    /**
     * Extracts the exact real subfolder path discovered by Lemuroid's own scanner for each console,
     * based on indexed [Game.fileUri] entries (works with SAF, TVFolderPicker, and legacy file:// URIs).
     */
    fun getDiscoveredConsoleFolders(context: Context): Map<String, String> {
        val globalUriStr = getGlobalRomFolderUri(context)
        val rootDocId =
            globalUriStr?.let { raw ->
                runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(raw)) }.getOrNull()
                    ?.let { Uri.decode(it) }
                    ?.trimEnd('/')
            }
        val rootReadable =
            if (!globalUriStr.isNullOrBlank()) {
                readableTreePath(globalUriStr).trimEnd('/')
            } else {
                null
            }

        val countsByConsole = HashMap<String, MutableMap<String, Int>>()
        for (game in indexedGamesSnapshot) {
            val relDir = extractConsoleFolderFromGameUri(game.fileUri, rootDocId, rootReadable)
            if (relDir.isNullOrBlank()) continue

            val keys = canonicalConsoleKeys(game.systemId)
            for (k in keys) {
                val map = countsByConsole.getOrPut(k) { HashMap() }
                map[relDir] = (map[relDir] ?: 0) + 1
            }
        }

        val resolved = HashMap<String, String>()
        for ((consoleKey, dirCounts) in countsByConsole) {
            val mostCommon = dirCounts.maxByOrNull { it.value }?.key
            if (!mostCommon.isNullOrBlank()) {
                resolved[consoleKey] = mostCommon
            }
        }
        return resolved
    }

    private fun extractConsoleFolderFromGameUri(
        fileUriStr: String,
        rootDocId: String?,
        rootReadable: String?,
    ): String? {
        val uri = runCatching { Uri.parse(fileUriStr) }.getOrNull() ?: return null
        val decodedDocId =
            runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                ?.let { Uri.decode(it) }
                ?: Uri.decode(uri.path ?: fileUriStr)

        // 1. Exact top-level console subfolder directly under global SAF tree root
        //    e.g. /storage/620F-B493/PSX/Fear Effect/Disc1.chd -> "PSX"
        if (!rootDocId.isNullOrBlank() && decodedDocId.startsWith(rootDocId, ignoreCase = true)) {
            val rel = decodedDocId.substring(rootDocId.length).trimStart('/', ':')
            val firstSlash = rel.indexOf('/')
            if (firstSlash > 0) {
                return rel.substring(0, firstSlash).trim('/')
            }
        }
        if (!rootReadable.isNullOrBlank()) {
            val idx = decodedDocId.indexOf(rootReadable, ignoreCase = true)
            if (idx >= 0) {
                val rel = decodedDocId.substring(idx + rootReadable.length).trimStart('/', ':')
                val firstSlash = rel.indexOf('/')
                if (firstSlash > 0) {
                    return rel.substring(0, firstSlash).trim('/')
                }
            }
        }

        // 2. Reliable fallback: take the immediate parent directory name of the indexed ROM file
        val cleanPath = decodedDocId.substringAfterLast(':').trimEnd('/')
        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        if (segments.size >= 2) {
            return segments[segments.size - 2]
        }
        return null
    }

    private fun canonicalConsoleKeys(systemId: String): List<String> =
        when (systemId.lowercase()) {
            "fbneo", "mame2003plus", "arcade", "mame" -> listOf("mame", "fbneo", "mame2003plus", "arcade")
            "md", "genesis", "megadrive" -> listOf("megadrive", "md", "genesis")
            "sms", "mastersystem" -> listOf("mastersystem", "sms")
            "nds", "ds" -> listOf("nds", "ds")
            else -> listOf(systemId.lowercase())
        }

    /**
     * Resolves (or creates) the console subfolder inside the shared global SAF root folder.
     * Prioritizes the exact real console folder already identified by Lemuroid's scanner so
     * Locadora & Multiplayer save straight into the user's original console folders!
     */
    fun resolveWritableConsoleDir(
        context: Context,
        consoleId: String,
    ): DocumentFile? {
        val specificUri = getConsoleFolderUri(context, consoleId) ?: return null
        val root = DocumentFile.fromTreeUri(context, Uri.parse(specificUri)) ?: return null
        if (!root.canWrite()) return null

        val globalUri = getGlobalRomFolderUri(context)
        if (specificUri != globalUri) {
            return root
        }

        // 1. Prefer the exact existing subfolder discovered by Lemuroid's own library scanner
        val discovered = getDiscoveredConsoleFolders(context)
        val discoveredPath =
            canonicalConsoleKeys(consoleId).firstNotNullOfOrNull { discovered[it] }
        if (!discoveredPath.isNullOrBlank()) {
            var cursor: DocumentFile = root
            var allFound = true
            for (segment in discoveredPath.split('/').filter { it.isNotBlank() }) {
                val child =
                    cursor.findFile(segment)
                        ?: cursor.listFiles().firstOrNull {
                            it.isDirectory && it.name.equals(segment, ignoreCase = true)
                        }
                if (child != null && child.isDirectory) {
                    cursor = child
                } else {
                    allFound = false
                    break
                }
            }
            if (allFound && cursor.canWrite()) {
                return cursor
            }
        }

        // 2. Fallback: find or create default folder name inside global SAF root
        val folderName = mapConsoleToFolderName(consoleId)
        val existing =
            root.findFile(folderName)
                ?: root.listFiles().firstOrNull {
                    it.isDirectory && it.name.equals(folderName, ignoreCase = true)
                }
        if (existing != null && existing.isDirectory) {
            return existing
        }
        return root.createDirectory(folderName) ?: root
    }

    fun mapConsoleToFolderName(consoleId: String): String =
        when (consoleId.lowercase()) {
            "mame", "fbneo", "mame2003plus", "arcade", "cps1", "cps2", "neogeo" -> "ARCADE"
            "nes", "fc" -> "NES"
            "snes", "sfc" -> "SNES"
            "n64" -> "N64"
            "gb" -> "GB"
            "gbc" -> "GBC"
            "gba" -> "GBA"
            "nds", "ds" -> "DS"
            "psx", "ps1" -> "PSX"
            "psp" -> "PSP"
            "md", "genesis", "megadrive" -> "MEGA"
            "sms", "mastersystem" -> "MASTER"
            "gg", "gamegear" -> "GG"
            "segacd", "megacd" -> "SEGACD"
            "pce", "pcengine" -> "PCE"
            "atari2600" -> "ATARI2600"
            "atari7800" -> "ATARI7800"
            "lynx" -> "LYNX"
            "dos", "msdos" -> "DOS"
            else -> consoleId.uppercase()
        }

    fun getSelectedMultiplayerServer(context: Context): MultiplayerServerPreset {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        return MultiplayerServerPreset.fromId(prefs.getString(PREF_KEY_MP_SERVER, null))
    }

    fun setSelectedMultiplayerServer(
        context: Context,
        preset: MultiplayerServerPreset,
    ) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit().putString(PREF_KEY_MP_SERVER, preset.id).commit()
    }

    fun resetMultiplayerDefaults(context: Context) {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        prefs.edit().putString(PREF_KEY_MP_SERVER, MultiplayerServerPreset.DEFAULT.id).commit()
    }

    fun visualsFor(theme: FrontendTheme): HubThemeVisuals =
        when (theme) {
            FrontendTheme.DEFAULT ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Essencial",
                    subtitle = "Minimalista e direto ao ponto",
                    accent = Color(0xFF38BDF8),
                    secondaryAccent = Color(0xFF818CF8),
                    cardSurface = Color(0xCC111827),
                    cardBorder = Color(0x6638BDF8),
                    scrimTop = Color(0xB8090D16),
                    scrimBottom = Color(0xE8060910),
                    webThemeKey = "nextgen",
                    webBgHex = "#0B1120",
                    webSurfaceHex = "#111827",
                    webCardHex = "#1E293B",
                    webAccentHex = "#38BDF8",
                    webAccent2Hex = "#818CF8",
                )
            FrontendTheme.LOCADORA ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Locadora",
                    subtitle = "Clima de videolocadora anos 90",
                    accent = Color(0xFFFFC107),
                    secondaryAccent = Color(0xFF29B6F6),
                    cardSurface = Color(0xCC1F140E),
                    cardBorder = Color(0x88FFC107),
                    scrimTop = Color(0xA6140B07),
                    scrimBottom = Color(0xEB100805),
                    webThemeKey = "retro80s",
                    webBgHex = "#16100C",
                    webSurfaceHex = "#1F1712",
                    webCardHex = "#2F231B",
                    webAccentHex = "#FFC107",
                    webAccent2Hex = "#29B6F6",
                )
            FrontendTheme.NETFLIX ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "CineArcade",
                    subtitle = "Vitrine cinematográfica widescreen",
                    accent = Color(0xFFE50914),
                    secondaryAccent = Color(0xFFF59E0B),
                    cardSurface = Color(0xCC161114),
                    cardBorder = Color(0x88E50914),
                    scrimTop = Color(0xB00B080A),
                    scrimBottom = Color(0xEC080608),
                    webThemeKey = "cinema",
                    webBgHex = "#0D0B0E",
                    webSurfaceHex = "#161217",
                    webCardHex = "#221C24",
                    webAccentHex = "#E50914",
                    webAccent2Hex = "#F59E0B",
                )
            FrontendTheme.CONSOLE ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Sala de Consoles",
                    subtitle = "Quarto gamer 3D dos anos 80/90",
                    accent = Color(0xFF38E8FF),
                    secondaryAccent = Color(0xFFFFB74D),
                    cardSurface = Color(0xCC151B26),
                    cardBorder = Color(0x8038E8FF),
                    scrimTop = Color(0xA80C1018),
                    scrimBottom = Color(0xEA080B12),
                    webThemeKey = "nextgen",
                    webBgHex = "#0E131D",
                    webSurfaceHex = "#161E2E",
                    webCardHex = "#1F2A3F",
                    webAccentHex = "#38E8FF",
                    webAccent2Hex = "#FFB74D",
                )
            FrontendTheme.TV ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "TV de Tubo",
                    subtitle = "Tubo CRT com madeira e fósforo",
                    accent = Color(0xFFFFB74D),
                    secondaryAccent = Color(0xFF4CFF6A),
                    cardSurface = Color(0xCC1C120C),
                    cardBorder = Color(0x80FFB74D),
                    scrimTop = Color(0xAA120A06),
                    scrimBottom = Color(0xEC0D0704),
                    webThemeKey = "retro80s",
                    webBgHex = "#140C08",
                    webSurfaceHex = "#21140D",
                    webCardHex = "#2E1B11",
                    webAccentHex = "#FFB74D",
                    webAccent2Hex = "#4CFF6A",
                )
            FrontendTheme.RETRO_80_90 ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Retrô 80/90",
                    subtitle = "Synthwave neon e grade laser",
                    accent = Color(0xFFFF2A85),
                    secondaryAccent = Color(0xFF00F0FF),
                    cardSurface = Color(0xCC1B0C33),
                    cardBorder = Color(0x9900F0FF),
                    scrimTop = Color(0xA8110624),
                    scrimBottom = Color(0xEC0C041A),
                    webThemeKey = "retro80s",
                    webBgHex = "#120822",
                    webSurfaceHex = "#1B0C33",
                    webCardHex = "#241042",
                    webAccentHex = "#FF2A85",
                    webAccent2Hex = "#00F0FF",
                )
            FrontendTheme.ROCKSTAR ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Lado B",
                    subtitle = "Vinil hard rock 80/90",
                    accent = Color(0xFFF5B942),
                    secondaryAccent = Color(0xFFE05A47),
                    cardSurface = Color(0xCC181412),
                    cardBorder = Color(0x88F5B942),
                    scrimTop = Color(0xB0100D0B),
                    scrimBottom = Color(0xEE0B0908),
                    webThemeKey = "cinema",
                    webBgHex = "#12100E",
                    webSurfaceHex = "#1C1815",
                    webCardHex = "#28221D",
                    webAccentHex = "#F5B942",
                    webAccent2Hex = "#E05A47",
                )
            FrontendTheme.THRASH_METAL ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Thrash Metal",
                    subtitle = "Aço escovado e brasas industriais",
                    accent = Color(0xFFFF4D2D),
                    secondaryAccent = Color(0xFFFFB020),
                    cardSurface = Color(0xCC171314),
                    cardBorder = Color(0x88FF4D2D),
                    scrimTop = Color(0xB40E0B0C),
                    scrimBottom = Color(0xEE090708),
                    webThemeKey = "cinema",
                    webBgHex = "#110E0F",
                    webSurfaceHex = "#1B1618",
                    webCardHex = "#272022",
                    webAccentHex = "#FF4D2D",
                    webAccent2Hex = "#FFB020",
                )
            FrontendTheme.THREE_D ->
                HubThemeVisuals(
                    theme = theme,
                    displayName = "Vitrine 3D",
                    subtitle = "Galeria de hardware e mídias 3D",
                    accent = Color(0xFF60A5FA),
                    secondaryAccent = Color(0xFFFBBF24),
                    cardSurface = Color(0xCC101624),
                    cardBorder = Color(0x8060A5FA),
                    scrimTop = Color(0xA8090D18),
                    scrimBottom = Color(0xEC070A12),
                    webThemeKey = "nextgen",
                    webBgHex = "#0A0E1A",
                    webSurfaceHex = "#111827",
                    webCardHex = "#1C2538",
                    webAccentHex = "#60A5FA",
                    webAccent2Hex = "#FBBF24",
                )
        }

    @Suppress("unused")
    fun hasStorageAccess(context: Context): Boolean {
        val uri = getGlobalRomFolderUri(context) ?: return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            doc != null && doc.exists()
        }.getOrDefault(false)
    }

    @Suppress("unused")
    fun safProviderClassName(): String = StorageAccessFrameworkProvider::class.java.simpleName
}
