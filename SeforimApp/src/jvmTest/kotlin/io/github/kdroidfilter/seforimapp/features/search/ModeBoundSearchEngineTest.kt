package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeBoundSearchEngineTest {
    @Test
    fun `feedback context snapshots executed query mode and filters`() {
        var selected = SearchMode.SMART
        val engine = ModeBoundSearchEngine(RecordingEngine(), { selected }, semanticReady = { true })
        val books = mutableListOf(9L, 2L)
        val session = engine.openSession("original query", near = 7, bookIds = books, baseBookOnly = false) as FeedbackSearchSession
        selected = SearchMode.EXACT
        books.clear()
        assertEquals("original query", session.feedbackContext.query)
        assertEquals(SearchMode.SMART, session.feedbackContext.requestedMode)
        assertEquals(SearchMode.SMART, session.effectiveMode)
        assertEquals(listOf(2L, 9L), session.feedbackContext.bookIds)
        assertEquals(7, session.feedbackContext.near)
        assertEquals(false, session.feedbackContext.baseBookOnly)
        session.close()
    }

    @Test
    fun `successful semantic session keeps smart mode`() =
        runTest {
            val delegate = RecordingEngine(reportedMode = SearchMode.SMART)
            var fallback = false
            val engine = ModeBoundSearchEngine(delegate, { SearchMode.SMART }, { fallback = true }, { true })
            engine.openSession("query")?.use { it.nextPage(25) }
            assertEquals(SearchMode.SMART, delegate.requestedMode)
            assertEquals(false, fallback)
        }

    @Test
    fun `restored smart mode uses flexible when bundle validation fails`() {
        val delegate = RecordingEngine()
        var fallback = false
        val engine = ModeBoundSearchEngine(delegate, { SearchMode.SMART }, { fallback = true }, { false })
        engine.openSession("query")?.close()
        assertEquals(SearchMode.FLEXIBLE, delegate.requestedMode)
        assertEquals(true, fallback)
    }

    @Test
    fun `runtime semantic failure is reported even when there are no hits`() =
        runTest {
            val delegate = RecordingEngine(reportedMode = SearchMode.FLEXIBLE)
            var fallback = false
            val engine = ModeBoundSearchEngine(delegate, { SearchMode.SMART }, { fallback = true }, { true })
            engine.openSession("query")?.use { it.nextPage(25) }
            assertEquals(SearchMode.SMART, delegate.requestedMode)
            assertEquals(true, fallback)
        }

    @Test
    fun `unsupported line scope reports flexible rather than smart`() {
        val delegate = RecordingEngine()
        var fallback = false
        val engine = ModeBoundSearchEngine(delegate, { SearchMode.SMART }, { fallback = true }, { true })
        engine.openSession("query", lineIds = listOf(1L))?.close()
        assertEquals(SearchMode.FLEXIBLE, delegate.requestedMode)
        assertEquals(true, fallback)
    }

    @Test
    fun `explicit exact mode bypasses semantic validation`() {
        val delegate = RecordingEngine()
        val engine = ModeBoundSearchEngine(delegate, { SearchMode.EXACT }, semanticReady = { error("unexpected validation") })
        engine.openSession("query")?.close()
        assertEquals(SearchMode.EXACT, delegate.requestedMode)
    }

    private class RecordingEngine(
        private val reportedMode: SearchMode? = null,
    ) : SearchEngine {
        var requestedMode: SearchMode? = null

        override fun openSession(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
            mode: SearchMode,
        ): SearchSession {
            requestedMode = mode
            return object : SearchSession {
                override val effectiveMode = reportedMode

                override suspend fun nextPage(limit: Int): SearchPage? = null

                override fun close() = Unit
            }
        }

        override fun searchBooksByTitlePrefix(
            query: String,
            limit: Int,
        ): List<Long> = emptyList()

        override fun buildSnippet(
            rawText: String,
            query: String,
            near: Int,
        ): String = rawText

        override fun buildHighlightTerms(query: String): List<String> = emptyList()

        override fun computeFacets(
            query: String,
            near: Int,
            bookFilter: Long?,
            categoryFilter: Long?,
            bookIds: Collection<Long>?,
            lineIds: Collection<Long>?,
            baseBookOnly: Boolean,
            mode: SearchMode,
        ): SearchFacets? = null

        override fun close() = Unit
    }
}
