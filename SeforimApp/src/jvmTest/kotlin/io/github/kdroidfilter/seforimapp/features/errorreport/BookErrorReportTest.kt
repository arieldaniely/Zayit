package io.github.kdroidfilter.seforimapp.features.errorreport

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class BookErrorReportTest {
    private val book = Book(id = 7, categoryId = 1, sourceId = 2, title = "ספר בדיקה", heRef = "מזהה ספר")
    private val line = Line(id = 11, bookId = 7, lineIndex = 4, content = "<b>טקסט</b> שגוי כאן", heRef = "פרק א")

    @Test
    fun draft_uses_right_clicked_line_when_there_is_no_selection() {
        val draft = createBookErrorReportDraft(book, "", listOf(line), line.id, rootTitle = null)

        assertNotNull(draft)
        assertEquals("טקסט שגוי כאן", draft.selectedText)
        assertEquals(line, draft.firstLine)
    }

    @Test
    fun payload_matches_otzaria_contract_without_local_path() {
        val draft = createBookErrorReportDraft(book, "טקסט שגוי", listOf(line), line.id, rootTitle = null)!!
        val payload =
            buildErrorReportPayload(
                ErrorReportRequest(
                    draft = draft,
                    senderEmail = "user@example.com",
                    errorDetails = "צריך לתקן",
                    sourceFolder = "Otzaria",
                    libraryVersion = "158",
                    reportId = "zayita-test",
                    createdAt = "2026-08-14T12:30:00Z",
                ),
            )
        val json = Json.parseToJsonElement(payload).jsonObject

        assertEquals("zayita-test", json.getValue("report_id").jsonPrimitive.content)
        assertEquals("user@example.com", json.getValue("sender_email").jsonPrimitive.content)
        assertEquals("5", json.getValue("line_number").jsonPrimitive.content)
        assertEquals("Otzaria", json.getValue("source_folder").jsonPrimitive.content)
        assertEquals("מזהה ספר", json.getValue("file_path").jsonPrimitive.content)
        assertFalse(payload.contains("C:\\"))
        assertFalse(json.keys.any { it in setOf("queueType", "recipient_email", "body", "file_name") })
    }
}
