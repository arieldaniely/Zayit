package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookInformationTest {
    @Test
    fun olderDatabaseStillShowsBookWithoutEditionTables() =
        runTest {
            val repository = mockk<SeforimRepository>()
            val book = mockk<Book>()
            coEvery { repository.getBook(7) } returns book
            coEvery { repository.getBookEditions(7) } throws IllegalStateException("no such table")
            assertEquals(BookInformation(book, emptyList()), loadBookInformation(repository, 7))
            coVerify(exactly = 0) { repository.getAuthorDetails(any()) }
        }

    @Test
    fun sourcesOnlyOpenWebLinks() {
        assertEquals("https://example.com/book", informationSourceUrl("https://example.com/book"))
        assertNull(informationSourceUrl("file:///C:/private"))
        assertNull(informationSourceUrl("javascript:alert(1)"))
        assertNull(informationSourceUrl(null))
    }
}
