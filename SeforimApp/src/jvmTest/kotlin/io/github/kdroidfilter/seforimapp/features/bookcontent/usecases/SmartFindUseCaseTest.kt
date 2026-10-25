package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SmartFindUseCaseTest {
    @Test
    fun dictionaryMarksArriveBeforeSemanticInferenceCompletes() =
        runTest {
            val engine = mockk<SearchEngine>()
            every { engine.buildHighlightTerms("חכם") } returns listOf("חכם", "נבון")
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { engine.semanticFind("חכם", 7, 150) } coAnswers {
                started.complete(Unit)
                release.await()
                listOf(10)
            }
            coEvery { engine.passagesInBookByMeaning("חכם", listOf("הוא מבין דבר מתוך דבר")) } returns listOf("מבין דבר מתוך דבר")
            val updates = mutableListOf<SmartFindHighlights>()
            val job =
                launch {
                    SmartFindUseCase(engine)
                        .search(7, "חכם", flowOf(listOf(Line(10, 7, 1, "הוא מבין דבר מתוך דבר"))))
                        .take(4)
                        .toList(updates)
                }
            started.await()
            assertTrue(updates.last().terms.contains("נבון"))
            assertTrue(updates.last().passages.isEmpty())
            release.complete(Unit)
            job.join()
            assertEquals("מבין דבר מתוך דבר", updates.last().passages[10])
        }

    @Test
    fun inferenceFailureKeepsTheDictionaryMarks() =
        runTest {
            val engine = mockk<SearchEngine>()
            every { engine.buildHighlightTerms("חכם") } returns listOf("חכם", "נבון")
            coEvery { engine.semanticFind(any(), any(), any()) } returns listOf(10)
            coEvery { engine.passagesInBookByMeaning(any(), any()) } throws IllegalStateException("Model unavailable")
            val updates =
                SmartFindUseCase(engine)
                    .search(7, "חכם", flowOf(listOf(Line(10, 7, 1, "טקסט"))))
                    .toList()
            assertEquals(listOf("חכם", "נבון"), updates.last().terms)
        }

    @Test
    fun cancellationPropagatesWithoutPublishingAnObsoletePassage() =
        runTest {
            val engine = mockk<SearchEngine>()
            every { engine.buildHighlightTerms(any()) } returns listOf("חכם")
            coEvery { engine.semanticFind(any(), any(), any()) } throws CancellationException("Query changed")
            assertFailsWith<CancellationException> {
                SmartFindUseCase(engine).search(7, "חכם", flowOf(emptyList())).toList()
            }
            coVerify(exactly = 0) { engine.passagesInBookByMeaning(any(), any()) }
        }

    @Test
    fun shortQueriesDoNotLoadTheDictionaryOrModel() =
        runTest {
            val engine = mockk<SearchEngine>()
            SmartFindUseCase(engine).search(7, "א", flowOf(emptyList())).toList()
            verify(exactly = 0) { engine.buildHighlightTerms(any()) }
            coVerify(exactly = 0) { engine.semanticFind(any(), any(), any()) }
        }
}
