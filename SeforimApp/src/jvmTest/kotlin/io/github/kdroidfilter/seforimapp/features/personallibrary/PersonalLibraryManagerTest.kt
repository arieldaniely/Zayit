package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.PropertiesSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PersonalLibraryManagerTest {
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
