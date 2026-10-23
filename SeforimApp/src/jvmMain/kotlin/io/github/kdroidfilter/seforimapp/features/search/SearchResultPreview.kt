package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jsoup.Jsoup
import seforimapp.seforimapp.generated.resources.*

internal suspend fun loadResultPreview(
    repository: SeforimRepository,
    result: SearchResult,
    radius: Int,
): List<Line> {
    val target = checkNotNull(repository.getLine(result.lineId)) { "Search result is no longer available" }
    check(target.bookId == result.bookId) { "Search result belongs to a different book" }
    val boundedRadius = radius.coerceIn(2, 20)
    return repository
        .getLines(target.bookId, (target.lineIndex - boundedRadius).coerceAtLeast(0), target.lineIndex + boundedRadius)
        .filter { it.bookId == target.bookId }
        .sortedBy { it.lineIndex }
}

private data class PreviewParagraph(
    val id: Long,
    val text: String,
    val ranges: List<IntRange>,
)

/** Read a bounded context in place; no reader tab or book-wide query is created. */
@Composable
internal fun SearchResultPreview(
    result: SearchResult,
    query: String,
    location: String,
    textSize: Float,
    fontFamily: FontFamily,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val graph = LocalAppGraph.current
    var radius by remember(result.lineId) { mutableIntStateOf(4) }
    val content by produceState<Result<List<PreviewParagraph>>?>(null, result.lineId, query, radius) {
        value =
            runSuspendCatching {
                withContext(Dispatchers.IO) {
                    loadResultPreview(graph.repository, result, radius).map { line ->
                        val plain = Jsoup.parse(line.content).text()
                        PreviewParagraph(line.id, plain, graph.searchEngine.highlightRanges(plain, query))
                    }
                }
            }
    }
    val accent = JewelTheme.globalColors.outlines.focused
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 660.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(result.bookTitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(location, color = JewelTheme.globalColors.text.info)
            when {
                content == null -> CircularProgressIndicator()
                content?.isFailure == true -> Text(stringResource(Res.string.search_preview_unavailable))
                else -> {
                    SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            content?.getOrNull().orEmpty().forEach { paragraph ->
                                val highlighted =
                                    remember(paragraph, accent) {
                                        AnnotatedString
                                            .Builder(paragraph.text)
                                            .apply {
                                                paragraph.ranges.forEach { range ->
                                                    if (range.first >= 0 && range.last < paragraph.text.length) {
                                                        addStyle(
                                                            SpanStyle(background = accent.copy(alpha = 0.25f)),
                                                            range.first,
                                                            range.last + 1,
                                                        )
                                                    }
                                                }
                                            }.toAnnotatedString()
                                    }
                                Text(
                                    highlighted,
                                    fontFamily = fontFamily,
                                    fontSize = textSize.sp,
                                    lineHeight = (textSize * 1.65f).sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (paragraph.id ==
                                                    result.lineId
                                                ) {
                                                    accent.copy(alpha = 0.08f)
                                                } else {
                                                    JewelTheme.globalColors.panelBackground
                                                },
                                                RoundedCornerShape(8.dp),
                                            ).padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DefaultButton(onClick = onOpen) { Text(stringResource(Res.string.search_preview_open)) }
                OutlinedButton(onClick = { radius += 4 }, enabled = radius < 20 && content?.isSuccess == true) {
                    Text(stringResource(Res.string.search_preview_more_context))
                }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.word_lookup_close)) }
            }
        }
    }
}
