package com.swordfish.lemuroid.lib.storage.local

import java.util.Locale

/** DOS packages are whole disks, never a single executable extracted from an archive. */
object DosArchiveRules {
    fun isDosArchive(name: String, path: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension == "dosz") return true
        if (extension != "zip") return false

        // SAF document paths may contain a volume separator or percent-encoded slashes.
        // Match directory components, not a title containing the letters "dos".
        val components = path.orEmpty()
            .replace(Regex("%2f", RegexOption.IGNORE_CASE), "/")
            .replace(Regex("%3a", RegexOption.IGNORE_CASE), ":")
            .replace('\\', '/')
            .split('/', ':')
            .dropLast(1)
        return components.any { it.lowercase(Locale.ROOT) in setOf("dos", "ms-dos", "msdos") }
    }
}
