package io.github.kdroidfilter.seforimapp.features.search

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Reuses a successful validation across launches while every immutable bundle file is unchanged. */
internal class SemanticBundleValidationCache {
    private var validated: Pair<Path, List<String>>? = null

    fun invalidate() {
        validated = null
    }

    fun validate(
        root: Path,
        database: Path,
        verify: () -> Unit,
    ) {
        val cacheFile = root.resolveSibling(".${root.fileName}.validated-v1")
        val stamp = semanticBundleStamp(root, database)
        if (validated == (cacheFile to stamp)) return
        val digest = MessageDigest.getInstance("SHA-256")
        stamp.forEach { entry ->
            digest.update(entry.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
        if (runCatching { Files.readString(cacheFile) }.getOrNull() != fingerprint) {
            verify()
            check(semanticBundleStamp(root, database) == stamp) { "Semantic bundle changed during validation" }
            // Read-only installs must remain usable. An incomplete cache is simply a miss next time.
            runCatching {
                val temporary = Files.createTempFile(cacheFile.parent, ".semantic-validation-", ".tmp")
                try {
                    Files.writeString(temporary, fingerprint)
                    Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING)
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
        validated = cacheFile to stamp
    }
}
