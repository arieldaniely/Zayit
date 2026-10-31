package io.github.kdroidfilter.seforimapp.features.personallibrary

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class PersonalBookReaderTest {
    @Test
    fun readsFootnotesEndnotesAndTheirHyperlinksAtTheReference() {
        val lines =
            readDocx(
                mapOf(
                    "word/document.xml" to
                        """
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                            <w:p><w:r><w:t>לפני</w:t><w:footnoteReference w:id="3"/><w:t>אחרי</w:t></w:r></w:p>
                            <w:p><w:r><w:endnoteReference w:id="1"/></w:r></w:p>
                            <w:p><w:r><w:t>הערה חסרה</w:t><w:footnoteReference w:id="99"/></w:r></w:p>
                        </w:body></w:document>
                        """.trimIndent(),
                    "word/footnotes.xml" to
                        """
                        <w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                            <w:footnote w:id="-1" w:type="separator"><w:p><w:r><w:t>מפריד</w:t></w:r></w:p></w:footnote>
                            <w:footnote w:id="3">
                                <w:p><w:r><w:footnoteRef/><w:t>הערה &lt;א&gt;</w:t></w:r></w:p>
                                <w:p><w:hyperlink r:id="noteLink"><w:r><w:t>מקור</w:t></w:r></w:hyperlink></w:p>
                            </w:footnote>
                        </w:footnotes>
                        """.trimIndent(),
                    "word/endnotes.xml" to
                        """
                        <w:endnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                            <w:endnote w:id="1"><w:p><w:r><w:endnoteRef/><w:t>הערת סיום</w:t></w:r></w:p></w:endnote>
                        </w:endnotes>
                        """.trimIndent(),
                    "word/_rels/footnotes.xml.rels" to
                        """
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                            <Relationship Id="noteLink" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                Target="zayita://book/2/line/42" TargetMode="External"/>
                        </Relationships>
                        """.trimIndent(),
                ),
            )
        assertEquals(
            listOf(
                "לפני<sup class=\"footnote-marker\">1</sup><i class=\"footnote\">" +
                    " 1. הערה &lt;א&gt;<br><a class=\"ref\" href=\"zayita://book/2/line/42\">מקור</a> </i>אחרי",
                "<sup class=\"footnote-marker\">2</sup><i class=\"footnote\"> 2. הערת סיום </i>",
                "הערה חסרה",
            ),
            lines,
        )
        val rendered =
            io.github.kdroidfilter.seforim.htmlparser
                .buildAnnotatedFromHtml(lines.first(), 16f)
        assertTrue(rendered.text.contains("1. הערה <א>"))
        assertTrue(rendered.getLinkAnnotations(0, rendered.length).isNotEmpty())
    }

    @Test
    fun preservesRelationshipAndFieldHyperlinksWithoutImportingInstructionsOrUnsafeTargets() {
        val lines =
            readDocx(
                mapOf(
                    "word/document.xml" to
                        """
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>
                            <w:p><w:hyperlink r:id="link"><w:r><w:t>שתי </w:t></w:r><w:r><w:t>מילים</w:t></w:r></w:hyperlink></w:p>
                            <w:p><w:fldSimple w:instr=' HYPERLINK "zayit://book/2/line/42" '>
                                <w:r><w:t>פשוט</w:t></w:r></w:fldSimple></w:p>
                            <w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r>
                                <w:r><w:instrText> HYPER</w:instrText></w:r>
                                <w:r><w:instrText>LINK "zayita://book/2/line/42" </w:instrText></w:r>
                                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                                <w:r><w:t>מורכב</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/><w:t> סוף</w:t></w:r></w:p>
                            <w:p><w:hyperlink r:id="unsafe"><w:r><w:t>טקסט רגיל</w:t></w:r></w:hyperlink></w:p>
                            <w:p><w:r><w:t>[otzaria://open/book/2?index=1]</w:t></w:r></w:p>
                        </w:body></w:document>
                        """.trimIndent(),
                    "word/_rels/document.xml.rels" to
                        """
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                            <Relationship Id="link" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                Target="otzaria://open/book/2?index=1&amp;mark=1" TargetMode="External"/>
                            <Relationship Id="unsafe" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                Target="javascript:alert(1)" TargetMode="External"/>
                        </Relationships>
                        """.trimIndent(),
                ),
            )
        assertEquals(
            listOf(
                "<a class=\"ref\" href=\"otzaria://open/book/2?index=1&amp;mark=1\">שתי מילים</a>",
                "<a class=\"ref\" href=\"zayit://book/2/line/42\">פשוט</a>",
                "<a class=\"ref\" href=\"zayita://book/2/line/42\">מורכב</a> סוף",
                "טקסט רגיל",
                "[otzaria://open/book/2?index=1]",
            ),
            lines,
        )
    }

    private fun readDocx(entries: Map<String, String>): List<String> {
        val file = Files.createTempFile("personal-book-notes-links", ".docx")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                entries.forEach { (name, xml) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(xml.toByteArray())
                    zip.closeEntry()
                }
            }
            return readPersonalBook(file)
        } finally {
            Files.deleteIfExists(file)
        }
    }

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
