package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchResultPreviewTest {
    private val result = SearchResult(7, "Book", 10, 100, "snippet", 1.0)

    @Test
    fun loadsOnlyABoundedWindowAroundTheActualHit() =
        runTest {
            val repository = mockk<SeforimRepository>()
            coEvery { repository.getLine(10) } returns Line(10, 7, 100, "Hit")
            coEvery { repository.getLines(7, 80, 120) } returns listOf(Line(10, 7, 100, "Hit"))
            val context = loadResultPreview(repository, result, 10_000)
            assertEquals(listOf(10L), context.map { it.id })
            coVerify(exactly = 1) { repository.getLines(7, 80, 120) }
        }

    @Test
    fun refusesAResultThatPointsToAnotherBook() =
        runTest {
            val repository = mockk<SeforimRepository>()
            coEvery { repository.getLine(10) } returns Line(10, 8, 100, "Different book")
            assertFailsWith<IllegalStateException> { loadResultPreview(repository, result, 4) }
            coVerify(exactly = 0) { repository.getLines(any(), any(), any()) }
        }
}
