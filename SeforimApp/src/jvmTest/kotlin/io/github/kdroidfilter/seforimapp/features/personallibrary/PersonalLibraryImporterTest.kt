package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalLibraryImporterTest {
    @Test
    fun resolvesLinksToPersonalBooksImportedFromALaterFolder() {
        val temp = Files.createTempDirectory("personal-library-docx-cross-folder")
        try {
            val base = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
            DriverManager.getConnection("jdbc:sqlite:$base").use { connection ->
                connection.createStatement().use { it.execute("INSERT INTO connection_type(id,name) VALUES(1,'COMMENTARY')") }
            }
            val targets = Files.createDirectory(temp.resolve("targets"))
            targets.resolve("מקור אישי.txt").writeText("טקסט המקור")
            val targetFolder = PersonalBookFolder("target", targets.toString(), "מקורות")
            val importer = PersonalLibraryImporter(base, temp.resolve("generations"))
            val (initial, _) = importer.build(listOf(targetFolder), "initial")
            val (targetBookId, targetLineId) =
                DriverManager.getConnection("jdbc:sqlite:${initial.databasePath}").use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT bookId,id FROM line").use { rows ->
                            assertTrue(rows.next())
                            rows.getLong(1) to rows.getLong(2)
                        }
                    }
                }
            val documents = Files.createDirectory(temp.resolve("documents"))
            ZipOutputStream(Files.newOutputStream(documents.resolve("פירוש אישי.docx"))).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(
                    """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                        <w:p><w:r><w:t>[zayita://book/$targetBookId/line/$targetLineId]</w:t></w:r></w:p>
                    </w:body></w:document>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            val documentFolder = PersonalBookFolder("document", documents.toString(), "פירושים")
            val (artifacts, summaries) = importer.build(listOf(documentFolder, targetFolder), "linked")
            assertEquals(1, summaries.getValue("document").links)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT sourceBookId,sourceLineId,targetBookId FROM link").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(targetBookId, rows.getLong(1))
                        assertEquals(targetLineId, rows.getLong(2))
                        assertTrue(rows.getLong(3) < 0 && rows.getLong(3) != targetBookId)
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun importsDocxFootnotesAndEmbeddedOrBracketedLinksAsSources() {
        val temp = Files.createTempDirectory("personal-library-docx-links")
        try {
            val base = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
            DriverManager.getConnection("jdbc:sqlite:$base").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("INSERT INTO source(id,name) VALUES(1,'base')")
                    statement.execute("INSERT INTO category(id,title,level) VALUES(1,'בסיס',0)")
                    statement.execute("INSERT INTO book(id,categoryId,sourceId,title) VALUES(1,1,1,'מקור')")
                    statement.execute("INSERT INTO book(id,categoryId,sourceId,title) VALUES(2,1,1,'ספר אחר')")
                    statement.execute("INSERT INTO line(id,bookId,lineIndex,content) VALUES(101,1,0,'תחילה')")
                    statement.execute("INSERT INTO line(id,bookId,lineIndex,content) VALUES(103,1,3,'המשך')")
                    statement.execute("INSERT INTO line(id,bookId,lineIndex,content) VALUES(201,2,0,'אחר')")
                    statement.execute("INSERT INTO connection_type(id,name) VALUES(1,'COMMENTARY')")
                }
            }
            val books = Files.createDirectory(temp.resolve("books"))
            ZipOutputStream(Files.newOutputStream(books.resolve("פירוש.docx"))).use { zip ->
                val entries =
                    mapOf(
                        "word/document.xml" to
                            """
                            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>
                                <w:p><w:hyperlink r:id="native"><w:r><w:t>המילים המקושרות</w:t></w:r></w:hyperlink>
                                    <w:r><w:t>[zayit://book/1/line/101]</w:t></w:r></w:p>
                                <w:p><w:r><w:t>[otzaria://open/book/1?index=3]</w:t></w:r></w:p>
                                <w:p><w:r><w:t>עם הערה</w:t><w:footnoteReference w:id="1"/></w:r></w:p>
                                <w:p><w:r><w:t>[zayit://book/1/line/201] [zayita://book/99]</w:t></w:r>
                                    <w:r><w:t> [otzaria://open/book/1?index=99] [otzaria://open/book/1?index=-1]</w:t></w:r></w:p>
                                <w:p><w:r><w:t>[zayita://book/1]</w:t></w:r></w:p>
                                <w:p><w:r><w:t>[zayita://book/1/line/103]</w:t></w:r></w:p>
                                <w:p><w:hyperlink r:id="external"><w:r><w:t>אתר</w:t></w:r></w:hyperlink></w:p>
                            </w:body></w:document>
                            """.trimIndent(),
                        "word/_rels/document.xml.rels" to
                            """
                            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                                <Relationship Id="native" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                    Target="zayita://book/1/line/101" TargetMode="External"/>
                                <Relationship Id="external" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                    Target="https://example.org" TargetMode="External"/>
                            </Relationships>
                            """.trimIndent(),
                        "word/footnotes.xml" to
                            """
                            <w:footnotes xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                                <w:footnote w:id="1"><w:p><w:r><w:footnoteRef/><w:t>תוכן ההערה </w:t></w:r>
                                    <w:hyperlink r:id="note"><w:r><w:t>מקור ההערה</w:t></w:r></w:hyperlink></w:p></w:footnote>
                            </w:footnotes>
                            """.trimIndent(),
                        "word/_rels/footnotes.xml.rels" to
                            """
                            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                                <Relationship Id="note" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                                    Target="zayit://book/1/line/101" TargetMode="External"/>
                            </Relationships>
                            """.trimIndent(),
                    )
                entries.forEach { (name, xml) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(xml.toByteArray())
                    zip.closeEntry()
                }
            }
            val (artifacts, summaries) =
                PersonalLibraryImporter(base, temp.resolve("generations"))
                    .build(listOf(PersonalBookFolder("docx", books.toString(), "מסמכים")), "docx")
            assertEquals(1, summaries.getValue("docx").books)
            assertEquals(5, summaries.getValue("docx").links)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id,hasSourceConnection FROM book").use { rows ->
                        assertTrue(rows.next())
                        val bookId = rows.getLong(1)
                        assertEquals(1, rows.getInt(2))
                        statement
                            .executeQuery(
                                "SELECT sourceBookId,targetBookId,sourceLineId,targetLineIndex,connectionTypeId FROM link",
                            ).use { links ->
                                val targets = mutableMapOf<Int, Long>()
                                while (links.next()) {
                                    assertEquals(1L, links.getLong(1))
                                    assertEquals(bookId, links.getLong(2))
                                    assertEquals(1L, links.getLong(5))
                                    targets[links.getInt(4)] = links.getLong(3)
                                }
                                assertEquals(mapOf(0 to 101L, 1 to 103L, 2 to 101L, 4 to 101L, 5 to 103L), targets)
                            }
                    }
                    statement.executeQuery("SELECT content FROM line ORDER BY lineIndex").use { rows ->
                        assertTrue(rows.next())
                        assertTrue(rows.getString(1).contains("href=\"seforim://line/101\">המילים המקושרות</a>"))
                        assertTrue(rows.next())
                        assertTrue(rows.next())
                        val note = rows.getString(1)
                        assertTrue(note.contains("class=\"footnote\""))
                        assertTrue(note.contains("תוכן ההערה"))
                        assertTrue(note.contains("href=\"seforim://line/101\">מקור ההערה</a>"))
                    }
                    statement.executeQuery("SELECT hasSourceLinks,hasTargetLinks FROM book_has_links").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(0, rows.getInt(1))
                        assertEquals(1, rows.getInt(2))
                    }
                }
            }
            PersistentSqliteDriver("jdbc:sqlite:$base").use { driver ->
                val overlay = PersonalLibraryOverlay(driver)
                overlay.attach(artifacts.databasePath)
                val queries = SeforimDb(driver).linkQueriesQueries
                val commentaries = queries.selectLinksBySourceLineIds(listOf(101L)).executeAsList()
                assertEquals(3, commentaries.size)
                assertTrue(commentaries.all { it.targetBookTitle == "פירוש" && it.connectionType == "COMMENTARY" })
                assertEquals("פירוש", queries.selectCommentatorsByBook(1L).executeAsOne().targetBookTitle)
                val documentLineId = commentaries.first().targetLineId
                val sources =
                    driver
                        .queryEachLinkPartitionForTargetLines(listOf(documentLineId)) {
                            queries.selectInverseLinkSummariesByTargetLineIds(listOf(documentLineId), listOf("COMMENTARY")).executeAsList()
                        }.flatten()
                assertEquals(1, sources.size)
                assertEquals("מקור", sources.single().targetBookTitle)
                assertEquals(101L, sources.single().targetLineId)
                overlay.attach(null)
                assertTrue(queries.selectLinksBySourceLineIds(listOf(101L)).executeAsList().isEmpty())
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun importsPdfPagesTextAndOriginalFileLocation() {
        val temp = Files.createTempDirectory("personal-library-pdf")
        try {
            val base = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
            val books = Files.createDirectory(temp.resolve("books"))
            val pdf = books.resolve("Printed.PDF")
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    content.newLineAtOffset(50f, 700f)
                    content.showText("Searchable <PDF>")
                    content.endText()
                }
                document.addPage(PDPage())
                document.save(pdf.toFile())
            }
            val importer =
                PersonalLibraryImporter(
                    base,
                    temp.resolve("generations"),
                    io.github.kdroidfilter.seforimapp.features.pdf
                        .PdfPreindexStore(temp.resolve("pdf-index")),
                )
            val folder = PersonalBookFolder("pdf", books.toString(), "PDF")
            val (artifacts, summaries) = importer.build(listOf(folder), "pdf")
            assertEquals(1, summaries.getValue("pdf").books)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT value FROM schema_meta WHERE key LIKE 'personal_pdf:%'").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(pdf.toAbsolutePath().normalize().toString(), rows.getString(1))
                    }
                    statement.executeQuery("SELECT content FROM line ORDER BY lineIndex").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("<h1>1</h1>", rows.getString(1))
                        assertTrue(rows.next())
                        assertTrue(rows.getString(1).contains("Searchable &lt;PDF&gt;"))
                        assertTrue(rows.next())
                        assertEquals("<h1>2</h1>", rows.getString(1))
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun skipsDamagedBooksAndImportsRemainingTxtAndDocx() {
        val temp = Files.createTempDirectory("personal-library-mixed")
        try {
            val baseDatabase = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)
            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("01-valid.txt").writeText("טקסט ראשון")
            Files.write(books.resolve("02-broken.txt"), byteArrayOf(0xC3.toByte(), 0x28))
            books.resolve("03-broken.docx").writeText("not a ZIP")
            ZipOutputStream(Files.newOutputStream(books.resolve("04-valid.docx"))).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(
                    """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>פרק א</w:t></w:r></w:p>
                        <w:p><w:r><w:t>טקסט שני</w:t></w:r></w:p>
                    </w:body></w:document>
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }
            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder = PersonalBookFolder("mixed", books.toString(), "Mixed")
            val progress = mutableListOf<Float>()
            val (artifacts, summaries) = importer.build(listOf(folder), "mixed", progress::add)
            val summary = summaries.getValue(folder.id)
            assertEquals(2, summary.books)
            assertEquals(listOf("02-broken.txt", "03-broken.docx"), summary.failedFiles)
            assertEquals(1f, progress.last())
            assertEquals(progress.sorted(), progress)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT title,totalLines FROM book ORDER BY title").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("01-valid", rows.getString(1))
                        assertTrue(rows.next())
                        assertEquals("04-valid", rows.getString(1))
                        assertEquals(2, rows.getInt(2))
                        assertTrue(!rows.next())
                    }
                    statement.executeQuery("SELECT text FROM tocText").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("פרק א", rows.getString(1))
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun importsPlainFolderWithoutOtzariaSubdirectory() {
        val temp = Files.createTempDirectory("personal-library-import")
        try {
            val baseDatabase = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("ספר בדיקה.txt").writeText("שורה ראשונה\nשורה שנייה")

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "plain-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )

            val (artifacts, summaries) = importer.build(listOf(folder), "test-generation")

            assertTrue(Files.isRegularFile(artifacts.databasePath))
            assertEquals(1, summaries.getValue(folder.id).books)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT title, totalLines FROM book").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("ספר בדיקה", rows.getString("title"))
                        assertEquals(2, rows.getInt("totalLines"))
                    }
                    statement
                        .executeQuery(
                            "SELECT value FROM schema_meta WHERE key='personal_target_book_hints_v2'",
                        ).use { rows ->
                            assertTrue(rows.next())
                            assertEquals("1", rows.getString(1))
                        }
                    statement
                        .executeQuery(
                            "SELECT COUNT(*) FROM personal_link_target_book",
                        ).use { rows ->
                            assertTrue(rows.next())
                            assertEquals(0, rows.getInt(1))
                        }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsProgressUpdatesDuringBuild() {
        val temp = Files.createTempDirectory("personal-library-progress")
        try {
            val baseDatabase = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("ספר בדיקה.txt").writeText("שורה ראשונה")

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "progress-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )
            val progressValues = mutableListOf<Float>()

            importer.build(listOf(folder), "test-progress") { progress ->
                progressValues += progress
            }

            assertTrue(progressValues.isNotEmpty())
            assertTrue(progressValues.last() >= 1f)
            assertTrue(progressValues.size > 4, "Progress should be reported throughout import and indexing")
            assertEquals(progressValues.sorted(), progressValues, "Progress must never move backwards")
            assertTrue(progressValues.any { it in 0.03f..0.62f }, "Import should report intermediate progress")
            assertTrue(progressValues.any { it in 0.62f..0.98f }, "Indexing should report intermediate progress")
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun preservesHeadingOrderWhenImporting() {
        val temp = Files.createTempDirectory("personal-library-heading-order")
        try {
            val baseDatabase = temp.resolve("base.db")
            PersistentSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            val content =
                """
                <h1>כותרת א</h1>
                טקסט פרק א
                <h1>כותרת ב</h1>
                טקסט פרק ב
                <h2>תת כותרת ב1</h2>
                טקסט סעיף
                <h1>כותרת ג</h1>
                טקסט פרק ג
                """.trimIndent()
            books.resolve("ספר מרובה כותרות.txt").writeText(content)

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "headings-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )

            val (artifacts, _) = importer.build(listOf(folder), "test-headings")

            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    val query =
                        """
                        SELECT tt.text, t.id
                        FROM tocEntry t
                        JOIN tocText tt ON t.textId = tt.id
                        ORDER BY t.id ASC
                        """.trimIndent()
                    statement.executeQuery(query).use { rows ->
                        val titles = mutableListOf<String>()
                        while (rows.next()) {
                            titles.add(rows.getString("text"))
                        }
                        assertEquals(
                            listOf("כותרת א", "כותרת ב", "תת כותרת ב1", "כותרת ג"),
                            titles,
                        )
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
