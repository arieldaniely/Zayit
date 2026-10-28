package io.github.kdroidfilter.seforimapp.features.personallibrary

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalLibraryFileAvailabilityTest {
    @Test
    fun detectsCloudPlaceholdersButAllowsDownloadedAndPinnedFiles() {
        assertTrue(personalFileNeedsDownload(0x00401620))
        assertTrue(personalFileNeedsDownload(0x00001000))
        assertTrue(personalFileNeedsDownload(0x00040000))
        assertFalse(personalFileNeedsDownload(0x00100020))
        assertFalse(personalFileNeedsDownload(0x00080020))
        assertFalse(personalFileNeedsDownload(0x00000020))
    }

    @Test
    fun readsLocalFileMetadataWithoutRejectingOrdinaryBooks() {
        val folder = Files.createTempDirectory("personal-local-files")
        try {
            Files.writeString(folder.resolve("book.txt"), "טקסט מקומי")
            requireLocalPersonalFiles(listOf(PersonalBookFolder("local", folder.toString(), "Local")))
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}
