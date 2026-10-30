package io.github.kdroidfilter.seforimapp.features.pdf

import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import org.apache.pdfbox.Loader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Persistent page positions, shared by personal PDFs and printed Talmud editions. */
class PdfPreindexStore(
    configuredDirectory: Path? = null,
) {
    private val directory by lazy {
        configuredDirectory ?: Path
            .of(getUserSettingsDatabasePath())
            .toAbsolutePath()
            .parent
            .resolve("pdf-index")
    }

    @Synchronized
    fun prepare(file: File) {
        val target = cachePath(file)
        if (Files.isRegularFile(target.resolve("ready"))) return
        Files.createDirectories(directory)
        val staging = Files.createTempDirectory(directory, ".building-")
        try {
            Loader.loadPDF(file).use { document ->
                repeat(document.numberOfPages) { page ->
                    val stripper = PositionCollectingPdfStripper(page)
                    stripper.getText(document)
                    DataOutputStream(GZIPOutputStream(Files.newOutputStream(staging.resolve("$page.gz")))).use { output ->
                        output.writeInt(stripper.glyphs.size)
                        stripper.glyphs.forEach { glyph ->
                            output.writeUTF(glyph.text)
                            output.writeFloat(glyph.bounds.left)
                            output.writeFloat(glyph.bounds.top)
                            output.writeFloat(glyph.bounds.right)
                            output.writeFloat(glyph.bounds.bottom)
                        }
                    }
                }
                Files.writeString(staging.resolve("ready"), document.numberOfPages.toString())
            }
            // A second importer may have finished the same immutable generation meanwhile.
            if (Files.isRegularFile(target.resolve("ready"))) return
            if (Files.exists(target)) removeCacheDirectory(target)
            try {
                Files.move(staging, target)
            } catch (error: java.nio.file.FileAlreadyExistsException) {
                if (!Files.isRegularFile(target.resolve("ready"))) throw error
            }
        } finally {
            if (Files.exists(staging)) removeCacheDirectory(staging)
        }
    }

    internal fun page(
        file: File,
        pageIndex: Int,
    ): PdfPageText? {
        val target = cachePath(file)
        if (!Files.isRegularFile(target.resolve("ready"))) return null
        return runCatching {
            DataInputStream(GZIPInputStream(Files.newInputStream(target.resolve("$pageIndex.gz")))).use { input ->
                val count = input.readInt()
                require(count in 0..MAX_PAGE_GLYPHS)
                PdfPageText(
                    List(count) {
                        PdfGlyph(
                            input.readUTF(),
                            PdfNormalizedRect(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat()),
                        )
                    },
                )
            }
        }.getOrElse {
            Files.deleteIfExists(target.resolve("ready"))
            null
        }
    }

    private fun cachePath(file: File): Path {
        val identity = "v1:${file.canonicalPath}:${file.length()}:${file.lastModified()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        return directory.resolve(hash)
    }

    private fun removeCacheDirectory(path: Path) {
        require(path.toAbsolutePath().normalize().parent == directory.toAbsolutePath().normalize())
        check(path.toFile().deleteRecursively()) { "Could not remove incomplete PDF index" }
    }

    private companion object {
        const val MAX_PAGE_GLYPHS = 2_000_000
    }
}
