package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalLibraryServiceTest {
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
            val catalogCache = mockk<CatalogCache>(relaxed = true)
            val databasePathProvider =
                mockk<DatabasePathProvider> {
                    every { get() } returns "base.db"
                }
            try {
                val service = PersonalLibraryService(manager, overlay, search, repository, databasePathProvider, catalogCache)
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
                verify(exactly = 1) { catalogCache.reloadCatalog() }
            } finally {
                release.complete(Unit)
            }
        }
}
