package io.github.kdroidfilter.seforimapp.features.errorreport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.formatHebrewSourceReference
import io.github.kdroidfilter.seforimapp.core.resolveLineRangeFromSelection
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.error_report_cancel
import seforimapp.seforimapp.generated.resources.error_report_details
import seforimapp.seforimapp.generated.resources.error_report_details_hint
import seforimapp.seforimapp.generated.resources.error_report_email
import seforimapp.seforimapp.generated.resources.error_report_excerpt
import seforimapp.seforimapp.generated.resources.error_report_failure
import seforimapp.seforimapp.generated.resources.error_report_invalid_email
import seforimapp.seforimapp.generated.resources.error_report_missing_details
import seforimapp.seforimapp.generated.resources.error_report_privacy
import seforimapp.seforimapp.generated.resources.error_report_send
import seforimapp.seforimapp.generated.resources.error_report_sending
import seforimapp.seforimapp.generated.resources.error_report_success
import seforimapp.seforimapp.generated.resources.error_report_title
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class BookErrorReportDraft(
    val book: Book,
    val firstLine: Line,
    val lastLine: Line,
    val selectedText: String,
    val contextText: String,
    val currentRef: String,
)

fun createBookErrorReportDraft(
    book: Book,
    selectedText: String,
    visibleLines: List<Line>,
    currentLineId: Long,
    rootTitle: String?,
): BookErrorReportDraft? {
    val range =
        if (selectedText.isNotBlank()) {
            resolveLineRangeFromSelection(selectedText, visibleLines)
        } else {
            visibleLines.firstOrNull { it.id == currentLineId }?.let { it to it }
        } ?: return null
    val plainContext =
        visibleLines
            .filter { it.lineIndex in range.first.lineIndex..range.second.lineIndex }
            .joinToString(" ") {
                buildAnnotatedFromHtml(it.content, baseTextSize = 16f, boldScale = 1f).text.trim()
            }.trim()
    val excerpt = selectedText.trim().ifBlank { plainContext }.take(1200)
    return BookErrorReportDraft(
        book = book,
        firstLine = range.first,
        lastLine = range.second,
        selectedText = excerpt,
        contextText = plainContext.take(2000),
        currentRef = formatHebrewSourceReference(book, rootTitle, range.first, range.second),
    )
}

internal data class ErrorReportRequest(
    val draft: BookErrorReportDraft,
    val senderEmail: String,
    val errorDetails: String,
    val sourceFolder: String,
    val libraryVersion: String,
    val reportId: String = "zayita-${UUID.randomUUID()}",
    val createdAt: String = Instant.now().toString(),
)

internal fun buildErrorReportPayload(request: ErrorReportRequest): String =
    buildJsonObject {
        put("report_id", request.reportId)
        put("sender_email", request.senderEmail.trim())
        put("subject", "דיווח על טעות: ${request.draft.book.title}")
        put("book_title", request.draft.book.title)
        put("current_ref", request.draft.currentRef)
        put("line_number", request.draft.firstLine.lineIndex + 1)
        put("selected_text", request.draft.selectedText)
        put("error_details", request.errorDetails.trim())
        put("context_text", request.draft.contextText)
        // A stable library identifier is useful to Otzaria; never expose the user's local path.
        val filePath =
            request.draft.book.heRef
                ?.takeIf { it.isNotBlank() }
                ?: request.draft.book.title
        put("file_path", filePath)
        put("source_folder", request.sourceFolder)
        put("library_version", request.libraryVersion)
        put("created_at", request.createdAt)
    }.toString()

private object BookErrorReportService {
    private const val ENDPOINT = "https://otzaria.org/api/reportingerrors"
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    suspend fun send(
        repository: SeforimRepository,
        draft: BookErrorReportDraft,
        senderEmail: String,
        details: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = repository.getSourceById(draft.book.sourceId)?.name ?: "unknown"
                val payload =
                    buildErrorReportPayload(
                        ErrorReportRequest(
                            draft = draft,
                            senderEmail = senderEmail,
                            errorDetails = details,
                            sourceFolder = source,
                            libraryVersion = DatabaseVersionManager.getCurrentDatabaseVersion() ?: "unknown",
                        ),
                    )
                val request =
                    HttpRequest
                        .newBuilder(URI.create(ENDPOINT))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                check(response.statusCode() == 200) { "Error report endpoint returned ${response.statusCode()}" }
            }
        }
}

private enum class ReportStatus { EDITING, SENDING, SUCCESS, FAILURE }

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Composable
fun BookErrorReportDialog(
    draft: BookErrorReportDraft,
    repository: SeforimRepository,
    onDismiss: () -> Unit,
) {
    val emailState = rememberTextFieldState(AppSettings.getErrorReportEmail())
    val detailsState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ReportStatus.EDITING) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val invalidEmailMessage = stringResource(Res.string.error_report_invalid_email)
    val missingDetailsMessage = stringResource(Res.string.error_report_missing_details)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .width(620.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(18.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(18.dp))
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(stringResource(Res.string.error_report_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("${draft.book.title} · ${draft.currentRef}", color = JewelTheme.globalColors.text.info)
            Text(stringResource(Res.string.error_report_excerpt), fontWeight = FontWeight.SemiBold)
            Text(
                text = "״${draft.selectedText}״",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                fontStyle = FontStyle.Italic,
                color = JewelTheme.globalColors.text.info,
                maxLines = 5,
            )
            Text(stringResource(Res.string.error_report_email), fontWeight = FontWeight.SemiBold)
            TextField(state = emailState, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.error_report_details), fontWeight = FontWeight.SemiBold)
            TextArea(
                state = detailsState,
                placeholder = { Text(stringResource(Res.string.error_report_details_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
            )
            Text(
                stringResource(Res.string.error_report_privacy),
                color = JewelTheme.globalColors.text.info,
                fontSize = 11.sp,
            )
            validationMessage?.let { Text(it, color = Color(0xFFB00020)) }
            when (status) {
                ReportStatus.SENDING ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.error_report_sending))
                    }
                ReportStatus.SUCCESS -> Text(stringResource(Res.string.error_report_success), color = JewelTheme.globalColors.text.info)
                ReportStatus.FAILURE -> Text(stringResource(Res.string.error_report_failure), color = Color(0xFFB00020))
                ReportStatus.EDITING -> Unit
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onDismiss, enabled = status != ReportStatus.SENDING) {
                    Text(stringResource(Res.string.error_report_cancel))
                }
                if (status != ReportStatus.SUCCESS) {
                    DefaultButton(
                        enabled = status != ReportStatus.SENDING,
                        onClick = {
                            val email = emailState.text.toString().trim()
                            val details = detailsState.text.toString().trim()
                            validationMessage =
                                when {
                                    !EMAIL_PATTERN.matches(email) -> invalidEmailMessage
                                    details.isBlank() -> missingDetailsMessage
                                    else -> null
                                }
                            if (validationMessage == null) {
                                AppSettings.setErrorReportEmail(email)
                                status = ReportStatus.SENDING
                                scope.launch {
                                    status =
                                        if (BookErrorReportService.send(repository, draft, email, details).isSuccess) {
                                            ReportStatus.SUCCESS
                                        } else {
                                            ReportStatus.FAILURE
                                        }
                                }
                            }
                        },
                    ) { Text(stringResource(Res.string.error_report_send)) }
                }
            }
        }
    }
}
