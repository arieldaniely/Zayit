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
    fun `keeps merged searches and inverse cross-library discovery on union views`() {
        val titleSearch = "SELECT * FROM book WHERE title LIKE ? ORDER BY title LIMIT ?"
        val links = "SELECT l.* FROM link l JOIN line sl ON l.sourceLineId = sl.id WHERE l.targetLineId IN (?)"
        assertEquals(titleSearch, PersonalLibraryQueryRouter.route(titleSearch, "%test%", attached = true))
        assertEquals(links, PersonalLibraryQueryRouter.route(links, -11L, attached = true))
    }

    @Test
    fun `routes base commentary joins directly`() {
        val sql =
            "SELECT l.*, ct.name, b.title, tl.content FROM link l " +
                "JOIN connection_type ct ON l.connectionTypeId = ct.id " +
                "JOIN book b ON l.targetBookId = b.id JOIN line tl ON l.targetLineId = tl.id " +
                "WHERE l.sourceLineId IN (?) AND l.targetBookId IN (?)"
        val routed = PersonalLibraryQueryRouter.route(sql, mapOf(0 to 12L, 1 to 34L), attached = true)
        assertTrue("FROM main.\"link\" l" in routed)
        assertTrue("JOIN main.\"connection_type\" ct" in routed)
        assertTrue("JOIN main.\"book\" b" in routed)
        assertTrue("JOIN main.\"line\" tl" in routed)
    }

    @Test
    fun `routes personal link with base commentary target across schemas`() {
        val sql =
            "SELECT l.*, ct.name, b.title, tl.content FROM link l " +
                "JOIN connection_type ct ON l.connectionTypeId = ct.id " +
                "JOIN book b ON l.targetBookId = b.id JOIN line tl ON l.targetLineId = tl.id " +
                "WHERE l.sourceLineId IN (?, ?) AND l.targetBookId IN (?)"
        val routed =
            PersonalLibraryQueryRouter.route(
                sql,
                mapOf(0 to -12L, 1 to -13L, 2 to 34L),
                attached = true,
            )
        assertTrue("FROM personal.\"link\" l" in routed)
        assertTrue("JOIN main.\"connection_type\" ct" in routed)
        assertTrue("JOIN main.\"book\" b" in routed)
        assertTrue("JOIN main.\"line\" tl" in routed)
    }

    @Test
    fun `routes inverse navigation by personal source book`() {
        val sql =
            "SELECT l.sourceLineId FROM link l " +
                "WHERE l.targetLineId IN (?, ?) AND l.sourceBookId = ? LIMIT 1"
        val routed =
            PersonalLibraryQueryRouter.route(
                sql,
                mapOf(0 to 10L, 1 to 11L, 2 to -42L),
                attached = true,
            )
        assertTrue("FROM personal.\"link\" l" in routed)
    }

    @Test
    fun `personal target forces personal link storage even for a base source`() {
        val sql =
            "SELECT l.targetLineId FROM link l " +
                "WHERE l.sourceLineId IN (?) AND l.targetBookId = ? LIMIT 1"
        val routed =
            PersonalLibraryQueryRouter.route(
                sql,
                mapOf(0 to 10L, 1 to -42L),
                attached = true,
            )
        assertTrue("FROM personal.\"link\" l" in routed)
    }

    @Test
    fun `does not route while overlay is detached`() {
        val sql = "SELECT * FROM line WHERE id = ?"
        assertEquals(sql, PersonalLibraryQueryRouter.route(sql, -1L, attached = false))
    }
}
