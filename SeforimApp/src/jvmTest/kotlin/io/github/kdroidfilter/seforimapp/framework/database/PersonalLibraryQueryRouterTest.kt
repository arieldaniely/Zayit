package io.github.kdroidfilter.seforimapp.framework.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalLibraryQueryRouterTest {
    @Test
    fun `routes base and personal entity queries directly`() {
        val sql = "SELECT charCount FROM line WHERE bookId = ? ORDER BY lineIndex"
        val base = PersonalLibraryQueryRouter.route(sql, 42L, attached = true)
        val personal = PersonalLibraryQueryRouter.route(sql, -42L, attached = true)
        assertTrue("FROM main.\"line\"" in base)
        assertTrue("FROM personal.\"line\"" in personal)
    }

    @Test
    fun `routes every table in toc joins to the same partition`() {
        val sql =
            "SELECT t.*, tt.text FROM tocEntry t JOIN tocText tt ON t.textId = tt.id " +
                "WHERE t.bookId = ?"
        val routed = PersonalLibraryQueryRouter.route(sql, -7L, attached = true)
        assertTrue("FROM personal.\"tocEntry\" t" in routed)
        assertTrue("JOIN personal.\"tocText\" tt" in routed)
    }

    @Test
    fun `keeps merged searches and cross-library links on union views`() {
        val titleSearch = "SELECT * FROM book WHERE title LIKE ? ORDER BY title LIMIT ?"
        val links = "SELECT l.* FROM link l JOIN line tl ON l.targetLineId = tl.id WHERE l.sourceLineId IN (?)"
        assertEquals(titleSearch, PersonalLibraryQueryRouter.route(titleSearch, "%test%", attached = true))
        assertEquals(links, PersonalLibraryQueryRouter.route(links, -11L, attached = true))
    }

    @Test
    fun `does not route while overlay is detached`() {
        val sql = "SELECT * FROM line WHERE id = ?"
        assertEquals(sql, PersonalLibraryQueryRouter.route(sql, -1L, attached = false))
    }
}
