package io.github.kdroidfilter.seforimapp.features.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PdfPreindexStoreTest {
    @Test
    fun persistsPositionsAcrossSessionsAndInvalidatesChangedFiles() {
        val temp = Files.createTempDirectory("pdf-preindex")
        try {
            val file = temp.resolve("book.pdf").toFile()
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    content.newLineAtOffset(50f, 700f)
                    content.showText("Indexed")
                    content.endText()
                }
                document.addPage(PDPage())
                document.save(file)
            }
            val cache = temp.resolve("index")
            PdfPreindexStore(cache).prepare(file)
            val reopened = PdfPreindexStore(cache)
            assertEquals("Indexed", assertNotNull(reopened.page(file, 0)).glyphs.joinToString("") { it.text })
            assertEquals(emptyList(), assertNotNull(reopened.page(file, 1)).glyphs)
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(file.lastModified() + 2_000))
            assertNull(reopened.page(file, 0))
            reopened.prepare(file)
            assertNotNull(reopened.page(file, 0))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
