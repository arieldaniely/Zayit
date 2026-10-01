package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressiveSearchHighlighterTest {
    @Test
    fun `pages return immediately and highlights arrive one at a time in page order`() =
        runTest {
            val firstGate = CompletableDeferred<Unit>()
            val secondGate = CompletableDeferred<Unit>()
            val updates = mutableListOf<LineHit>()
            val session =
                FakeSession { hits ->
                    flow {
                        for (hit in hits) {
                            if (hit.lineId == 1L) firstGate.await() else secondGate.await()
                            emit(hit.copy(snippet = "<b>${hit.rawText}</b>"))
                        }
                    }
                }
            val highlighter = ProgressiveSearchHighlighter(backgroundScope, updates::add, StandardTestDispatcher(testScheduler))
            highlighter.start(session)
            val first = session.nextPage(1)!!
            highlighter.submit(session, first.hits)
            val second = session.nextPage(1)!!
            highlighter.submit(session, second.hits)
            runCurrent()
            assertEquals(listOf(1L), first.hits.map { it.lineId })
            assertEquals(listOf(2L), second.hits.map { it.lineId })
            assertTrue(updates.isEmpty())

            firstGate.complete(Unit)
            runCurrent()
            assertEquals(listOf(1L), updates.map { it.lineId })
            secondGate.complete(Unit)
            runCurrent()
            assertEquals(listOf(1L, 2L), updates.map { it.lineId })
            assertEquals("<b>text 1</b>", updates.first().snippet)
            highlighter.cancel()
        }

    @Test
    fun `replacing a session discards late updates and queued pages from the old search`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val updates = mutableListOf<LineHit>()
            val processed = mutableListOf<Long>()
            val old =
                FakeSession { hits ->
                    flow {
                        processed += hits.first().lineId
                        // Simulate native inference that cannot be interrupted mid-call.
                        withContext(NonCancellable) { gate.await() }
                        emit(hits.first().copy(snippet = "obsolete"))
                    }
                }
            val replacement = FakeSession { hits -> flow { emit(hits.first().copy(snippet = "current")) } }
            val highlighter = ProgressiveSearchHighlighter(backgroundScope, updates::add, StandardTestDispatcher(testScheduler))
            highlighter.start(old)
            highlighter.submit(old, listOf(hit(1)))
            highlighter.submit(old, listOf(hit(2)))
            runCurrent()
            highlighter.start(replacement)
            highlighter.submit(old, listOf(hit(3)))
            highlighter.submit(replacement, listOf(hit(1)))
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("current"), updates.map { it.snippet })
            assertEquals(listOf(1L), processed)
            highlighter.cancel()
        }

    @Test
    fun `failed enrichment does not prevent later pages from being highlighted`() =
        runTest {
            val updates = mutableListOf<LineHit>()
            val session =
                FakeSession { hits ->
                    flow {
                        if (hits.first().lineId == 1L) error("inference failed")
                        emit(hits.first())
                    }
                }
            val highlighter = ProgressiveSearchHighlighter(backgroundScope, updates::add, StandardTestDispatcher(testScheduler))
            highlighter.start(session)
            highlighter.submit(session, listOf(hit(1)))
            highlighter.submit(session, listOf(hit(2)))
            runCurrent()
            assertEquals(listOf(2L), updates.map { it.lineId })
            highlighter.cancel()
        }

    private class FakeSession(
        private val highlights: (List<LineHit>) -> Flow<LineHit>,
    ) : SearchSession {
        private var offset = 0L

        override suspend fun nextPage(limit: Int): SearchPage = SearchPage(listOf(hit(++offset)), 2, offset == 2L)

        override fun highlightUpdates(hits: List<LineHit>): Flow<LineHit> = highlights(hits)

        override fun close() = Unit
    }

    companion object {
        private fun hit(id: Long): LineHit = LineHit(1, "book", id, id.toInt(), "preview", 1f, "text $id")
    }
}
