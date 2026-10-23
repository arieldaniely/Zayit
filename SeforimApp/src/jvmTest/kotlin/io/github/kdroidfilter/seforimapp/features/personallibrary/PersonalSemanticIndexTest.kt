package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimlibrary.search.VectorSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PersonalSemanticIndexTest {
    @Test
    fun buildsCompatibleVectorsWithStableNegativeIds() =
        runTest {
            val root = fixture()
            val output = root.resolve("personal-semantic-test")
            try {
                val vector = FloatArray(256) { if (it == 0) 1f else 0f }
                buildPersonalSemanticIndex(root.resolve("personal.db"), root, output, { vector })
                VectorSearcher(output, 256, root.resolve("personal.db"), root).use { search ->
                    assertEquals(listOf(-10L), search.search(vector, 5, bookIds = listOf(-7)).map { it.lineId })
                    assertEquals(emptyList(), search.search(vector, 5, bookIds = listOf(-8)))
                }
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun cancellationDoesNotPublishPartialIndex() =
        runTest {
            val root = fixture()
            val output = root.resolve("personal-semantic-cancel")
            try {
                assertFailsWith<CancellationException> {
                    buildPersonalSemanticIndex(root.resolve("personal.db"), root, output, { throw CancellationException() })
                }
                assertFalse(Files.exists(output))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun rejectsInvalidEmbeddingWithoutPublishing() =
        runTest {
            val root = fixture()
            val output = root.resolve("personal-semantic-invalid")
            try {
                assertFailsWith<IllegalArgumentException> {
                    buildPersonalSemanticIndex(root.resolve("personal.db"), root, output, { FloatArray(256) })
                }
                assertFalse(Files.exists(output))
            } finally {
                root.toFile().deleteRecursively()
            }
        }

    private fun fixture() =
        createTempDirectory("personal-vectors").also { root ->
            Files.writeString(root.resolve("seforim-embed-round2-int8.onnx"), "test-model")
            Files.writeString(root.resolve("tokenizer.json"), "test-tokenizer")
            DriverManager.getConnection("jdbc:sqlite:${root.resolve("personal.db")}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE book(id INTEGER PRIMARY KEY,isBaseBook INTEGER)")
                    statement.execute("CREATE TABLE line(id INTEGER PRIMARY KEY,bookId INTEGER,content TEXT)")
                    statement.execute("INSERT INTO book VALUES(-7,1)")
                    statement.execute("INSERT INTO line VALUES(-10,-7,'תוכן אישי לחיפוש')")
                }
            }
        }
}
