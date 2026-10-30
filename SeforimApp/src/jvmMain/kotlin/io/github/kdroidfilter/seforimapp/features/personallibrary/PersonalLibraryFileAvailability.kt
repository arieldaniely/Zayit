package io.github.kdroidfilter.seforimapp.features.personallibrary

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

internal const val PERSONAL_FILES_UNAVAILABLE = "PERSONAL_FILES_UNAVAILABLE"
private const val MAX_UNAVAILABLE_FILES = 10

/** Check metadata before opening cloud placeholders, which can block for minutes per file. */
internal fun requireLocalPersonalFiles(folders: List<PersonalBookFolder>) {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
    val unavailable = mutableListOf<String>()
    folders.forEach { folder ->
        if (unavailable.size == MAX_UNAVAILABLE_FILES) return@forEach
        Files.walk(Path.of(folder.path)).use { files ->
            files
                .filter { it.isRegularFile() && it.extension.lowercase() in setOf("txt", "docx", "pdf", "json") }
                .filter { file ->
                    val attributes = Files.getAttribute(file, "dos:attributes", LinkOption.NOFOLLOW_LINKS) as Int
                    personalFileNeedsDownload(attributes)
                }.limit((MAX_UNAVAILABLE_FILES - unavailable.size).toLong())
                .forEach { unavailable += it.toString() }
        }
    }
    check(unavailable.isEmpty()) { "$PERSONAL_FILES_UNAVAILABLE\n${unavailable.joinToString("\n")}" }
}

internal fun personalFileNeedsDownload(attributes: Int): Boolean =
    // FILE_ATTRIBUTE_OFFLINE, RECALL_ON_OPEN and RECALL_ON_DATA_ACCESS. UNPINNED alone is safe to read.
    attributes and (0x00001000 or 0x00040000 or 0x00400000) != 0
