package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesLibraryPathTest {
    private val repository: SeforimRepository = mockk()

    @Test
    fun `line with multi-level TOC returns Book gt Chapter gt Paragraph format`() =
        runTest {
            val lineId = 100L
            val bookTitle = "שולחן ערוך אורח חיים"

            coEvery { repository.getTocEntryIdForLine(lineId) } returns 3L
            coEvery { repository.getTocEntry(3L) } returns
                TocEntry(id = 3L, bookId = 1L, text = "סעיף א", level = 3, parentId = 2L)
            coEvery { repository.getTocEntry(2L) } returns
                TocEntry(id = 2L, bookId = 1L, text = "סימן א", level = 2, parentId = 1L)
            coEvery { repository.getTocEntry(1L) } returns
                TocEntry(id = 1L, bookId = 1L, text = "הלכות השכמת הבוקר", level = 1, parentId = null)

            val path = resolveNoteLocationPath(repository, bookTitle, lineId)
            assertEquals("שולחן ערוך אורח חיים > הלכות השכמת הבוקר > סימן א > סעיף א", path)
        }

    @Test
    fun `first TOC entry matching book title is stripped to avoid duplicate`() =
        runTest {
            val lineId = 200L
            val bookTitle = "בראשית"

            coEvery { repository.getTocEntryIdForLine(lineId) } returns 2L
            coEvery { repository.getTocEntry(2L) } returns
                TocEntry(id = 2L, bookId = 1L, text = "פרק א", level = 2, parentId = 1L)
            coEvery { repository.getTocEntry(1L) } returns
                TocEntry(id = 1L, bookId = 1L, text = "בראשית", level = 1, parentId = null)

            val path = resolveNoteLocationPath(repository, bookTitle, lineId)
            assertEquals("בראשית > פרק א", path)
        }

    @Test
    fun `line without TOC returns book title alone`() =
        runTest {
            val lineId = 300L
            val bookTitle = "ספר ללא חלוקה"

            coEvery { repository.getTocEntryIdForLine(lineId) } returns null

            val path = resolveNoteLocationPath(repository, bookTitle, lineId)
            assertEquals("ספר ללא חלוקה", path)
        }

    @Test
    fun `consecutive duplicate TOC entries are deduplicated`() =
        runTest {
            val lineId = 400L
            val bookTitle = "ספר"

            coEvery { repository.getTocEntryIdForLine(lineId) } returns 3L
            coEvery { repository.getTocEntry(3L) } returns
                TocEntry(id = 3L, bookId = 1L, text = "פסקה ב", level = 3, parentId = 2L)
            coEvery { repository.getTocEntry(2L) } returns
                TocEntry(id = 2L, bookId = 1L, text = "פרק א", level = 2, parentId = 1L)
            coEvery { repository.getTocEntry(1L) } returns
                TocEntry(id = 1L, bookId = 1L, text = "פרק א", level = 1, parentId = null)

            val path = resolveNoteLocationPath(repository, bookTitle, lineId)
            assertEquals("ספר > פרק א > פסקה ב", path)
        }

    @Test
    fun `exception in repository is caught and falls back to book title`() =
        runTest {
            val lineId = 500L
            val bookTitle = "ספר ברירת מחדל"

            coEvery { repository.getTocEntryIdForLine(lineId) } throws RuntimeException("DB error")

            val path = resolveNoteLocationPath(repository, bookTitle, lineId)
            assertEquals("ספר ברירת מחדל", path)
        }
}
