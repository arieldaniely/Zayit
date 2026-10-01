package io.github.kdroidfilter.seforimapp.features.personallibrary

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PersonalBookReaderTest {
    @Test
    fun readsDocxParagraphsTablesAndHeadingStyles() {
        val file = Files.createTempFile("personal-book", ".DOCX")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry("word/styles.xml"))
                zip.write(
                    """
                    <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                        <w:style w:styleId="CustomHeading"><w:pPr><w:outlineLvl w:val="1"/></w:pPr></w:style>
                    </w:styles>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(
                    """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                        <w:p><w:pPr><w:pStyle w:val="CustomHeading"/></w:pPr><w:r><w:t>פרק א</w:t></w:r></w:p>
                        <w:p><w:r><w:t>טקסט &lt;בדיקה&gt;</w:t><w:tab/><w:t>נוסף</w:t><w:br/><w:t>סוף</w:t></w:r></w:p>
                        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>בתוך טבלה</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                    </w:body></w:document>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            assertEquals(
                listOf("<h2>פרק א</h2>", "טקסט &lt;בדיקה&gt; נוסף<br>סוף", "בתוך טבלה"),
                readPersonalBook(file),
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun rejectsDocxWithExternalEntities() {
        val file = Files.createTempFile("personal-book-entities", ".docx")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("<!DOCTYPE document [<!ENTITY secret SYSTEM 'file:///private'>]><document>&secret;</document>".toByteArray())
                zip.closeEntry()
            }
            assertFails { readPersonalBook(file) }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
