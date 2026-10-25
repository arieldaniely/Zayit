package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.withBackground
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*

/**
 * The selected result in its book, to read without opening it: its place as a title, then the
 * found line marked by a bar, between the lines around it, faded; in the books' font and size, over
 * the whole width as the book's text. A click on a line opens the book there.
 */
@Composable
internal fun PassagePreview(
    preview: SearchResultViewModel.PassagePreview,
    pieces: List<String>?,
    query: String,
    bookFontCode: String,
    textSize: Float,
    onOpen: () -> Unit,
    // A click on a line opens the book there, in a new tab (the found line as the result does)
    onOpenLine: (lineId: Long) -> Unit,
    modifier: Modifier = Modifier,
    byMeaning: Boolean = false,
) {
    val hit = preview.hit
    val accent = JewelTheme.globalColors.outlines.focused
    val ink = JewelTheme.globalColors.text.normal
    val (categories, place) = remember(pieces, hit.bookTitle) { categoriesAndPlace(pieces, hit.bookTitle) }
    val lines = preview.lines
    val fontFamily = FontCatalog.familyFor(bookFontCode)
    val searchEngine = LocalAppGraph.current.searchEngine
    val highlight = accent.copy(alpha = 0.22f)
    val scroll = rememberScrollState()
    // The found line in view, once laid out
    var foundTop by remember(hit.lineId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(foundTop) { foundTop?.let { scroll.animateScrollTo((it - FOUND_LINE_MARGIN).coerceAtLeast(0)) } }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .card(),
    ) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 14.dp)) {
            if (categories.isNotEmpty()) Text(categories, fontSize = 12.sp, color = readingSecondary(), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hit.bookTitle, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                    if (place != null) Text(place, fontSize = 14.sp, color = readingSecondary(), maxLines = 1)
                }
            }
        }
        Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
        VerticallyScrollableContainer(scrollState = scroll, modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The ScrollState overload scrolls its content itself
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    // The whole width, as the book's text
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                ) {
                    lines.forEach { line ->
                        val found = line.id == hit.lineId
                        // Links and footnote markers in the books' color (the accent), not the parser's default blue
                        val annotated =
                            remember(line.id, textSize, accent) {
                                buildAnnotatedFromHtml(line.content, textSize, footnoteMarkerColor = accent)
                            }
                        // The search's own highlighting (SearchEngine.highlights): by meaning, the passage closest in
                        // meaning in the found line alone; else the query's words in every line
                        val ranges by produceState(
                            if (byMeaning) emptyList() else searchEngine.highlightRanges(annotated.text, query),
                            annotated,
                            query,
                            found,
                        ) {
                            if (found && byMeaning) {
                                runSuspendCatching {
                                    searchEngine
                                        .highlights(
                                            listOf(annotated.text),
                                            query,
                                            alwaysByMeaning = true,
                                        ).single()
                                        .ranges
                                }.onSuccess { value = it }
                            }
                        }
                        val text = remember(annotated, ranges, highlight) { annotated.withBackground(ranges, highlight) }
                        val lineHover = remember { MutableInteractionSource() }
                        val lineHovered by lineHover.collectIsHoveredAsState()
                        // The found line: a bar on its side, the text at full strength. Drawn, not measured: an
                        // intrinsic height at a collapsed pane's 0 width overflows Constraints and crashes
                        Text(
                            text = text,
                            fontFamily = fontFamily,
                            fontSize = textSize.sp,
                            lineHeight = (textSize * 1.8f).sp,
                            color = if (found) ink else ink.copy(alpha = 0.45f),
                            textAlign = TextAlign.Justify,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (lineHovered) accent.copy(alpha = 0.06f) else Color.Transparent)
                                    .drawBehind {
                                        if (found) {
                                            val bar = 3.dp.toPx()
                                            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                                            drawRect(accent, Offset(x, 0f), Size(bar, size.height))
                                        }
                                    }.hoverable(lineHover)
                                    .clickable(interactionSource = lineHover, indication = null) {
                                        if (found) onOpen() else onOpenLine(line.id)
                                    }.pointerHoverIcon(PointerIcon.Hand)
                                    .onGloballyPositioned { if (found) foundTop = it.positionInParent().y.toInt() }
                                    .padding(start = 17.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val FOUND_LINE_MARGIN = 40

/** The result's categories (הלכה › ראשונים) and its place in the book (סימן קד), once its breadcrumb is known. */
private fun categoriesAndPlace(
    pieces: List<String>?,
    bookTitle: String,
): Pair<String, String?> {
    val bookIndex = pieces?.indexOf(bookTitle) ?: -1
    if (pieces == null || bookIndex < 0) return "" to null
    val place =
        pieces
            .drop(bookIndex + 1)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
    return pieces.take(bookIndex).joinToString(" › ") to place
}

/** The page's panes, as the book's (PaneCard): a small gap around, rounded, on the panel's background, no border. */
@Composable
private fun Modifier.card(): Modifier =
    padding(vertical = 6.dp, horizontal = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(JewelTheme.globalColors.panelBackground)
