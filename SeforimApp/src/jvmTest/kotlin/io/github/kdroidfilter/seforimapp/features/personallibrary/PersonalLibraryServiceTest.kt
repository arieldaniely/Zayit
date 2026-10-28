package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalLibraryServiceTest {
    @Test
    fun importingBooksNeverLoadsTheSemanticModelEvenWithAnOutdatedSemanticIndex() =
        runBlocking {
            val temporary = Files.createTempDirectory("personal-lexical-import")
            val previousDatabase = PersonalLibraryRuntime.activeDatabase
            val engine = slot<SearchEngine>()
            mockkObject(CatalogCache)
            mockkStatic(::getDatabasePath)
            try {
                val base = temporary.resolve("base.db")
                PersistentSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
                val books = Files.createDirectory(temporary.resolve("books"))
                Files.writeString(books.resolve("book.txt"), "שורה ראשונה\nשורה שנייה")
                val manager = PersonalLibraryManager(PropertiesSettings(Properties()), base, temporary.resolve("library"))
                val overlay = mockk<PersonalLibraryOverlay>(relaxed = true)
                val search = mockk<CompositeSearchEngine>(relaxed = true)
                every { search.replacePersonal(capture(engine)) } returns Unit
                every { getDatabasePath() } returns base.toString()
                every { CatalogCache.reloadCatalog() } returns Unit
                val service = PersonalLibraryService(manager, overlay, search, mockk<SeforimRepository>())
                val configuration =
                    PersonalLibraryConfiguration(
                        folders = listOf(PersonalBookFolder("books", books.toString(), "Books")),
                    )

                service.requestSynchronize(configuration)
                assertTrue(service.state.value.success)
                assertFalse(service.state.value.semanticWorking)
                assertEquals(
                    1,
                    service
                        .configuration()
                        .folders
                        .single()
                        .lastBookCount,
                )
                assertTrue(engine.captured is LuceneSearchEngine)
                engine.captured.close()

                val outdated =
                    service.configuration().copy(
                        semanticEnabled = true,
                        semanticGeneration = "old-generation",
                        semanticDirectory = temporary.resolve("missing-semantic-index").toString(),
                    )
                service.requestSynchronize(outdated, force = true)
                assertTrue(service.state.value.success)
                assertTrue(engine.captured is LuceneSearchEngine)
                coVerify(exactly = 0) { search.sharedEmbedder() }
                Files.walk(temporary).use { paths ->
                    assertFalse(paths.anyMatch { it.fileName.toString().startsWith("personal-semantic-") })
                }
            } finally {
                if (engine.isCaptured) engine.captured.close()
                PersonalLibraryRuntime.activeDatabase = previousDatabase
                unmockkObject(CatalogCache)
                unmockkStatic(::getDatabasePath)
                temporary.toFile().deleteRecursively()
            }
        }

    @Test
    fun closingCallerDuringImportStillPublishesSuccessfulGeneration() =
        runBlocking {
            val original = PersonalLibraryConfiguration()
            val updated = original.copy(activeGeneration = "imported")
            val manager = mockk<PersonalLibraryManager>()
            every { manager.store.load() } returns original
            val overlay = mockk<PersonalLibraryOverlay>(relaxed = true)
            val search = mockk<CompositeSearchEngine>(relaxed = true)
            val repository = mockk<SeforimRepository>()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            every { manager.synchronize(any(), any(), any()) } answers {
                started.complete(Unit)
                runBlocking { release.await() }
                updated to null
            }
            mockkObject(CatalogCache)
            mockkStatic(::getDatabasePath)
            try {
                every { CatalogCache.reloadCatalog() } returns Unit
                every { getDatabasePath() } returns "base.db"
                val service = PersonalLibraryService(manager, overlay, search, repository)
                val job = launch(Dispatchers.Default) { service.requestSynchronize(original) }
                withTimeout(10_000) { started.await() }
                job.cancel()
                release.complete(Unit)
                withTimeout(10_000) { job.join() }
                assertTrue(service.state.value.success)
                assertFalse(service.state.value.isWorking)
                assertEquals(null, service.state.value.error)
                assertEquals(updated, service.state.value.configuration)
                verify(exactly = 1) { overlay.attach(null) }
                verify(exactly = 1) { search.replacePersonal(null) }
                verify(exactly = 1) { CatalogCache.reloadCatalog() }
            } finally {
                release.complete(Unit)
                unmockkObject(CatalogCache)
                unmockkStatic(::getDatabasePath)
            }
        }
}
