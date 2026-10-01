package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import com.github.luben.zstd.ZstdOutputStream
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.pdf.TalmudPdfService
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.database.selectInstallationRoot
import io.github.kdroidfilter.seforimapp.testAppSettings
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
            withInstallRoot { root, source, appSettings, pathProvider, pdfService ->
                for (pdf in listOf(false, true)) {
                    for (vectors in listOf(false, true)) {
                        val install = File(root, "variant-$pdf-$vectors").apply { mkdirs() }
                        selectInstallationRoot(appSettings, install)
                        val entries = linkedMapOf("seforim.db" to "books", "lexical.db" to "dictionary")
                        if (pdf) entries["תלמוד בבלי/ברכות.pdf"] = "pdf"
                        if (vectors) entries["seforim.db.semantic/model/tokenizer.json"] = "tokenizer"
                        val archive = File(source, "variant-$pdf-$vectors.tar.zst")
                        writeBundle(archive, entries)
                        val installed = ExtractUseCase(appSettings, pathProvider, pdfService).extractToDatabase(archive.path) {}
                        assertEquals(File(install, "databases/seforim.db").absolutePath, installed)
                        assertEquals("books", File(installed).readText())
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
            withInstallRoot { root, source, appSettings, pathProvider, pdfService ->
                selectInstallationRoot(appSettings, root)
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
                val installed = ExtractUseCase(appSettings, pathProvider, pdfService).extractToDatabase(parts.first().path) {}
                assertEquals("books", File(installed).readText())
                assertTrue(parts.all { it.exists() })
            }
        }

    @Test
    fun `rejects archive entries outside the installation root`() =
        runBlocking {
            withInstallRoot { root, source, appSettings, pathProvider, pdfService ->
                selectInstallationRoot(appSettings, root)
                val archive = File(source, "unsafe.tar.zst")
                writeBundle(archive, mapOf("../escaped.db" to "unsafe"))
                assertFailsWith<IllegalArgumentException> {
                    ExtractUseCase(appSettings, pathProvider, pdfService).extractToDatabase(archive.path) {}
                }
                assertFalse(File(root, "escaped.db").exists())
            }
        }

    private suspend fun withInstallRoot(
        block: suspend (File, File, AppSettings, DatabasePathProvider, TalmudPdfService) -> Unit,
    ) {
        val appSettings = testAppSettings()
        val databasePathProvider = DatabasePathProvider(appSettings)
        val talmudPdfService = TalmudPdfService(databasePathProvider)
        val directory = createTempDirectory("zayita-extract-test").toFile()
        try {
            block(
                File(directory, "root").apply { mkdirs() },
                File(directory, "source").apply { mkdirs() },
                appSettings,
                databasePathProvider,
                talmudPdfService,
            )
        } finally {
            databasePathProvider.reset()
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
