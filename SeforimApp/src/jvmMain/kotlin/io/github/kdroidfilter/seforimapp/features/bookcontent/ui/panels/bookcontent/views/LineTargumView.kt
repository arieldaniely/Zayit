package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.kdroidfilter.seforim.htmlparser.SkiaHtmlImageBuilder
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.LineConnectionsSnapshot
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.SafeSelectionContainer
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimlibrary.dao.repository.CommentaryWithText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.links
import seforimapp.seforimapp.generated.resources.mentions
import seforimapp.seforimapp.generated.resources.no_mentions_for_line
import seforimapp.seforimapp.generated.resources.select_line_for_mentions
import seforimapp.seforimapp.generated.resources.no_links_for_line
import seforimapp.seforimapp.generated.resources.no_sources_for_line
import seforimapp.seforimapp.generated.resources.select_line_for_links
import seforimapp.seforimapp.generated.resources.select_line_for_sources
import seforimapp.seforimapp.generated.resources.sources

// Per-side vertical padding applied by `LinkItem`'s Column. Exposed so the scrollbar
// can derive the exact per-item padding contribution as `2 × LinkItemVerticalPaddingPerSide`.
private val LinkItemVerticalPaddingPerSide = 8.dp
private const val MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT = 1200
private const val MAX_COLLAPSED_LINK_ITEM_LINES = 6

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
private fun SingleLineTargumView(
    selectedLine: Line?,
    onEvent: (BookContentEvent) -> Unit,
    buildLinksPagerFor: (Long, Long?) -> Flow<PagingData<CommentaryWithText>>,
    getAvailableLinksForLine: suspend (Long) -> Map<String, Long>,
    getLinkCharCountsForLine: suspend (Long, Long, ConnectionType) -> List<Int>,
    showDiacritics: Boolean,
    commentariesScrollIndex: Int = 0,
    commentariesScrollOffset: Int = 0,
    initiallySelectedSourceIds: Set<Long> = emptySet(),
    onSelectedSourcesChange: (Set<Long>) -> Unit = {},
    onLinkClick: (CommentaryWithText) -> Unit = {},
    onScroll: (Int, Int) -> Unit = { _, _ -> },
    onHide: () -> Unit = {},
    highlightQuery: String = "",
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    availabilityType: ConnectionType = ConnectionType.TARGUM,
    fontCodeFlow: StateFlow<String> = AppSettings.targumFontCodeFlow,
    titleRes: StringResource = Res.string.links,
    selectLineRes: StringResource = Res.string.select_line_for_links,
    emptyRes: StringResource = Res.string.no_links_for_line,
) {
    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val isBookContentZoomInProgress = LocalBookContentZoomInProgress.current
    val zoomAnimSpec = if (isTabSelected && !isBookContentZoomInProgress) tween<Float>(durationMillis = 300) else snap()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = zoomAnimSpec,
        label = "linkTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = zoomAnimSpec,
        label = "linkLineHeightAnim",
    )

    val currentGetAvailableLinksForLine by rememberUpdatedState(getAvailableLinksForLine)
    val currentOnSelectedSourcesChange by rememberUpdatedState(onSelectedSourcesChange)
    val currentOnScroll by rememberUpdatedState(onScroll)

    // Selected font for targumim
    val targumFontCode by fontCodeFlow.collectAsState()
    val targumFontFamily = FontCatalog.familyFor(targumFontCode)
    val boldScaleForPlatform =
        remember(targumFontCode) {
            val lacksBold = targumFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }

    val paneInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .hoverable(paneInteractionSource),
    ) {
        PaneHeader(
            label = stringResource(titleRes),
            interactionSource = paneInteractionSource,
            onHide = onHide,
        )

        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            when (selectedLine) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(selectLineRes))
                    }
                }

                else -> {
                    val cachedSources =
                        remember(selectedLine.id, lineConnections, availabilityType) {
                            lineConnections[selectedLine.id]?.let { snapshot ->
                                when (availabilityType) {
                                    ConnectionType.SOURCE -> snapshot.sources
                                    ConnectionType.MENTION -> snapshot.mentions
                                    else -> snapshot.targumSources
                                }
                            }
                        }

                    var titleToIdMap by remember(selectedLine.id, cachedSources) {
                        mutableStateOf<Map<String, Long>>(cachedSources ?: emptyMap())
                    }

                    LaunchedEffect(selectedLine.id, lineConnections, availabilityType) {
                        val cached =
                            lineConnections[selectedLine.id]?.let { snapshot ->
                                when (availabilityType) {
                                    ConnectionType.SOURCE -> snapshot.sources
                                    ConnectionType.MENTION -> snapshot.mentions
                                    else -> snapshot.targumSources
                                }
                            }
                        if (cached != null) {
                            titleToIdMap = cached
                            return@LaunchedEffect
                        }

                        runSuspendCatching { currentGetAvailableLinksForLine(selectedLine.id) }
                            .onSuccess { map -> titleToIdMap = map }
                            .onFailure { titleToIdMap = emptyMap() }
                    }

                    if (titleToIdMap.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(emptyRes))
                        }
                    } else {
                        // Preserve insertion order from the provider — the SQL
                        // ORDER BY (l.isDeclaredBase DESC, b.isBaseBook DESC,
                        // b.id, sl.lineIndex) ranks Sefaria-declared bases
                        // first and then by canonical catalog position
                        // (Tanakh → Mishnah → Bavli → Yerushalmi → Tosefta →
                        // Halakhah → commentators). Re-sorting by title
                        // alphabet would override that semantic ordering.
                        val availableSources =
                            remember(titleToIdMap) {
                                titleToIdMap.entries.map { SourceMeta(it.key, it.value) }
                            }

                        val selectedSources =
                            remember(titleToIdMap, initiallySelectedSourceIds) {
                                val availableIds = availableSources.map { it.bookId }.toSet()
                                val initial = initiallySelectedSourceIds.ifEmpty { availableIds }
                                initial.intersect(availableIds)
                            }

                        LaunchedEffect(selectedSources) {
                            currentOnSelectedSourcesChange(selectedSources)
                        }

                        val sourceSections =
                            availableSources.mapNotNull { meta ->
                                val pagerFlow =
                                    remember(selectedLine.id, meta.bookId) {
                                        buildLinksPagerFor(selectedLine.id, meta.bookId).distinctUntilChanged()
                                    }
                                val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()
                                SourceSection(
                                    title = meta.title,
                                    bookId = meta.bookId,
                                    items = lazyPagingItems,
                                )
                            }
                        val annotationCache =
                            remember(selectedLine.id, availabilityType) {
                                StableAnnotatedCache(mutableStateMapOf())
                            }

                        val listState =
                            rememberSaveable(
                                selectedLine.id,
                                saver = LazyListState.Saver,
                            ) {
                                LazyListState(
                                    firstVisibleItemIndex = commentariesScrollIndex,
                                    firstVisibleItemScrollOffset = commentariesScrollOffset,
                                )
                            }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                                .distinctUntilChanged()
                                .collect { (index, offset) -> currentOnScroll(index, offset) }
                        }

                        // Build the flat char-count vector for the scrollbar in LazyColumn
                        // display order: for each section, one zero entry (the bold title
                        // header) followed by the section's ordered per-link char counts.
                        // The per-section vector is fetched once per (lineId, bookId, type);
                        // failures fold to an empty vector for that section.
                        val sectionBookIds =
                            remember(sourceSections) { sourceSections.map { it.bookId } }
                        val getLinkCharCountsRef by rememberUpdatedState(getLinkCharCountsForLine)
                        val allCharCounts by produceState(
                            initialValue = emptyList<Int>(),
                            selectedLine.id,
                            sectionBookIds,
                            availabilityType,
                        ) {
                            value =
                                runSuspendCatching {
                                    coroutineScope {
                                        sectionBookIds
                                            .map { bookId ->
                                                async {
                                                    getLinkCharCountsRef(
                                                        selectedLine.id,
                                                        bookId,
                                                        availabilityType,
                                                    )
                                                }
                                            }.awaitAll()
                                    }
                                }.getOrElse { sectionBookIds.map { emptyList() } }
                                    .flatMap { listOf(0) + it }
                        }

                        var expandedItemIds by remember(selectedLine.id) { mutableStateOf(setOf<Long>()) }

                        val density = LocalDensity.current
                        val textMeasurer = rememberTextMeasurer()
                        var textLayoutWidthPx by remember(selectedLine.id) { mutableIntStateOf(0) }
                        val lineHeightPx =
                            with(density) { (commentTextSize * lineHeight).sp.toPx() }
                        val paddingPerItemPx =
                            with(density) { (LinkItemVerticalPaddingPerSide * 2).toPx() }
                        val capacity by remember(textLayoutWidthPx, commentTextSize, lineHeight, targumFontFamily) {
                            derivedStateOf {
                                if (textLayoutWidthPx <= 0) {
                                    0
                                } else {
                                    val result =
                                        textMeasurer.measure(
                                            text = AnnotatedString(CAPACITY_REFERENCE),
                                            style =
                                                TextStyle(
                                                    fontSize = commentTextSize.sp,
                                                    fontFamily = targumFontFamily,
                                                    lineHeight = (commentTextSize * lineHeight).sp,
                                                ),
                                            constraints = Constraints(maxWidth = textLayoutWidthPx),
                                        )
                                    (CAPACITY_REFERENCE.length / result.lineCount.coerceAtLeast(1)).coerceAtLeast(1)
                                }
                            }
                        }

                        val effectiveCharCounts by remember(allCharCounts, expandedItemIds, capacity, sourceSections) {
                            derivedStateOf {
                                computeEffectiveCharCounts(sourceSections, allCharCounts, expandedItemIds, capacity)
                            }
                        }

                        SafeSelectionContainer(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    sourceSections.forEach { section ->
                                        item(key = "header-${section.bookId}") {
                                            SourceSectionHeader(
                                                title = section.title,
                                                textSize = commentTextSize,
                                                onClick =
                                                    when (availabilityType) {
                                                        ConnectionType.SOURCE -> {
                                                            {
                                                                onEvent(
                                                                    BookContentEvent.OpenSourceBookInNewTab(
                                                                        bookId = section.bookId,
                                                                        baseLineIds = listOf(selectedLine.id),
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        ConnectionType.MENTION -> {
                                                            {
                                                                onEvent(
                                                                    BookContentEvent.OpenBookByIdInNewTab(
                                                                        bookId = section.bookId,
                                                                        baseLineIds = listOf(selectedLine.id),
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        else -> null
                                                    },
                                            )
                                        }

                                        items(
                                            count = section.items.itemCount,
                                            key = { index ->
                                                section.items
                                                    .peek(index)
                                                    ?.link
                                                    ?.id ?: "source-${section.bookId}-$index"
                                            },
                                        ) { index ->
                                            section.items[index]?.let { item ->
                                                LinkItem(
                                                    linkId = item.link.id,
                                                    targetText = item.targetText,
                                                    commentTextSize = commentTextSize,
                                                    lineHeight = lineHeight,
                                                    fontFamily = targumFontFamily,
                                                    boldScale = boldScaleForPlatform,
                                                    highlightQuery = highlightQuery,
                                                    onClick = { onLinkClick(item) },
                                                    isExpanded = item.link.id in expandedItemIds,
                                                    onToggleExpand = {
                                                        val id = item.link.id
                                                        expandedItemIds =
                                                            if (id in expandedItemIds) {
                                                                expandedItemIds - id
                                                            } else {
                                                                expandedItemIds + id
                                                            }
                                                    },
                                                    showDiacritics = showDiacritics,
                                                    annotationCache = annotationCache,
                                                    onLayoutWidthMeasure = { width ->
                                                        if (textLayoutWidthPx == 0 && width > 0) {
                                                            textLayoutWidthPx = width
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        when (val state = section.items.loadState.append) {
                                            is LoadState.Error ->
                                                item(key = "append-error-${section.bookId}") {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(text = state.error.message ?: "Error loading more")
                                                    }
                                                }

                                            is LoadState.Loading ->
                                                item(key = "append-loading-${section.bookId}") {
                                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }

                                            else -> {}
                                        }
                                    }
                                }
                                TargumScrollbar(
                                    listState = listState,
                                    allCharCounts = effectiveCharCounts,
                                    capacity = capacity,
                                    lineHeightPx = lineHeightPx,
                                    paddingPerItemPx = paddingPerItemPx,
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
@Composable
fun LineTargumView(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    lineConnections: Map<Long, LineConnectionsSnapshot> = emptyMap(),
    availabilityType: ConnectionType = ConnectionType.TARGUM,
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content
    val selectedLineIds = contentState.selectedLineIds.toList()
    // Multi-sélection manuelle (Ctrl+click) = afficher targum/sources de toutes les lignes
    // TOC entry selection = afficher targum/sources seulement de la ligne primaire
    val isManualMultiSelection = selectedLineIds.size > 1 && !contentState.isTocEntrySelection
    val windowInfo = LocalWindowInfo.current
    val findQuery by AppSettings.findQueryFlow(uiState.tabId).collectAsState("")
    val showFind by AppSettings.findBarOpenFlow(uiState.tabId).collectAsState()
    val activeQuery = if (showFind) findQuery else ""

    // Sélectionner les bons providers et callbacks selon le type
    val isSourceType = availabilityType == ConnectionType.SOURCE
    val isMentionType = availabilityType == ConnectionType.MENTION

    val buildPagerFor =
        when {
            isSourceType -> providers.buildSourcesPagerFor
            isMentionType -> providers.buildMentionsPagerFor
            else -> providers.buildLinksPagerFor
        }
    val getAvailableForLine =
        when {
            isSourceType -> providers.getAvailableSourcesForLine
            isMentionType -> providers.getAvailableMentionsForLine
            else -> providers.getAvailableLinksForLine
        }
    val initiallySelectedIds =
        when {
            isSourceType -> contentState.selectedSourceIds
            isMentionType -> emptySet()
            else -> contentState.selectedTargumSourceIds
        }

    val onSelectedSourcesChange =
        remember(contentState.primaryLine, isSourceType, isMentionType) {
            { ids: Set<Long> ->
                contentState.primaryLine?.let { line ->
                    when {
                        isSourceType -> onEvent(BookContentEvent.SelectedSourcesChanged(line.id, ids))
                        isMentionType -> Unit
                        else -> onEvent(BookContentEvent.SelectedTargumSourcesChanged(line.id, ids))
                    }
                }
                Unit
            }
        }

    val onLinkClick =
        remember(windowInfo) {
            { commentary: CommentaryWithText ->
                val mods = windowInfo.keyboardModifiers
                if (mods.isCtrlPressed || mods.isMetaPressed) {
                    onEvent(
                        BookContentEvent.OpenCommentaryTarget(
                            bookId = commentary.link.targetBookId,
                            lineId = commentary.link.targetLineId,
                        ),
                    )
                }
            }
        }

    val onScroll =
        remember {
            { index: Int, offset: Int ->
                onEvent(BookContentEvent.CommentariesScrolled(index, offset))
            }
        }

    val onHide =
        remember(isSourceType, isMentionType) {
            {
                when {
                    isSourceType -> onEvent(BookContentEvent.ToggleSources)
                    isMentionType -> onEvent(BookContentEvent.ToggleMentions)
                    else -> onEvent(BookContentEvent.ToggleTargum)
                }
            }
        }

    // Titres et messages selon le type
    val titleRes = when {
        isSourceType -> Res.string.sources
        isMentionType -> Res.string.mentions
        else -> Res.string.links
    }
    val selectLineRes = when {
        isSourceType -> Res.string.select_line_for_sources
        isMentionType -> Res.string.select_line_for_mentions
        else -> Res.string.select_line_for_links
    }
    val emptyRes = when {
        isSourceType -> Res.string.no_sources_for_line
        isMentionType -> Res.string.no_mentions_for_line
        else -> Res.string.no_links_for_line
    }
    val fontCodeFlow = if (isSourceType || isMentionType) AppSettings.sourceFontCodeFlow else AppSettings.targumFontCodeFlow

    if (isManualMultiSelection) {
        MultiLineTargumView(
            selectedLineIds = selectedLineIds,
            uiState = uiState,
            onEvent = onEvent,
            showDiacritics = showDiacritics,
            availabilityType = availabilityType,
            highlightQuery = activeQuery,
            onHide = onHide,
        )
    } else {
        SingleLineTargumView(
            selectedLine = contentState.primaryLine,
            onEvent = onEvent,
            buildLinksPagerFor = buildPagerFor,
            getAvailableLinksForLine = getAvailableForLine,
            getLinkCharCountsForLine = providers.getLinkCharCountsForLine,
            commentariesScrollIndex = contentState.commentariesScrollIndex,
            commentariesScrollOffset = contentState.commentariesScrollOffset,
            initiallySelectedSourceIds = initiallySelectedIds,
            onSelectedSourcesChange = onSelectedSourcesChange,
            onLinkClick = onLinkClick,
            onScroll = onScroll,
            onHide = onHide,
            highlightQuery = activeQuery,
            lineConnections = lineConnections,
            availabilityType = availabilityType,
            fontCodeFlow = fontCodeFlow,
            titleRes = titleRes,
            selectLineRes = selectLineRes,
            emptyRes = emptyRes,
            showDiacritics = showDiacritics,
        )
    }
}

/**
 * Multi-line version of LineTargumView.
 * Aggregates links/targum from all selected lines.
 */
@Composable
private fun MultiLineTargumView(
    selectedLineIds: List<Long>,
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    showDiacritics: Boolean,
    availabilityType: ConnectionType,
    highlightQuery: String,
    onHide: () -> Unit,
) {
    val providers = uiState.providers ?: return
    val contentState = uiState.content
    val windowInfo = LocalWindowInfo.current
    val currentOnEvent by rememberUpdatedState(onEvent)

    val rawTextSize by AppSettings.textSizeFlow.collectAsState()
    val isTabSelected = LocalTabSelected.current
    val isBookContentZoomInProgress = LocalBookContentZoomInProgress.current
    val zoomAnimSpec = if (isTabSelected && !isBookContentZoomInProgress) tween<Float>(durationMillis = 300) else snap()
    val commentTextSize by animateFloatAsState(
        targetValue = rawTextSize * 0.875f,
        animationSpec = zoomAnimSpec,
        label = "multiLineLinkTextSizeAnim",
    )
    val rawLineHeight by AppSettings.lineHeightFlow.collectAsState()
    val lineHeight by animateFloatAsState(
        targetValue = rawLineHeight,
        animationSpec = zoomAnimSpec,
        label = "multiLineLinkLineHeightAnim",
    )

    // Selected font for targumim
    val targumFontCode by AppSettings.targumFontCodeFlow.collectAsState()
    val targumFontFamily = FontCatalog.familyFor(targumFontCode)
    val boldScaleForPlatform =
        remember(targumFontCode) {
            val lacksBold = targumFontCode in setOf("notoserifhebrew", "notorashihebrew", "frankruhllibre")
            if (PlatformInfo.isMacOS && lacksBold) 1.08f else 1.0f
        }

    val paneInteractionSource = remember { MutableInteractionSource() }

    // Use multi-line provider to get aggregated available links
    val titleToIdMap by produceState<Map<String, Long>>(emptyMap(), selectedLineIds, availabilityType) {
        value =
            when (availabilityType) {
                ConnectionType.SOURCE -> providers.getAvailableSourcesForLines(selectedLineIds)
                ConnectionType.MENTION -> providers.getAvailableMentionsForLines(selectedLineIds)
                else -> providers.getAvailableLinksForLines(selectedLineIds)
            }
    }

    val titleRes =
        when (availabilityType) {
            ConnectionType.SOURCE -> Res.string.sources
            ConnectionType.MENTION -> Res.string.mentions
            else -> Res.string.links
        }
    val emptyRes =
        when (availabilityType) {
            ConnectionType.SOURCE -> Res.string.no_sources_for_line
            ConnectionType.MENTION -> Res.string.no_mentions_for_line
            else -> Res.string.no_links_for_line
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .hoverable(paneInteractionSource),
    ) {
        PaneHeader(
            label = stringResource(titleRes),
            interactionSource = paneInteractionSource,
            onHide = onHide,
        )

        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            if (titleToIdMap.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(emptyRes))
                }
            } else {
                // Preserve insertion order from the provider — the underlying
                // SQL ORDER BY (l.isDeclaredBase DESC, b.orderIndex, sl.lineIndex)
                // already ranks declared bases first and otherwise sorts by the
                // source book's catalog position. Re-sorting by title alphabet
                // here would override that semantic ordering.
                val availableSources =
                    remember(titleToIdMap) {
                        titleToIdMap.entries.map { SourceMeta(it.key, it.value) }
                    }

                // Build pagers for each source using multi-line provider
                val sourceSections =
                    availableSources.mapNotNull { meta ->
                        val pagerFlow =
                            remember(selectedLineIds, meta.bookId, availabilityType) {
                                when (availabilityType) {
                                    ConnectionType.SOURCE ->
                                        providers.buildSourcesPagerForLines(selectedLineIds, meta.bookId)

                                    ConnectionType.MENTION ->
                                        providers.buildMentionsPagerForLines(selectedLineIds, meta.bookId)

                                    else ->
                                        providers.buildLinksPagerForLines(selectedLineIds, meta.bookId)
                                }.distinctUntilChanged()
                            }
                        val lazyPagingItems = pagerFlow.collectAsLazyPagingItems()
                        SourceSection(
                            title = meta.title,
                            bookId = meta.bookId,
                            items = lazyPagingItems,
                        )
                    }
                val annotationCache =
                    remember(selectedLineIds, availabilityType) {
                        StableAnnotatedCache(mutableStateMapOf())
                    }

                val listState =
                    rememberSaveable(
                        selectedLineIds,
                        saver = LazyListState.Saver,
                    ) {
                        LazyListState(
                            firstVisibleItemIndex = contentState.commentariesScrollIndex,
                            firstVisibleItemScrollOffset = contentState.commentariesScrollOffset,
                        )
                    }

                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .distinctUntilChanged()
                        .collect { (index, offset) ->
                            currentOnEvent(BookContentEvent.CommentariesScrolled(index, offset))
                        }
                }

                val sectionBookIds =
                    remember(sourceSections) { sourceSections.map { it.bookId } }
                val getLinkCharCountsRef by rememberUpdatedState(providers.getLinkCharCountsForLines)
                val allCharCounts by produceState(
                    initialValue = emptyList<Int>(),
                    selectedLineIds,
                    sectionBookIds,
                    availabilityType,
                ) {
                    value =
                        runSuspendCatching {
                            coroutineScope {
                                sectionBookIds
                                    .map { bookId ->
                                        async {
                                            getLinkCharCountsRef(
                                                selectedLineIds,
                                                bookId,
                                                availabilityType,
                                            )
                                        }
                                    }.awaitAll()
                            }
                        }.getOrElse { sectionBookIds.map { emptyList() } }
                            .flatMap { listOf(0) + it }
                }

                var expandedItemIds by remember(selectedLineIds) { mutableStateOf(setOf<Long>()) }

                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                var textLayoutWidthPx by remember(selectedLineIds) { mutableIntStateOf(0) }
                val lineHeightPx =
                    with(density) { (commentTextSize * lineHeight).sp.toPx() }
                val paddingPerItemPx =
                    with(density) { (LinkItemVerticalPaddingPerSide * 2).toPx() }
                val capacity by remember(textLayoutWidthPx, commentTextSize, lineHeight, targumFontFamily) {
                    derivedStateOf {
                        if (textLayoutWidthPx <= 0) {
                            0
                        } else {
                            val result =
                                textMeasurer.measure(
                                    text = AnnotatedString(CAPACITY_REFERENCE),
                                    style =
                                        TextStyle(
                                            fontSize = commentTextSize.sp,
                                            fontFamily = targumFontFamily,
                                            lineHeight = (commentTextSize * lineHeight).sp,
                                        ),
                                    constraints = Constraints(maxWidth = textLayoutWidthPx),
                                )
                            (CAPACITY_REFERENCE.length / result.lineCount.coerceAtLeast(1)).coerceAtLeast(1)
                        }
                    }
                }

                val effectiveCharCounts by remember(allCharCounts, expandedItemIds, capacity, sourceSections) {
                    derivedStateOf {
                        computeEffectiveCharCounts(sourceSections, allCharCounts, expandedItemIds, capacity)
                    }
                }

                SafeSelectionContainer(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sourceSections.forEach { section ->
                                item(key = "header-${section.bookId}") {
                                    SourceSectionHeader(
                                        title = section.title,
                                        textSize = commentTextSize,
                                        onClick =
                                            when (availabilityType) {
                                                ConnectionType.SOURCE -> {
                                                    {
                                                        onEvent(
                                                            BookContentEvent.OpenSourceBookInNewTab(
                                                                bookId = section.bookId,
                                                                baseLineIds = selectedLineIds,
                                                            ),
                                                        )
                                                    }
                                                }
                                                ConnectionType.MENTION -> {
                                                    {
                                                        onEvent(
                                                            BookContentEvent.OpenBookByIdInNewTab(
                                                                bookId = section.bookId,
                                                                baseLineIds = selectedLineIds,
                                                            ),
                                                        )
                                                    }
                                                }
                                                else -> null
                                            },
                                    )
                                }

                                items(
                                    count = section.items.itemCount,
                                    key = { index ->
                                        section.items
                                            .peek(index)
                                            ?.link
                                            ?.id ?: "multi-source-${section.bookId}-$index"
                                    },
                                ) { index ->
                                    section.items[index]?.let { item ->
                                        LinkItem(
                                            linkId = item.link.id,
                                            targetText = item.targetText,
                                            commentTextSize = commentTextSize,
                                            lineHeight = lineHeight,
                                            fontFamily = targumFontFamily,
                                            boldScale = boldScaleForPlatform,
                                            highlightQuery = highlightQuery,
                                            onClick = {
                                                onEvent(
                                                    BookContentEvent.OpenCommentaryTarget(
                                                        bookId = item.link.targetBookId,
                                                        lineId = item.link.targetLineId,
                                                    ),
                                                )
                                            },
                                            isExpanded = item.link.id in expandedItemIds,
                                            onToggleExpand = {
                                                val id = item.link.id
                                                expandedItemIds =
                                                    if (id in expandedItemIds) {
                                                        expandedItemIds - id
                                                    } else {
                                                        expandedItemIds + id
                                                    }
                                            },
                                            showDiacritics = showDiacritics,
                                            annotationCache = annotationCache,
                                            onLayoutWidthMeasure = { width ->
                                                if (textLayoutWidthPx == 0 && width > 0) {
                                                    textLayoutWidthPx = width
                                                }
                                            },
                                        )
                                    }
                                }

                                when (val state = section.items.loadState.append) {
                                    is LoadState.Error ->
                                        item(key = "append-error-${section.bookId}") {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(text = state.error.message ?: "Error loading more")
                                            }
                                        }

                                    is LoadState.Loading ->
                                        item(key = "append-loading-${section.bookId}") {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator()
                                            }
                                        }

                                    else -> {}
                                }
                            }
                        }
                        TargumScrollbar(
                            listState = listState,
                            allCharCounts = effectiveCharCounts,
                            capacity = capacity,
                            lineHeightPx = lineHeightPx,
                            paddingPerItemPx = paddingPerItemPx,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class SourceSection(
    val title: String,
    val bookId: Long,
    val items: LazyPagingItems<CommentaryWithText>,
)

private data class SourceMeta(
    val title: String,
    val bookId: Long,
)

@Composable
private fun LinkItem(
    linkId: Long,
    targetText: String,
    commentTextSize: Float,
    lineHeight: Float,
    fontFamily: FontFamily,
    highlightQuery: String,
    onClick: () -> Unit,
    showDiacritics: Boolean,
    annotationCache: StableAnnotatedCache,
    boldScale: Float = 1.0f,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onLayoutWidthMeasure: (Int) -> Unit = {},
) {
    val windowInfo = LocalWindowInfo.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LinkItemVerticalPaddingPerSide, horizontal = 16.dp)
                .pointerInput(linkId) {
                    detectTapGestures(onTap = {
                        val mods = windowInfo.keyboardModifiers
                        if (mods.isCtrlPressed || mods.isMetaPressed) {
                            onClick()
                        } else {
                            onToggleExpand()
                        }
                    })
                },
    ) {
        val processedText =
            remember(linkId, targetText, showDiacritics) {
                if (showDiacritics) targetText else HebrewTextUtils.removeAllDiacritics(targetText)
            }
        val shouldCollapse = remember(processedText) {
            processedText.length > MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT
        }
        val maxLines = if (shouldCollapse && !isExpanded) MAX_COLLAPSED_LINK_ITEM_LINES else Int.MAX_VALUE
        val overflow = if (shouldCollapse && !isExpanded) TextOverflow.Ellipsis else TextOverflow.Clip

        // Footnote marker color from theme
        val footnoteMarkerColor = JewelTheme.globalColors.outlines.focused

        val isDarkTheme = JewelTheme.isDark
        val imageColorFilter: @Composable () -> ColorFilter? =
            remember(isDarkTheme) {
                { if (isDarkTheme) SkiaHtmlImageBuilder.InvertColorFilter else null }
            }

        val annotationCacheKey =
            remember(linkId, processedText, commentTextSize, boldScale, footnoteMarkerColor, isDarkTheme) {
                HtmlAnnotationCacheKey(
                    itemId = linkId,
                    contentHash = processedText.hashCode(),
                    contentLength = processedText.length,
                    baseTextSize = commentTextSize,
                    boldScale = boldScale,
                    footnoteMarkerColor = footnoteMarkerColor,
                    invertImages = isDarkTheme,
                )
            }
        val annotation =
            rememberAsyncHtmlAnnotation(
                cacheKey = annotationCacheKey,
                html = processedText,
                baseTextSize = commentTextSize,
                boldScale = boldScale,
                footnoteMarkerColor = footnoteMarkerColor,
                imageColorFilter = imageColorFilter,
                annotatedCache = annotationCache,
            )

        if (annotation == null) {
            Text(
                text = htmlAnnotationPlaceholderText(processedText.length),
                textAlign = TextAlign.Justify,
                fontSize = commentTextSize.sp,
                fontFamily = fontFamily,
                lineHeight = (commentTextSize * lineHeight).sp,
                maxLines = maxLines,
                overflow = overflow,
                onTextLayout = { result ->
                    val cw = result.layoutInput.constraints.maxWidth
                    if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
                },
            )
        } else {
            val annotated = annotation.annotated
            val inlineImageContent = annotation.inlineContent

            // Highlight occurrences using the current tab's find-in-page query
            val display: AnnotatedString =
                remember(annotated, highlightQuery) {
                    io.github.kdroidfilter.seforimapp.core.presentation.text
                        .highlightAnnotated(annotated, highlightQuery)
                }

            Text(
                text = display,
                textAlign = TextAlign.Justify,
                fontFamily = fontFamily,
                lineHeight = (commentTextSize * lineHeight).sp,
                inlineContent = inlineImageContent,
                maxLines = maxLines,
                overflow = overflow,
                onTextLayout = { result ->
                    val cw = result.layoutInput.constraints.maxWidth
                    if (cw > 0 && cw != Int.MAX_VALUE) onLayoutWidthMeasure(cw)
                },
            )
        }
    }
}

@Composable
private fun SourceSectionHeader(
    title: String,
    textSize: Float,
    onClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = (textSize * 1.1f).sp,
        textAlign = TextAlign.Center,
        textDecoration =
            if (onClick != null && isHovered) {
                TextDecoration.Underline
            } else {
                TextDecoration.None
            },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .hoverable(interactionSource)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(title) { detectTapGestures(onTap = { onClick() }) }
                    } else {
                        Modifier
                    },
                ),
    )
}

private fun computeEffectiveCharCounts(
    sourceSections: List<SourceSection>,
    allCharCounts: List<Int>,
    expandedItemIds: Set<Long>,
    capacity: Int,
): List<Int> {
    if (allCharCounts.isEmpty()) return emptyList()
    val result = ArrayList<Int>(allCharCounts.size)
    var globalIdx = 0
    for (section in sourceSections) {
        if (globalIdx >= allCharCounts.size) break
        // Header entry in allCharCounts is 0
        result.add(allCharCounts[globalIdx++])
        val itemCount = section.items.itemCount
        for (i in 0 until itemCount) {
            if (globalIdx >= allCharCounts.size) break
            val rawCount = allCharCounts[globalIdx++]
            val item = section.items.peek(i)
            val isExpanded = item != null && item.link.id in expandedItemIds
            val effectiveCount =
                if (rawCount > MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT && !isExpanded) {
                    if (capacity > 0) {
                        minOf(rawCount, capacity * MAX_COLLAPSED_LINK_ITEM_LINES)
                    } else {
                        minOf(rawCount, MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT)
                    }
                } else {
                    rawCount
                }
            result.add(effectiveCount)
        }
    }
    // Any remaining items if allCharCounts has entries beyond loaded sections
    while (globalIdx < allCharCounts.size) {
        val rawCount = allCharCounts[globalIdx++]
        val effectiveCount =
            if (rawCount > MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT) {
                if (capacity > 0) {
                    minOf(rawCount, capacity * MAX_COLLAPSED_LINK_ITEM_LINES)
                } else {
                    minOf(rawCount, MAX_COLLAPSED_LINK_ITEM_CHAR_COUNT)
                }
            } else {
                rawCount
            }
        result.add(effectiveCount)
    }
    return result
}
