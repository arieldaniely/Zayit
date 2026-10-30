package io.github.kdroidfilter.seforimapp.features.personallibrary

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PersonalBookReaderTest {
    @Test
    fun resolvesCustomStyleInheritanceAndExplicitBodyText() {
        val file = Files.createTempFile("personal-book-styles", ".docx")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                val entries =
                    mapOf(
                        "word/styles.xml" to
                            """
                            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                                <w:style w:type="paragraph" w:styleId="Base"><w:pPr><w:outlineLvl w:val="2"/></w:pPr></w:style>
                                <w:style w:type="paragraph" w:styleId="Custom"><w:basedOn w:val="Base"/></w:style>
                                <w:style w:type="paragraph" w:styleId="Nested"><w:basedOn w:val="Custom"/></w:style>
                                <w:style w:type="paragraph" w:styleId="Plain">
                                    <w:basedOn w:val="Nested"/><w:pPr><w:outlineLvl w:val="9"/></w:pPr>
                                </w:style>
                                <w:style w:type="paragraph" w:styleId="Localized"><w:name w:val="כותרת 2"/></w:style>
                                <w:style w:type="paragraph" w:styleId="Cycle"><w:basedOn w:val="Cycle"/></w:style>
                            </w:styles>
                            """.trimIndent(),
                        "word/document.xml" to
                            """
                            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                                <w:p><w:pPr><w:pStyle w:val="Nested"/></w:pPr><w:r><w:t>ירושה</w:t></w:r></w:p>
                                <w:p><w:pPr><w:pStyle w:val="Nested"/><w:outlineLvl w:val="0"/></w:pPr>
                                    <w:r><w:t>דריסה</w:t></w:r></w:p>
                                <w:p><w:pPr><w:pStyle w:val="Nested"/><w:outlineLvl w:val="9"/></w:pPr>
                                    <w:r><w:t>רגיל בפסקה</w:t></w:r></w:p>
                                <w:p><w:pPr><w:pStyle w:val="Plain"/></w:pPr><w:r><w:t>רגיל בסגנון</w:t></w:r></w:p>
                                <w:p><w:pPr><w:pStyle w:val="Localized"/></w:pPr><w:r><w:t>עברית</w:t></w:r></w:p>
                                <w:p><w:pPr><w:pStyle w:val="Cycle"/></w:pPr><w:r><w:t>מעגל</w:t></w:r></w:p>
                            </w:body></w:document>
                            """.trimIndent(),
                    )
                entries.forEach { (name, xml) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(xml.toByteArray())
                    zip.closeEntry()
                }
            }
            assertEquals(
                listOf("<h3>ירושה</h3>", "<h1>דריסה</h1>", "רגיל בפסקה", "רגיל בסגנון", "<h2>עברית</h2>", "מעגל"),
                readPersonalBook(file),
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

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
