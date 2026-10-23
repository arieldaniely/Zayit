package io.github.kdroidfilter.seforimapp.features.search

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SemanticBundleValidationCacheTest {
    @Test
    fun unchangedBundleDoesNotReopenIndexesAcrossLaunches() =
        withBundle { root, database ->
            var validations = 0
            repeat(3) {
                SemanticBundleValidationCache().validate(root, database) { validations++ }
            }
            assertEquals(1, validations)
        }

    @Test
    fun databaseOrIndexChangesRequireValidationAgain() =
        withBundle { root, database ->
            val cache = SemanticBundleValidationCache()
            var validations = 0

            fun validate() = cache.validate(root, database) { validations++ }
            validate()
            Files.write(database, byteArrayOf(1, 2))
            validate()
            val shard = Files.write(root.resolve("shard-1"), byteArrayOf(3))
            validate()
            Files.delete(shard)
            validate()
            assertEquals(4, validations)
        }

    @Test
    fun failedValidationIsNeverCached() =
        withBundle { root, database ->
            repeat(2) {
                assertFailsWith<IllegalStateException> {
                    SemanticBundleValidationCache().validate(root, database) { error("Invalid index") }
                }
            }
        }

    @Test
    fun filesChangedDuringValidationCannotBePublishedAsReady() =
        withBundle { root, database ->
            assertFailsWith<IllegalStateException> {
                SemanticBundleValidationCache().validate(root, database) {
                    Files.write(database, byteArrayOf(1, 2))
                }
            }
            var validations = 0
            SemanticBundleValidationCache().validate(root, database) { validations++ }
            assertEquals(1, validations)
        }

    @Test
    fun fingerprintSidecarsDoNotTriggerAnotherIndexOpen() =
        withBundle { root, database ->
            var validations = 0
            SemanticBundleValidationCache().validate(root, database) {
                validations++
                Files.writeString(root.resolve(".model.onnx.sha256-cache"), "optional hash cache")
            }
            SemanticBundleValidationCache().validate(root, database) { validations++ }
            assertEquals(1, validations)
        }

    @Test
    fun unavailableDiskCacheStillAllowsSuccessfulValidation() =
        withBundle { root, database ->
            Files.createDirectory(root.resolveSibling(".${root.fileName}.validated-v1"))
            val cache = SemanticBundleValidationCache()
            var validations = 0
            repeat(2) { cache.validate(root, database) { validations++ } }
            assertEquals(1, validations)
        }

    private fun withBundle(block: (Path, Path) -> Unit) {
        val directory = Files.createTempDirectory("semantic-validation-")
        try {
            val database = Files.write(directory.resolve("seforim.db"), byteArrayOf(1))
            val root = Files.createDirectory(directory.resolve("seforim.db.semantic"))
            Files.write(root.resolve("model.onnx"), byteArrayOf(2))
            block(root, database)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
