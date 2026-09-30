package io.github.kdroidfilter.seforimapp.features.search

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SemanticBundleStampTest {
    @Test
    fun `stamp includes the complete database path and detects database and bundle changes`() {
        val directory = Files.createTempDirectory("semantic-stamp-")
        try {
            val database = Files.write(directory.resolve("seforim.db"), byteArrayOf(1))
            val root = Files.createDirectory(directory.resolve("seforim.db.semantic"))
            val model = Files.write(root.resolve("model.onnx"), byteArrayOf(2))
            val initial = semanticBundleStamp(root, database)
            assertEquals(2, initial.size)
            assertTrue(initial.any { it.startsWith("$database:") })
            assertTrue(initial.any { it.startsWith("$model:") })
            assertEquals(initial, semanticBundleStamp(root, database))

            Files.write(database, byteArrayOf(1, 2))
            val databaseChanged = semanticBundleStamp(root, database)
            assertNotEquals(initial, databaseChanged)

            Files.write(model, byteArrayOf(2, 3))
            assertNotEquals(databaseChanged, semanticBundleStamp(root, database))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
