package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdOutputStream
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.resetDatabasePathCache
import io.github.kdroidfilter.seforimapp.framework.database.selectInstallationRoot
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractUseCaseTest {
    @Test
    fun `imports precisely the components in each distribution and selects the books database`() =
        runBlocking {
            withInstallRoot { root, source ->
                for (pdf in listOf(false, true)) {
                    for (vectors in listOf(false, true)) {
                        val install = File(root, "variant-$pdf-$vectors").apply { mkdirs() }
                        selectInstallationRoot(install)
                        val entries = linkedMapOf("seforim.db" to "books", "lexical.db" to "dictionary")
                        if (pdf) entries["תלמוד בבלי/ברכות.pdf"] = "pdf"
                        if (vectors) entries["seforim.db.semantic/model/tokenizer.json"] = "tokenizer"
                        val archive = File(source, "variant-$pdf-$vectors.tar.zst")
                        writeBundle(archive, entries)
                        val installed = ExtractUseCase().extractToDatabase(archive.path) {}
                        assertEquals(File(install, "databases/seforim.db").absolutePath, installed)
                        assertEquals("books", File(installed).readText())
                        val fingerprint =
                            java.util.Properties().apply {
                                File(installed).resolveSibling(".seforim.db.sha256-cache").inputStream().use { load(it) }
                            }
                        val expectedDigest =
                            java.security.MessageDigest
                                .getInstance("SHA-256")
                                .digest("books".toByteArray())
                                .joinToString("") { "%02x".format(it) }
                        assertEquals(expectedDigest, fingerprint.getProperty("sha256"))
                        assertEquals(pdf, File(install, "databases/תלמוד בבלי/ברכות.pdf").exists())
                        assertEquals(vectors, File(install, "databases/seforim.db.semantic/model/tokenizer.json").exists())
                        assertTrue(archive.exists())
                    }
                }
            }
        }

    @Test
    fun `imports more than two compressed parts in numeric order`() =
        runBlocking {
            withInstallRoot { root, source ->
                selectInstallationRoot(root)
                val archive = File(source, "seforim_bundle.tar.zst")
                writeBundle(archive, mapOf("seforim.db" to "books", "lexical.db" to "dictionary"))
                val bytes = archive.readBytes()
                val parts =
                    bytes.toList().chunked((bytes.size + 2) / 3).mapIndexed { index, chunk ->
                        File(source, "seforim_bundle.tar.zst.part%02d".format(index + 1)).apply {
                            writeBytes(chunk.toByteArray())
                        }
                    }
                assertEquals(3, parts.size)
                val installed = ExtractUseCase().extractToDatabase(parts.first().path) {}
                assertEquals("books", File(installed).readText())
                assertTrue(parts.all { it.exists() })
            }
        }

    @Test
    fun `imports concatenated database pdf and semantic frames including split boundaries`() =
        runBlocking {
            withInstallRoot { root, source ->
                val base = File(source, "base.tar.zst")
                val pdf = File(source, "pdf.tar.zst")
                val semantic = File(source, "semantic.tar.zst")
                writeBundle(base, mapOf("seforim.db" to "books", "lexical.db" to "dictionary"))
                writeBundle(pdf, mapOf("תלמוד בבלי/ברכות.pdf" to "pdf"))
                writeBundle(semantic, mapOf("model/tokenizer.json" to "tokenizer", "index/shard-00/segments" to "vectors"))
                val bytes = base.readBytes() + pdf.readBytes() + semantic.readBytes()
                val combined = File(source, "combined.tar.zst").apply { writeBytes(bytes) }
                val parts =
                    bytes.toList().chunked(37).mapIndexed { index, chunk ->
                        File(source, "combined.tar.zst.part%02d".format(index + 1)).apply {
                            writeBytes(chunk.toByteArray())
                        }
                    }
                for ((index, archive) in listOf(combined, parts.first()).withIndex()) {
                    val install = File(root, "concatenated-$index").apply { mkdirs() }
                    selectInstallationRoot(install)
                    val installed = ExtractUseCase().extractToDatabase(archive.path) {}
                    assertEquals("books", File(installed).readText())
                    val directory = File(installed).parentFile
                    assertEquals("pdf", File(directory, "תלמוד בבלי/ברכות.pdf").readText())
                    assertEquals("tokenizer", File(directory, "seforim.db.semantic/model/tokenizer.json").readText())
                    assertEquals("vectors", File(directory, "seforim.db.semantic/index/shard-00/segments").readText())
                }
            }
        }

    @Test
    fun `replaces all old shards and stale text index commits for single and split archives`() =
        runBlocking {
            withInstallRoot { root, source ->
                val archive = File(source, "upgrade.tar.zst")
                writeBundle(
                    archive,
                    mapOf(
                        "seforim.db" to "books",
                        "seforim.db.semantic/index/shard-00/segments_1" to "int8",
                        "seforim.db.lucene/segments_1" to "text",
                    ),
                )
                val parts =
                    archive.readBytes().toList().chunked(37).mapIndexed { index, bytes ->
                        File(source, "upgrade.tar.zst.part%02d".format(index + 1)).apply { writeBytes(bytes.toByteArray()) }
                    }
                for ((index, selected) in listOf(archive, parts.first()).withIndex()) {
                    val install = File(root, "upgrade-$index").apply { mkdirs() }
                    selectInstallationRoot(install)
                    val data = File(install, "databases")
                    repeat(8) { shard ->
                        File(data, "seforim.db.semantic/index/shard-%02d/segments_999".format(shard)).apply {
                            parentFile.mkdirs()
                            writeText("old")
                        }
                    }
                    File(data, "seforim.db.lucene/segments_999").apply {
                        parentFile.mkdirs()
                        writeText("old")
                    }
                    ExtractUseCase().extractToDatabase(selected.path) {}
                    assertEquals(listOf("shard-00"), File(data, "seforim.db.semantic/index").list()!!.toList())
                    assertEquals(listOf("segments_1"), File(data, "seforim.db.lucene").list()!!.toList())
                    assertFalse(File(data, "seforim.db.semantic/index/shard-00/segments_999").exists())
                    assertFalse(data.list()!!.any { it.startsWith(".index-install-") })
                }
            }
        }

    @Test
    fun `keeps old indexes when an archive fails validation`() =
        runBlocking {
            withInstallRoot { root, source ->
                selectInstallationRoot(root)
                val old =
                    File(root, "databases/seforim.db.semantic/index/shard-07/segments_999").apply {
                        parentFile.mkdirs()
                        writeText("old")
                    }
                val archive = File(source, "failed.tar.zst")
                writeBundle(
                    archive,
                    linkedMapOf(
                        "seforim.db" to "books",
                        "seforim.db.semantic/index/shard-00/segments_1" to "new",
                        "../escaped.db" to "unsafe",
                    ),
                )
                assertFailsWith<IllegalArgumentException> { ExtractUseCase().extractToDatabase(archive.path) {} }
                assertEquals("old", old.readText())
                assertFalse(File(root, "databases/seforim.db.semantic/index/shard-00").exists())
            }
        }

    @Test
    fun `rejects archive entries outside the installation root`() =
        runBlocking {
            withInstallRoot { root, source ->
                selectInstallationRoot(root)
                val archive = File(source, "unsafe.tar.zst")
                writeBundle(archive, mapOf("../escaped.db" to "unsafe"))
                assertFailsWith<IllegalArgumentException> { ExtractUseCase().extractToDatabase(archive.path) {} }
                assertFalse(File(root, "escaped.db").exists())
            }
        }

    private suspend fun withInstallRoot(block: suspend (File, File) -> Unit) {
        val previous = AppSettings.getDatabasePath()
        val directory = createTempDirectory("zayita-extract-test").toFile()
        try {
            block(File(directory, "root").apply { mkdirs() }, File(directory, "source").apply { mkdirs() })
        } finally {
            AppSettings.setDatabasePath(previous)
            resetDatabasePathCache()
            directory.deleteRecursively()
        }
    }

    private fun writeBundle(
        archive: File,
        entries: Map<String, String>,
    ) {
        TarArchiveOutputStream(ZstdOutputStream(archive.outputStream())).use { tar ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }
}
