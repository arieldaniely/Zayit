package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersonalLibraryManagerTest {
    @Test
    fun retriesSkippedFilesEvenWhenTheirSizeAndModificationTimeHaveNotChanged() {
        val temporary = Files.createTempDirectory("personal-retry-")
        val previousDatabase = PersonalLibraryRuntime.activeDatabase
        try {
            val base = temporary.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
            val books = Files.createDirectory(temporary.resolve("books"))
            val file = Files.write(books.resolve("book.txt"), byteArrayOf(0xC3.toByte(), 0x28))
            val modified = Files.getLastModifiedTime(file)
            val manager = PersonalLibraryManager(PropertiesSettings(Properties()), base, temporary.resolve("library"))
            val requested = PersonalLibraryConfiguration(listOf(PersonalBookFolder("books", books.toString(), "Books")))
            val failed = manager.synchronize(requested).first
            assertEquals(0, failed.folders.single().lastBookCount)
            assertNotNull(failed.folders.single().lastError)

            Files.writeString(file, "ok")
            Files.setLastModifiedTime(file, modified)
            val retried = manager.synchronize(failed).first
            assertEquals(failed.synchronizedFingerprint, retried.synchronizedFingerprint)
            assertNotEquals(failed.activeGeneration, retried.activeGeneration)
            assertEquals(1, retried.folders.single().lastBookCount)
            assertNull(retried.folders.single().lastError)
        } finally {
            PersonalLibraryRuntime.activeDatabase = previousDatabase
            temporary.toFile().deleteRecursively()
        }
    }

    @Test
    fun startupReusesCompatibleBooksEvenWhenTheSourceFolderIsOffline() =
        withLibrary { manager, _, folder, _ ->
            manager.synchronize()
            Files.delete(Path.of(folder.path))
            val active = assertNotNull(manager.prepareForStartup())
            assertEquals("generation", active.generation)
            assertNotNull(manager.takeStartupSynchronization())
            assertNull(manager.takeStartupSynchronization())
        }

    @Test
    fun changingTheBaseDatabaseInvalidatesThePublishedGeneration() =
        withLibrary { manager, base, _, _ ->
            manager.synchronize()
            assertNotNull(manager.compatibleStartupArtifacts())
            Files.write(base, byteArrayOf(1, 2, 3))
            assertNull(manager.compatibleStartupArtifacts())
        }

    @Test
    fun legacyInstallationsAcquireAStartupStampWithoutReimportingBooks() =
        withLibrary { manager, _, _, _ ->
            assertNull(manager.compatibleStartupArtifacts())
            assertEquals("generation", manager.prepareForStartup()?.generation)
            assertNotNull(manager.compatibleStartupArtifacts())
            assertNull(manager.takeStartupSynchronization())
        }

    @Test
    fun disabledFoldersDoNotReappearThroughTheCachedGeneration() =
        withLibrary { manager, _, folder, configuration ->
            manager.synchronize()
            manager.store.save(configuration.copy(folders = listOf(folder.copy(enabled = false))))
            assertNull(manager.prepareForStartup())
            assertNull(manager.activeArtifacts())
            assertNull(manager.takeStartupSynchronization())
        }

    private fun withLibrary(block: (PersonalLibraryManager, Path, PersonalBookFolder, PersonalLibraryConfiguration) -> Unit) {
        val temporary = Files.createTempDirectory("personal-startup-")
        val previousDatabase = PersonalLibraryRuntime.activeDatabase
        try {
            val base = Files.write(temporary.resolve("base.db"), byteArrayOf(1))
            val folder = PersonalBookFolder("books", Files.createDirectory(temporary.resolve("books")).toString(), "Books")
            val storage = temporary.resolve("library")
            val generation = Files.createDirectories(storage.resolve("generations/generation"))
            Files.write(generation.resolve("personal.db"), byteArrayOf(2))
            Files.createDirectory(generation.resolve("personal.lucene"))
            val fingerprint = PersonalLibraryImporter(base, storage.resolve("generations")).fingerprint(listOf(folder))
            val manager = PersonalLibraryManager(PropertiesSettings(Properties()), base, storage)
            val configuration = PersonalLibraryConfiguration(listOf(folder), "generation", fingerprint)
            manager.store.save(configuration)
            block(manager, base, folder, configuration)
        } finally {
            PersonalLibraryRuntime.activeDatabase = previousDatabase
            temporary.toFile().deleteRecursively()
        }
    }
}
