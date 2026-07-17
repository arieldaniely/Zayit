package io.github.kdroidfilter.seforimapp.features.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfOutlineIndexTest {
    private val index =
        PdfOutlineIndex(
            listOf(
                PdfOutlineEntry("דף ב עמוד א", level = 0, pageIndex = 3),
                PdfOutlineEntry("דף ב עמוד ב", level = 0, pageIndex = 4),
                PdfOutlineEntry("ג.", level = 0, pageIndex = 5),
            ),
        )

    @Test
    fun `full text line reference resolves through daf and amud`() {
        assertEquals(3, index.pageFor(listOf("ברכות ב., ג")))
        assertEquals(4, index.pageFor(listOf("ברכות ב:, א")))
    }

    @Test
    fun `bookmark phrase and abbreviated text heading are equivalent`() {
        assertEquals(3, index.pageFor(listOf("ב ע״א")))
        assertEquals(5, index.pageFor(listOf("ברכות ג., ה")))
    }

    @Test
    fun `exact title matching ignores punctuation variants and final line segment`() {
        val exact = PdfOutlineIndex(listOf(PdfOutlineEntry("ברכות ד.", 0, 7)))
        assertEquals(7, exact.pageFor(listOf("ברכות ד., ב")))
    }

    @Test
    fun `visible PDF page maps back to text heading`() {
        val anchors =
            listOf(
                PdfTextAnchor("ב עמוד א", 20L),
                PdfTextAnchor("ב עמוד ב", 21L),
                PdfTextAnchor("ג.", 30L),
            )

        assertEquals(21L, index.textAnchorForPage(4, anchors)?.lineId)
        assertEquals(30L, index.textAnchorForPage(5, anchors)?.lineId)
    }

    @Test
    fun `page without its own bookmark uses nearest preceding bookmark`() {
        assertEquals("ג.", index.entryForPage(6)?.title)
    }
}
