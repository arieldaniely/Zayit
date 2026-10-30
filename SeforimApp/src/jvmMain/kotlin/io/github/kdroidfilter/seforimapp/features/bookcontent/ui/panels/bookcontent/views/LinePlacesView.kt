package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.references.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.search.BookGroup
import io.github.kdroidfilter.seforimapp.features.search.BookResultCard
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.LinkLoadLevel
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.*

/** The reference and source panes share the search's result cards, typography, expansion, and quote highlighting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LinePlacesView(
    kind: LinePlaces,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    diacritics: DiacriticsMode,
) {
    val graph = LocalAppGraph.current
    val content = uiState.content
    val lines = if (content.selectedLines.size > 1) content.selectedLines.sortedBy { it.lineIndex } else listOfNotNull(content.primaryLine)
    val ids = lines.map { it.id }
    val level by graph.appSettings.linkLoadLevelFlow.collectAsState()
    val references by produceState<LineReferences?>(null, kind, ids, level) {
        value = null
        value =
            runSuspendCatching { loadLinePlaces(kind, graph.repository, lines, LinkLoadLevel.fromValue(level)) }
                .getOrDefault(LineReferences.Empty)
    }
    val interaction = remember { MutableInteractionSource() }
    val sources = kind == LinePlaces.Sources
    Column(Modifier.fillMaxSize().hoverable(interaction)) {
        PaneHeader(
            label = stringResource(if (sources) Res.string.sources else Res.string.mentions),
            interactionSource = interaction,
            onHide = { onEvent(if (sources) BookContentEvent.ToggleSources else BookContentEvent.ToggleMentions) },
        )
        val loaded = references
        when {
            lines.isEmpty() ->
                PlacesMessage(
                    stringResource(if (sources) Res.string.select_line_for_sources else Res.string.select_line_for_mentions),
                )
            loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            loaded.books.isEmpty() ->
                PlacesMessage(
                    stringResource(if (sources) Res.string.no_sources_for_line else Res.string.no_mentions_for_line),
                )
            else -> {
                var selectedTab by remember(kind, ids) { mutableStateOf<Long?>(null) }
                val expanded = remember(kind, ids) { mutableStateMapOf<String, Boolean>() }
                val books = loaded.books.filter { selectedTab == null || it.tabId == selectedTab }
                val tabs =
                    loaded.kindTabs.map {
                        it.tabId to
                            stringResource(
                                if (it ==
                                    ReferenceKind.EinMishpat
                                ) {
                                    Res.string.references_ein_mishpat
                                } else {
                                    Res.string.references_mesorat_hashas
                                },
                            )
                    } + loaded.categoryTabs
                if (tabs.isNotEmpty()) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (listOf(null to stringResource(Res.string.search_tab_all)) + tabs).forEach { (id, title) ->
                            val selected = selectedTab == id
                            Text(
                                title,
                                fontSize = 12.sp,
                                modifier =
                                    Modifier
                                        .background(
                                            JewelTheme.globalColors.outlines.focused
                                                .copy(alpha = if (selected) 0.18f else 0.04f),
                                            RoundedCornerShape(12.dp),
                                        ).clickable { selectedTab = id }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.references_summary, loaded.books.size, loaded.placeCount),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                )
                val listState = rememberLazyListState()
                LaunchedEffect(selectedTab) { listState.scrollToItem(0) }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = (maxWidth / 340.dp).toInt().coerceIn(1, 4)
                    val rows = books.chunked(columns)
                    VerticallyScrollableContainer(scrollState = listState, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(rows, key = { row -> "${row.first().kind}-${row.first().bookId}" }) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                    row.forEach { book ->
                                        val key = "${book.kind}-${book.bookId}"
                                        Box(Modifier.weight(1f)) {
                                            ReferenceBookCard(book, expanded[key] == true, diacritics, {
                                                expanded[key] =
                                                    expanded[key] != true
                                            }, onEvent)
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceBookCard(
    book: ReferenceBook,
    expanded: Boolean,
    diacritics: DiacriticsMode,
    onExpand: () -> Unit,
    onEvent: (BookContentEvent) -> Unit,
) {
    val graph = LocalAppGraph.current
    val textSize by graph.appSettings.textSizeFlow.collectAsState()
    val lineHeight by graph.appSettings.lineHeightFlow.collectAsState()
    val font by graph.appSettings.bookFontCodeFlow.collectAsState()
    val shown = if (expanded) book.places else book.places.take(1)
    val snippets by produceState<Map<Long, String>>(emptyMap(), shown, diacritics) {
        value = withContext(Dispatchers.Default) { shown.associate { it.line.id to diacritics.apply(it.snippet(graph.searchEngine)) } }
    }
    val hits =
        shown.map { place ->
            SearchResult(
                book.bookId,
                book.title,
                place.line.id,
                place.line.lineIndex,
                snippets[place.line.id] ?: diacritics.apply(place.start),
                0.0,
            )
        }
    val breadcrumbs = shown.associate { it.line.id to listOfNotNull(book.title, it.place) }.toImmutableMap()
    val group = BookGroup(book.bookId, book.title, hits.first(), emptyList(), book.places.size)
    BookResultCard(
        group = group,
        textSize = textSize * 0.875f,
        lineHeight = lineHeight,
        fontFamily = FontCatalog.familyFor(font),
        findQuery = null,
        bookFontCode = font,
        breadcrumbs = breadcrumbs,
        onRequestBreadcrumb = {},
        onOpenResult = { onEvent(BookContentEvent.OpenCommentaryTarget(it.bookId, it.lineId)) },
        onOpenPdf = null,
        isExpanded = expanded,
        expandedHits = hits.takeIf { expanded },
        onToggleExpand = { onExpand() },
        showPreviewAction = false,
        primaryMaxLines = 6,
        collapsedSecondaryLimit = 0,
        expandLabel = stringResource(Res.string.references_more_from_book, book.places.size - 1, book.title),
    )
}

@Composable
private fun PlacesMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(message) }
}
