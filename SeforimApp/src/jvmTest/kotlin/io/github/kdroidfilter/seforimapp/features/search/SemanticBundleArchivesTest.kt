package io.github.kdroidfilter.seforimapp.features.search

import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SemanticBundleArchivesTest {
    @Test
    fun `selecting the first part discovers and extracts the complete archive`() =
        withDirectory { directory ->
            val content = Random(42).nextBytes(8192)
            val parts = writeSplitArchive(directory, content)
            writeManifest(directory)
            val resolved = resolveSemanticBundleArchives(listOf(parts.first()))
            assertEquals(parts, resolved)

            val stage = Files.createDirectory(directory.resolve("stage"))
            SemanticAssetsManager.extractTarZstd(resolved, stage)
            assertContentEquals(content, Files.readAllBytes(stage.resolve("model/tokenizer.json")))
        }

    @Test
    fun `a missing final part is identified from the manifest before extraction`() =
        withDirectory { directory ->
            val parts = writeSplitArchive(directory, Random(42).nextBytes(8192))
            writeManifest(directory)
            Files.delete(parts.last())

            val failure =
                assertFailsWith<SemanticBundleImportException> {
                    resolveSemanticBundleArchives(listOf(parts.first()))
                }
            assertEquals(SemanticBundleImportProblem.MISSING_PARTS, failure.problem)
            assertEquals(listOf("semantic-bundle.tar.zst.part04"), failure.missingParts)
        }

    @Test
    fun `a missing middle part is identified without a manifest`() =
        withDirectory { directory ->
            val parts = writeSplitArchive(directory, Random(42).nextBytes(8192))
            Files.delete(parts[1])

            val failure =
                assertFailsWith<SemanticBundleImportException> {
                    resolveSemanticBundleArchives(listOf(parts.first()))
                }
            assertEquals(listOf("semantic-bundle.tar.zst.part02"), failure.missingParts)
        }

    @Test
    fun `a truncated final part produces an actionable archive error`() =
        withDirectory { directory ->
            val parts = writeSplitArchive(directory, Random(42).nextBytes(8192))
            Files.delete(parts.last())
            val stage = Files.createDirectory(directory.resolve("stage"))

            val failure =
                assertFailsWith<SemanticBundleImportException> {
                    SemanticAssetsManager.extractTarZstd(resolveSemanticBundleArchives(listOf(parts.first())), stage)
                }
            assertEquals(SemanticBundleImportProblem.DAMAGED_ARCHIVE, failure.problem)
        }

    @Test
    fun `unsplit archives keep their existing import path`() =
        withDirectory { directory ->
            val archive = Files.createFile(directory.resolve("semantic-bundle.tar.zst"))
            assertEquals(listOf(archive), resolveSemanticBundleArchives(listOf(archive)))
        }

    @Test
    fun `multiple selected parts are deduplicated and sorted numerically`() =
        withDirectory { directory ->
            val parts = writeSplitArchive(directory, Random(42).nextBytes(8192))
            assertEquals(parts, resolveSemanticBundleArchives(listOf(parts[3], parts[1], parts[1])))
        }

    private fun writeSplitArchive(
        directory: Path,
        content: ByteArray,
    ): List<Path> {
        val output = ByteArrayOutputStream()
        ZstdOutputStream(output).use { zstd ->
            TarArchiveOutputStream(zstd).use { tar ->
                val entry = TarArchiveEntry("model/tokenizer.json").apply { size = content.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }
        val compressed = output.toByteArray()
        return (1..4).map { number ->
            val path = directory.resolve("semantic-bundle.tar.zst.part${number.toString().padStart(2, '0')}")
            Files.write(path, compressed.copyOfRange((number - 1) * compressed.size / 4, number * compressed.size / 4))
        }
    }

    private fun writeManifest(directory: Path) {
        Files.writeString(directory.resolve("semantic-bundle.json"), """{"archiveType":"tar.zst","parts":4}""")
    }

    private fun withDirectory(action: (Path) -> Unit) {
        val directory = Files.createTempDirectory("semantic-bundle-test-")
        try {
            action(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
