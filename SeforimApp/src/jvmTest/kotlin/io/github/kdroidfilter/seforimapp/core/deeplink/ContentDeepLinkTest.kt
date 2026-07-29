package io.github.kdroidfilter.seforimapp.core.deeplink

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentDeepLinkTest {
    @Test
    fun `parses otzaria book index and decoded text highlight`() {
        val parsed = parseContentDeepLink("otzaria://open/book/2?index=2&m=%D7%A9%D7%81%D6%B0%D7%9E%D7%95%D6%B9%D7%AA%D6%B9%D7%99")!!
        val destination = assertIs<TabsDestination.BookContent>(parsed.destination)

        assertEquals(2L, destination.bookId)
        assertEquals(2, parsed.lineIndex)
        assertEquals("שְׁמוֹתֹי", parsed.highlightText)
        assertFalse(parsed.markLine)
    }

    @Test
    fun `parses otzaria whole-line mark`() {
        val parsed = parseContentDeepLink("OTZARIA://OPEN/BOOK/1?index=0&mark")!!
        assertEquals(0, parsed.lineIndex)
        assertTrue(parsed.markLine)
    }

    @Test
    fun `invalid otzaria index opens book without a line`() {
        assertNull(parseContentDeepLink("otzaria://open/book/1?index=-1")!!.lineIndex)
        assertNull(parseContentDeepLink("otzaria://open/book/1?index=nope")!!.lineIndex)
    }

    @Test
    fun `ignores non-book otzaria actions`() {
        assertNull(parseContentDeepLink("otzaria://open/calendar"))
        assertNull(parseContentDeepLink("otzaria://open/pdf/1?index=2"))
    }

    @Test
    fun `keeps parsing native zayit links`() {
        val destination = parseZayitDeepLink("zayit://book/2/line/1585")
        assertEquals(1585L, assertIs<TabsDestination.BookContent>(destination).lineId)
    }
}
