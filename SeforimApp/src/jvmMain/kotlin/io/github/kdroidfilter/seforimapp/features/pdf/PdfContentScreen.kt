package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import org.apache.pdfbox.text.TextPosition
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.zIndex
import io.github.kdroidfilter.seforimapp.core.presentation.components.CountBadge
import io.github.kdroidfilter.seforimapp.core.presentation.components.FindInPageBar
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.pdfbox.text.PDFTextStripper
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import seforimapp.seforimapp.generated.resources.*
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Image as SkiaImage

internal const val PDF_ZOOM_MIN = 0.55f
internal const val PDF_ZOOM_MAX = 1.8f
internal const val PDF_ZOOM_STEP = 0.1f
internal const val PDF_DEFAULT_ZOOM = 0.92f
private const val PDF_COLUMN_GAP = 0.022f
private const val PDF_BLOCK_CONTINUATION_THRESHOLD = 0.12f

/**
 * The printed-edition content pane. The regular book screen supplies the surrounding library,
 * table of contents, notes, commentaries, sources, and breadcrumbs.
 */
@Composable
fun PdfContentView(
    file: File?,
    tabId: String,
    bookId: Long,
    selectedLineId: Long?,
    requestedReferences: List<String>,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onLineSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (file == null) {
        MissingPdfPanel(modifier)
        return
    }

    val loadState by produceState<PdfLoadState>(PdfLoadState.Loading, file) {
        val session =
            runCatching { withContext(Dispatchers.IO) { PdfDocumentSession.open(file) } }
                .getOrElse {
                    value = PdfLoadState.Failed(it.message.orEmpty())
                    return@produceState
                }
        value = PdfLoadState.Ready(session)
        try {
            awaitCancellation()
        } finally {
            session.close()
        }
    }

    when (val state = loadState) {
        PdfLoadState.Loading -> LoadingPanel(modifier)
        is PdfLoadState.Failed -> PdfErrorPanel(state.message, modifier)
        is PdfLoadState.Ready ->
            PdfReader(
                session = state.session,
                tabId = tabId,
                bookId = bookId,
                selectedLineId = selectedLineId,
                requestedReferences = requestedReferences,
                zoom = zoom,
                onZoomChange = onZoomChange,
                onLineSelected = onLineSelected,
                modifier = modifier,
            )
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun PdfReader(
    session: PdfDocumentSession,
    tabId: String,
    bookId: Long,
    selectedLineId: Long?,
    requestedReferences: List<String>,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onLineSelected: (Long) -> Unit,
    modifier: Modifier,
) {
    val repository = LocalAppGraph.current.repository
    val textAnchors by produceState<List<PdfTextAnchor>>(emptyList(), bookId) {
        value =
            withContext(Dispatchers.IO) {
                repository
                    .getTocEntriesForBook(bookId)
                    .mapNotNull { entry -> entry.lineId?.let { PdfTextAnchor(entry.text, it) } }
            }
    }
    val initialPage = remember(session, requestedReferences) {
        session.outlineIndex.pageFor(requestedReferences)?.coerceIn(0, session.pageCount - 1) ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val currentSelectedLineId by rememberUpdatedState(selectedLineId)
    val currentOnLineSelected by rememberUpdatedState(onLineSelected)
    var programmaticTarget by remember(session) { mutableStateOf<Int?>(null) }
    val showFind by AppSettings.findBarOpenFlow(tabId).collectAsState()
    val persistedFindQuery by AppSettings.findQueryFlow(tabId).collectAsState("")
    val findState = remember(tabId) { TextFieldState() }
    val scope = rememberCoroutineScope()
    var searchMatches by remember(session) { mutableStateOf(emptyList<PdfSearchMatch>()) }
    var selectedMatchIndex by remember(session) { mutableIntStateOf(-1) }

    LaunchedEffect(persistedFindQuery) {
        if (findState.text.toString() != persistedFindQuery) {
            findState.edit { replace(0, length, persistedFindQuery) }
        }
    }

    LaunchedEffect(session, requestedReferences) {
        val page = session.outlineIndex.pageFor(requestedReferences) ?: return@LaunchedEffect
        if (page != listState.mostVisiblePage()) {
            programmaticTarget = page
            listState.scrollToItem(page)
        }
    }

    LaunchedEffect(session, listState, textAnchors) {
        snapshotFlow { listState.mostVisiblePage() to listState.isScrollInProgress }
            .distinctUntilChanged()
            .debounce(180)
            .collect { (page, scrolling) ->
                if (scrolling || page !in 0 until session.pageCount) return@collect
                val target = programmaticTarget
                if (target != null) {
                    if (target == page) programmaticTarget = null
                    return@collect
                }
                val anchor = session.outlineIndex.textAnchorForPage(page, textAnchors) ?: return@collect
                if (anchor.lineId != currentSelectedLineId) currentOnLineSelected(anchor.lineId)
            }
    }

    LaunchedEffect(findState.text, showFind, session) {
        val query = findState.text.toString()
        AppSettings.setFindQuery(tabId, if (query.length >= 2) query else "")
        if (!showFind || query.length < 2) {
            searchMatches = emptyList()
            selectedMatchIndex = -1
            return@LaunchedEffect
        }
        delay(PDF_SEARCH_DEBOUNCE_MS)
        val matches = withContext(Dispatchers.IO) { session.searchPages(query) }
        searchMatches = matches
        selectedMatchIndex =
            matches.indexOfFirst { it.pageIndex >= listState.mostVisiblePage() }
                .takeIf { it >= 0 }
                ?: if (matches.isEmpty()) -1 else 0
    }

    fun navigateToMatch(forward: Boolean) {
        if (searchMatches.isEmpty()) return
        selectedMatchIndex =
            if (forward) {
                (selectedMatchIndex + 1).mod(searchMatches.size)
            } else {
                (selectedMatchIndex - 1).mod(searchMatches.size)
            }
        val page = searchMatches[selectedMatchIndex].pageIndex
        programmaticTarget = page
        scope.launch { listState.animateScrollToItem(page) }
    }

    Box(modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        PdfPages(
            session = session,
            zoom = zoom,
            listState = listState,
            onZoomChange = onZoomChange,
            searchMatches = searchMatches,
            selectedMatchIndex = selectedMatchIndex,
            modifier = Modifier.fillMaxSize(),
        )
        if (showFind) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).zIndex(2f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (findState.text.length >= 2) {
                    CountBadge(searchMatches.size)
                    Spacer(Modifier.width(8.dp))
                }
                FindInPageBar(
                    state = findState,
                    onEnterNext = { navigateToMatch(true) },
                    onEnterPrev = { navigateToMatch(false) },
                    onClose = { AppSettings.closeFindBar(tabId) },
                    showSmartModeToggle = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PdfPages(
    session: PdfDocumentSession,
    zoom: Float,
    listState: LazyListState,
    onZoomChange: (Float) -> Unit,
    searchMatches: List<PdfSearchMatch>,
    selectedMatchIndex: Int,
    modifier: Modifier = Modifier,
) {
    val horizontalScrollState = rememberScrollState()
    val renderSnapshot by session.renderSnapshot.collectAsState()
    val textPages by session.textPages.collectAsState()
    val currentPage by remember(listState) { derivedStateOf { listState.mostVisiblePage() } }
    val isScrolling by remember(listState) { derivedStateOf { listState.isScrollInProgress } }
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val renderDpi = remember(zoom) {
        val rawDpi = (PDF_BASE_DPI * max(1f, zoom)).coerceAtMost(PDF_MAX_DPI.toFloat())
        (rawDpi / PDF_DPI_STEP).roundToInt() * PDF_DPI_STEP
    }
    LaunchedEffect(session, currentPage, renderDpi, isScrolling) {
        withContext(Dispatchers.IO) {
            runCatching { session.loadTextPage(currentPage) }
            progressiveRenderPlan(currentPage, renderDpi, session.pageCount, isScrolling).forEach { request ->
                currentCoroutineContext().ensureActive()
                runCatching { session.render(request.pageIndex, request.dpi) }
                    .onFailure { session.recordRenderFailure(request.pageIndex) }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(
        modifier
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val modifiers = event.keyboardModifiers
                if (!(modifiers.isCtrlPressed || modifiers.isMetaPressed)) return@onPointerEvent
                val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                val zoomDelta = if (abs(delta.y) >= abs(delta.x)) delta.y else delta.x
                if (zoomDelta == 0f) return@onPointerEvent
                val exponent = (-zoomDelta * 0.08f).coerceIn(-0.25f, 0.25f)
                currentOnZoomChange(
                    (currentZoom * exp(exponent.toDouble()).toFloat()).coerceIn(PDF_ZOOM_MIN, PDF_ZOOM_MAX),
                )
                event.changes.forEach { it.consume() }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    if (gestureZoom != 1f) {
                        currentOnZoomChange((currentZoom * gestureZoom).coerceIn(PDF_ZOOM_MIN, PDF_ZOOM_MAX))
                    }
                }
            },
    ) {
        val contentWidth = maxWidth * max(1f, zoom)
        val pageWidthFraction = if (zoom < 1f) zoom else 0.96f
        LaunchedEffect(zoom, horizontalScrollState.maxValue) {
            if (horizontalScrollState.maxValue > 0) {
                horizontalScrollState.scrollTo(horizontalScrollState.maxValue / 2)
            }
        }
        Box(Modifier.fillMaxSize().horizontalScroll(horizontalScrollState, reverseScrolling = true)) {
            LazyColumn(
                modifier = Modifier.width(contentWidth).fillMaxHeight(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                items(session.pageCount, key = { it }) { pageIndex ->
                    val renderedPage = renderSnapshot.pages[pageIndex]
                    val pageState =
                        when {
                            renderedPage != null -> PdfPageState.Ready(renderedPage)
                            pageIndex in renderSnapshot.failedPages -> PdfPageState.Failed("")
                            else -> PdfPageState.Loading
                        }
                    val pageHighlights =
                        searchMatches.mapIndexedNotNull { index, match ->
                            if (match.pageIndex == pageIndex) {
                                PdfPageHighlight(match.bounds, index == selectedMatchIndex)
                            } else {
                                null
                            }
                        }
                    PdfPageCard(
                        state = pageState,
                        aspectRatio = session.displayAspectRatio,
                        actualAspectRatio = session.pageAspectRatios[pageIndex],
                        widthFraction = pageWidthFraction,
                        highlights = pageHighlights,
                        pageText = textPages[pageIndex],
                    )
                }
            }
        }
        if (horizontalScrollState.maxValue > 0) {
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}
    }

@Composable
private fun PdfPageCard(
    state: PdfPageState,
    aspectRatio: Float,
    actualAspectRatio: Float,
    widthFraction: Float,
    highlights: List<PdfPageHighlight>,
    pageText: PdfPageText?,
) {
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var selectionStart by remember(pageText) { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember(pageText) { mutableStateOf<Offset?>(null) }
    var isSelecting by remember(pageText) { mutableStateOf(false) }
    val selectedGlyphs =
        remember(pageText, selectionStart, selectionEnd, cardSize, actualAspectRatio) {
            val start = selectionStart
            val end = selectionEnd
            if (pageText == null || start == null || end == null || cardSize == IntSize.Zero) {
                emptyList()
            } else {
                val size = Size(cardSize.width.toFloat(), cardSize.height.toFloat())
                pageText.selectTextFlow(start, end, size, actualAspectRatio)
            }
        }
    val selectedText = remember(selectedGlyphs) { selectedGlyphs.toPdfSelectionText() }

    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .onSizeChanged { cardSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.C &&
                    (event.isCtrlPressed || event.isMetaPressed) &&
                    selectedText.isNotEmpty()
                ) {
                    clipboard.setText(AnnotatedString(selectedText))
                    true
                } else {
                    false
                }
            }.pointerInput(pageText, cardSize, actualAspectRatio) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val size = Size(cardSize.width.toFloat(), cardSize.height.toFloat())
                        isSelecting =
                            pageText?.glyphs?.any {
                                it.bounds.fitToPage(size, actualAspectRatio).contains(offset)
                            } == true
                        if (isSelecting) {
                            selectionStart = offset
                            selectionEnd = offset
                            focusRequester.requestFocus()
                        }
                    },
                    onDrag = { change, _ ->
                        if (isSelecting) {
                            selectionEnd = change.position
                            change.consume()
                        }
                    },
                    onDragEnd = { isSelecting = false },
                    onDragCancel = { isSelecting = false },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PdfPageState.Loading -> Unit
            is PdfPageState.Failed ->
                Text(
                    stringResource(Res.string.pdf_render_failed),
                    color = Color(0xFFB00020),
                    modifier = Modifier.padding(16.dp),
                )
            is PdfPageState.Ready ->
                Image(
                    bitmap = state.page.bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
        }
        Canvas(Modifier.fillMaxSize()) {
            highlights.forEach { highlight ->
                val color = if (highlight.isCurrent) PDF_ACTIVE_HIGHLIGHT_COLOR else PDF_HIGHLIGHT_COLOR
                highlight.bounds.forEach { bounds ->
                    val fitted = bounds.fitToPage(size, actualAspectRatio)
                    drawRect(
                        color = color,
                        topLeft = Offset(fitted.left, fitted.top),
                        size = Size(fitted.right - fitted.left, fitted.bottom - fitted.top),
                    )
                }
            }
            mergeGlyphBounds(selectedGlyphs).forEach { bounds ->
                val fitted = bounds.fitToPage(size, actualAspectRatio)
                drawRect(
                    color = PDF_SELECTION_COLOR,
                    topLeft = Offset(fitted.left, fitted.top),
                    size = Size(fitted.right - fitted.left, fitted.bottom - fitted.top),
                )
            }
        }
    }
}

private data class PdfPageHighlight(
    val bounds: List<PdfNormalizedRect>,
    val isCurrent: Boolean,
)

@Composable
private fun MissingPdfPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.55f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JewelTheme.globalColors.borders.disabled.copy(alpha = 0.14f))
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Book, contentDescription = null, modifier = Modifier.size(42.dp))
            Text(stringResource(Res.string.pdf_missing), color = JewelTheme.globalColors.text.normal)
            Text(stringResource(Res.string.pdf_missing_settings), color = JewelTheme.globalColors.text.info)
        }
    }
}

@Composable
private fun LoadingPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.pdf_loading))
        }
    }
}

@Composable
private fun PdfErrorPanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    val detail = message.ifBlank { stringResource(Res.string.pdf_unknown_error) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.pdf_load_failed, detail),
            color = Color(0xFFB00020),
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun LazyListState.mostVisiblePage(): Int {
    val layout = layoutInfo
    if (layout.visibleItemsInfo.isEmpty()) return firstVisibleItemIndex
    return layout.visibleItemsInfo
        .maxByOrNull { item ->
            val visibleStart = max(item.offset, layout.viewportStartOffset)
            val visibleEnd = min(item.offset + item.size, layout.viewportEndOffset)
            (visibleEnd - visibleStart).coerceAtLeast(0)
        }?.index ?: firstVisibleItemIndex
}

private sealed interface PdfLoadState {
    data object Loading : PdfLoadState

    data class Ready(
        val session: PdfDocumentSession,
    ) : PdfLoadState

    data class Failed(
        val message: String,
    ) : PdfLoadState
}

private sealed interface PdfPageState {
    data object Loading : PdfPageState

    data class Ready(
        val page: RenderedPdfPage,
    ) : PdfPageState

    data class Failed(
        val message: String,
    ) : PdfPageState
}

@Stable
private data class RenderedPdfPage(
    val bitmap: ImageBitmap,
)

@Stable
private data class PdfRenderSnapshot(
    val pages: Map<Int, RenderedPdfPage> = emptyMap(),
    val failedPages: Set<Int> = emptySet(),
)

/** One open PDFBox document per visible PDF tab, guarded because PDFBox renderers are not thread-safe. */
private class PdfDocumentSession private constructor(
    private val document: PDDocument,
    val outlineIndex: PdfOutlineIndex,
    val pageAspectRatios: List<Float>,
    val displayAspectRatio: Float,
    private val searchIndex: PdfTextSearchIndex,
) : AutoCloseable {
    private val renderer = PDFRenderer(document)
    private val lock = Any()
    private var closed = false
    private val pageCache =
        object : LinkedHashMap<Int, CachedRenderedPdfPage>(PDF_CACHE_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CachedRenderedPdfPage>?): Boolean =
                size > PDF_CACHE_SIZE
        }

    private val mutableRenderSnapshot = MutableStateFlow(PdfRenderSnapshot())
    val renderSnapshot: StateFlow<PdfRenderSnapshot> = mutableRenderSnapshot

    val pageCount: Int get() = pageAspectRatios.size

    fun render(
        pageIndex: Int,
        dpi: Int,
    ): RenderedPdfPage =
        synchronized(lock) {
            check(!closed) { "PDF document is closed" }
            val cached = pageCache[pageIndex]
            if (cached != null && cached.dpi >= dpi) return@synchronized cached.page
            val image = renderer.renderImageWithDPI(pageIndex, dpi.toFloat(), ImageType.RGB)
            val source = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            val pixels = ByteArray(source.size * 4)
            source.forEachIndexed { index, argb ->
                val offset = index * 4
                pixels[offset] = (argb and 0xFF).toByte()
                pixels[offset + 1] = ((argb ushr 8) and 0xFF).toByte()
                pixels[offset + 2] = ((argb ushr 16) and 0xFF).toByte()
                pixels[offset + 3] = 0xFF.toByte()
            }
            val info = ImageInfo.makeN32(image.width, image.height, ColorAlphaType.OPAQUE)
            val skiaImage = SkiaImage.makeRaster(info, pixels, image.width * 4)
            val renderedPage =
                try {
                    RenderedPdfPage(skiaImage.toComposeImageBitmap())
                } finally {
                    skiaImage.close()
                }
            renderedPage
                .also {
                    pageCache[pageIndex] = CachedRenderedPdfPage(dpi, it)
                    mutableRenderSnapshot.value =
                        PdfRenderSnapshot(
                            pages = pageCache.mapValues { entry -> entry.value.page },
                            failedPages = mutableRenderSnapshot.value.failedPages - pageIndex,
                        )
                }
        }

    val textPages: StateFlow<Map<Int, PdfPageText>> = searchIndex.pages

    fun searchPages(query: String): List<PdfSearchMatch> = searchIndex.searchMatches(query)

    fun loadTextPage(pageIndex: Int) = searchIndex.page(pageIndex)

    fun recordRenderFailure(pageIndex: Int) {
        synchronized(lock) {
            mutableRenderSnapshot.value =
                mutableRenderSnapshot.value.copy(failedPages = mutableRenderSnapshot.value.failedPages + pageIndex)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pageCache.values.forEach { it.page.bitmap.asSkiaBitmap().close() }
            pageCache.clear()
            mutableRenderSnapshot.value = PdfRenderSnapshot()
            searchIndex.close()
            document.close()
        }
    }

    companion object {
        fun open(file: File): PdfDocumentSession {
            val document = Loader.loadPDF(file)
            return try {
                val aspectRatios =
                    document.pages.map { page ->
                        val box = page.cropBox
                        val rotated = page.rotation.mod(180) != 0
                        val width = if (rotated) box.height else box.width
                        val height = if (rotated) box.width else box.height
                        (width / height).coerceAtLeast(0.1f)
                    }
                PdfDocumentSession(
                    document = document,
                    outlineIndex = PdfOutlineIndex(readPdfOutline(document)),
                    pageAspectRatios = aspectRatios,
                    displayAspectRatio = aspectRatios.sorted().getOrNull(aspectRatios.size / 2) ?: PDF_DEFAULT_ASPECT_RATIO,
                    searchIndex = PdfTextSearchIndex(file),
                )
            } catch (error: Throwable) {
                document.close()
                throw error
            }
        }
    }
}

/** Builds a lazy, page-level index from the PDF's embedded text. No OCR is involved. */
private class PdfTextSearchIndex(
    private val file: File,
) : AutoCloseable {
    private val lock = Any()
    private val document = Loader.loadPDF(file)
    private val pageCache = arrayOfNulls<PdfPageText>(document.numberOfPages)
    private val mutablePages = MutableStateFlow<Map<Int, PdfPageText>>(emptyMap())
    val pages: StateFlow<Map<Int, PdfPageText>> = mutablePages
    private var pageTexts: List<String>? = null

    fun page(pageIndex: Int): PdfPageText =
        synchronized(lock) {
            pageCache[pageIndex]?.let { return@synchronized it }
            val stripper = PositionCollectingPdfStripper(pageIndex)
            stripper.getText(document)
            val page = PdfPageText(stripper.glyphs.toList())
            pageCache[pageIndex] = page
            mutablePages.value = mutablePages.value + (pageIndex to page)
            page
        }

    fun searchMatches(query: String): List<PdfSearchMatch> =
        synchronized(lock) {
            val needle = normalizePdfMatchKey(query)
            if (needle.length < 2) return@synchronized emptyList()
            buildList {
                for (pageIndex in pageCache.indices) {
                    addAll(findMatches(pageIndex, page(pageIndex), needle))
                }
            }
        }


    fun search(query: String): List<Int> =
        synchronized(lock) {
            val needle = normalizePdfSearchText(query)
            if (needle.length < 2) return@synchronized emptyList()
            val reversedNeedle = needle.reversed()
            val texts = pageTexts ?: extractPageTexts().also { pageTexts = it }
            texts.mapIndexedNotNull { index, text ->
                index.takeIf {
                    text.contains(needle, ignoreCase = true) ||
                        text.contains(reversedNeedle, ignoreCase = true)
                }
            }
        }

    private fun extractPageTexts(): List<String> =
        Loader.loadPDF(file).use { searchDocument ->
            List(searchDocument.numberOfPages) { pageIndex ->
                PDFTextStripper()
                    .apply {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                        sortByPosition = true
                    }.getText(searchDocument)
                    .let(::normalizePdfSearchText)
            }
        }

    override fun close() =
        synchronized(lock) {
            pageTexts = null
            pageCache.fill(null)
            mutablePages.value = emptyMap()
            document.close()
}
}

private fun normalizePdfSearchText(text: String): String =
    text.replace(PDF_DIRECTIONAL_MARKS, "").replace(PDF_SEARCH_WHITESPACE, " ").trim()
private class PositionCollectingPdfStripper(pageIndex: Int) : PDFTextStripper() {
    val glyphs = mutableListOf<PdfGlyph>()

    init {
        startPage = pageIndex + 1
        endPage = pageIndex + 1
        sortByPosition = true
    }

    override fun processTextPosition(text: TextPosition) {
        val pageWidth = text.pageWidth.coerceAtLeast(1f)
        val pageHeight = text.pageHeight.coerceAtLeast(1f)
        val bounds =
            PdfNormalizedRect(
                left = (text.xDirAdj / pageWidth).coerceIn(0f, 1f),
                top = ((text.yDirAdj - text.heightDir) / pageHeight).coerceIn(0f, 1f),
                right = ((text.xDirAdj + text.widthDirAdj) / pageWidth).coerceIn(0f, 1f),
                bottom = (text.yDirAdj / pageHeight).coerceIn(0f, 1f),
            )
        text.unicode.orEmpty().forEach { character ->
            if (!character.isWhitespace() && !character.isPdfDirectionalMark()) {
                glyphs += PdfGlyph(character.toString(), bounds)
            }
        }
        super.processTextPosition(text)
    }
}

private fun findMatches(pageIndex: Int, page: PdfPageText, needle: String): List<PdfSearchMatch> {
    val logicalGlyphs = page.glyphs.toLogicalPdfOrder()
    val searchable = logicalGlyphs.joinToString(separator = "") { it.text.lowercase() }
    return buildList {
        var start = searchable.indexOf(needle)
        while (start >= 0) {
            val glyphs = logicalGlyphs.subList(start, start + needle.length)
            add(PdfSearchMatch(pageIndex, mergeGlyphBounds(glyphs)))
            start = searchable.indexOf(needle, start + 1)
        }
    }.distinctBy { it.bounds }
}

private fun mergeGlyphBounds(glyphs: List<PdfGlyph>): List<PdfNormalizedRect> =
    buildList {
        glyphs.forEach { glyph ->
            val previous = lastOrNull()
            if (previous == null || abs(previous.verticalCenter - glyph.bounds.verticalCenter) > PDF_LINE_TOLERANCE) {
                add(glyph.bounds)
            } else {
                this[lastIndex] = previous.union(glyph.bounds)
            }
        }
    }

private fun normalizePdfMatchKey(text: String): String =
    text.filterNot { it.isWhitespace() || it.isPdfDirectionalMark() }.lowercase()

private fun Char.isPdfDirectionalMark(): Boolean =
    this == '\u200E' || this == '\u200F' || this in '\u202A'..'\u202E' || this in '\u2066'..'\u2069'
private fun Char.isHebrewCharacter(): Boolean = this in '\u0590'..'\u05FF' || this in '\uFB1D'..'\uFB4F'

/** PDF text streams commonly store Hebrew glyphs in visual order inside each word. */
private fun List<PdfGlyph>.toLogicalPdfOrder(): List<PdfGlyph> =
    buildList {
        val word = mutableListOf<PdfGlyph>()

        fun flushWord() {
            if (word.any { glyph -> glyph.text.any(Char::isHebrewCharacter) }) {
                addAll(word.asReversed())
            } else {
                addAll(word)
            }
            word.clear()
        }

        this@toLogicalPdfOrder.forEach { glyph ->
            val previous = word.lastOrNull()
            if (previous != null) {
                val newLine = abs(previous.bounds.verticalCenter - glyph.bounds.verticalCenter) > PDF_LINE_TOLERANCE
                val horizontalGap =
                    max(
                        glyph.bounds.left - previous.bounds.right,
                        previous.bounds.left - glyph.bounds.right,
                    )
                if (newLine || horizontalGap > PDF_WORD_GAP) flushWord()
            }
            word += glyph
        }
        flushWord()
    }


/** Selects by reading lines and follows the overlapping text column where the drag began. */
private fun PdfPageText.selectTextFlow(
    start: Offset,
    end: Offset,
    pageSize: Size,
    aspectRatio: Float,
): List<PdfGlyph> {
    if (glyphs.isEmpty()) return emptyList()
    fun rect(glyph: PdfGlyph) = glyph.bounds.fitToPage(pageSize, aspectRatio)
    var startGlyph = glyphs.minByOrNull { rect(it).distanceSquaredTo(start) } ?: return emptyList()
    var endGlyph = glyphs.minByOrNull { rect(it).distanceSquaredTo(end) } ?: startGlyph
    val baselines = glyphs.toPdfBaselines()
    var startBaseline = baselines.indexOfFirst { baseline -> baseline.any { segment -> startGlyph in segment } }
    var endBaseline = baselines.indexOfFirst { baseline -> baseline.any { segment -> endGlyph in segment } }
    if (startBaseline < 0) return emptyList()
    if (endBaseline < 0) endBaseline = startBaseline
    var startPoint = start
    var endPoint = end
    if (startBaseline > endBaseline) {
        val glyphSwap = startGlyph
        startGlyph = endGlyph
        endGlyph = glyphSwap
        val baselineSwap = startBaseline
        startBaseline = endBaseline
        endBaseline = baselineSwap
        startPoint = end
        endPoint = start
    }
    val chosen = mutableListOf<List<PdfGlyph>>()
    var segment = baselines[startBaseline].first { startGlyph in it }
    chosen += segment
    var lineIndex = startBaseline + 1
    while (lineIndex <= endBaseline) {
        val candidate = baselines[lineIndex].maxByOrNull(segment::continuationScore)
        if (candidate != null && segment.continuationScore(candidate) >= PDF_BLOCK_CONTINUATION_THRESHOLD) {
            segment = candidate
            chosen += candidate
        }
        lineIndex += 1
    }
    if (chosen.size == 1) {
        val low = min(startPoint.x, endPoint.x)
        val high = max(startPoint.x, endPoint.x)
        return chosen.single().filter { rect(it).right >= low && rect(it).left <= high }
    }
    return buildList {
        chosen.forEachIndexed { index, line ->
            val rtl = line.count { it.text.any(Char::isHebrewCharacter) } * 2 >= line.size
            addAll(
                when (index) {
                    0 -> line.filter { rect(it).let { r -> if (rtl) r.left <= startPoint.x else r.right >= startPoint.x } }
                    chosen.lastIndex -> line.filter { rect(it).let { r -> if (rtl) r.right >= endPoint.x else r.left <= endPoint.x } }
                    else -> line
                },
            )
        }
    }
}

private fun List<PdfGlyph>.toPdfBaselines(): List<List<List<PdfGlyph>>> {
    val lines = mutableListOf<MutableList<PdfGlyph>>()
    sortedWith(compareBy<PdfGlyph> { it.bounds.verticalCenter }.thenBy { it.bounds.left }).forEach { glyph ->
        val line = lines.lastOrNull()
        if (line == null || abs(line.first().bounds.verticalCenter - glyph.bounds.verticalCenter) > PDF_LINE_TOLERANCE) {
            lines += mutableListOf(glyph)
        } else {
            line += glyph
        }
    }
    return lines.map { line ->
        val segments = mutableListOf<MutableList<PdfGlyph>>()
        line.sortedBy { it.bounds.left }.forEach { glyph ->
            val segment = segments.lastOrNull()
            if (segment == null || glyph.bounds.left - segment.last().bounds.right > PDF_COLUMN_GAP) {
                segments += mutableListOf(glyph)
            } else {
                segment += glyph
            }
        }
        segments
    }
}

private fun List<PdfGlyph>.continuationScore(other: List<PdfGlyph>): Float {
    val thisLeft = minOf { it.bounds.left }
    val thisRight = maxOf { it.bounds.right }
    val otherLeft = other.minOf { it.bounds.left }
    val otherRight = other.maxOf { it.bounds.right }
    val overlap = (min(thisRight, otherRight) - max(thisLeft, otherLeft)).coerceAtLeast(0f)
    val narrower = min(thisRight - thisLeft, otherRight - otherLeft).coerceAtLeast(0.001f)
    val centerDistance = abs((thisLeft + thisRight) / 2f - (otherLeft + otherRight) / 2f)
    return overlap / narrower - centerDistance
}
private data class PdfPageText(val glyphs: List<PdfGlyph>)

private data class PdfGlyph(val text: String, val bounds: PdfNormalizedRect)

private data class PdfSearchMatch(val pageIndex: Int, val bounds: List<PdfNormalizedRect>)

private data class PdfNormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val verticalCenter: Float get() = (top + bottom) / 2f

    fun union(other: PdfNormalizedRect): PdfNormalizedRect =
        PdfNormalizedRect(
            left = min(left, other.left),
            top = min(top, other.top),
            right = max(right, other.right),
            bottom = max(bottom, other.bottom),
        )
}
private data class PdfPixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(point: Offset): Boolean =
        point.x in (left - PDF_SELECTION_HIT_SLOP)..(right + PDF_SELECTION_HIT_SLOP) &&
            point.y in (top - PDF_SELECTION_HIT_SLOP)..(bottom + PDF_SELECTION_HIT_SLOP)

    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        this.right >= left && this.left <= right && this.bottom >= top && this.top <= bottom
    fun distanceSquaredTo(point: Offset): Float {
        val dx = when {
            point.x < left -> left - point.x
            point.x > right -> point.x - right
            else -> 0f
        }
        val dy = when {
            point.y < top -> top - point.y
            point.y > bottom -> point.y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }
}

private fun PdfNormalizedRect.fitToPage(size: Size, actualAspectRatio: Float): PdfPixelRect {
    if (size.width <= 0f || size.height <= 0f) return PdfPixelRect(0f, 0f, 0f, 0f)
    val cardAspectRatio = size.width / size.height
    val contentWidth: Float
    val contentHeight: Float
    val offsetX: Float
    val offsetY: Float
    if (actualAspectRatio >= cardAspectRatio) {
        contentWidth = size.width
        contentHeight = size.width / actualAspectRatio
        offsetX = 0f
        offsetY = (size.height - contentHeight) / 2f
    } else {
        contentHeight = size.height
        contentWidth = size.height * actualAspectRatio
        offsetX = (size.width - contentWidth) / 2f
        offsetY = 0f
    }
    return PdfPixelRect(
        left = offsetX + left * contentWidth,
        top = offsetY + top * contentHeight,
        right = offsetX + right * contentWidth,
        bottom = offsetY + bottom * contentHeight,
    )
}

private fun List<PdfGlyph>.toPdfSelectionText(): String {
    val logicalGlyphs = toLogicalPdfOrder()
    return buildString {
        logicalGlyphs.forEachIndexed { index, glyph ->
            val previous = logicalGlyphs.getOrNull(index - 1)
            if (previous != null) {
                val newLine =
                    abs(previous.bounds.verticalCenter - glyph.bounds.verticalCenter) > PDF_LINE_TOLERANCE
                if (newLine) {
                    appendLine()
                } else {
                    val horizontalGap =
                        max(
                            glyph.bounds.left - previous.bounds.right,
                            previous.bounds.left - glyph.bounds.right,
                        )
                    if (horizontalGap > PDF_WORD_GAP) append(' ')
                }
            }
            append(glyph.text)
        }
    }

}

private data class CachedRenderedPdfPage(
    val dpi: Int,
    val page: RenderedPdfPage,
)

private data class RenderRequest(
    val pageIndex: Int,
    val dpi: Int,
)

private fun progressiveRenderPlan(
    currentPage: Int,
    targetDpi: Int,
    pageCount: Int,
    isScrolling: Boolean,
): List<RenderRequest> =
    buildList {
        fun addIfValid(pageIndex: Int, dpi: Int) {
            if (pageIndex in 0 until pageCount) add(RenderRequest(pageIndex, dpi))
        }

        addIfValid(currentPage, if (isScrolling) min(targetDpi, PDF_ACTIVE_PREVIEW_DPI) else targetDpi)

        for (distance in 1..PDF_PREFETCH_DISTANCE) {
            val dpi =
                when (distance) {
                    1, 2 -> PDF_SCROLL_PREVIEW_NEAR_DPI
                    in 3..5 -> PDF_SCROLL_PREVIEW_MID_DPI
                    else -> PDF_SCROLL_PREVIEW_FAR_DPI
                }
            addIfValid(currentPage + distance, dpi)
            addIfValid(currentPage - distance, dpi)
        }

        if (!isScrolling) {
            for (distance in 1..PDF_QUALITY_UPGRADE_DISTANCE) {
                val dpi =
                    when (distance) {
                        1 -> PDF_NEARBY_DPI
                        2 -> PDF_DISTANT_DPI
                        else -> PDF_FAR_DPI
                    }
                addIfValid(currentPage + distance, dpi)
                addIfValid(currentPage - distance, dpi)
            }
        }
    }.distinct()

private fun readPdfOutline(document: PDDocument): List<PdfOutlineEntry> =
    buildList {
        document.documentCatalog.documentOutline?.firstChild?.let { appendOutline(document, it, 0) }
    }

private fun MutableList<PdfOutlineEntry>.appendOutline(
    document: PDDocument,
    item: PDOutlineItem,
    level: Int,
) {
    var current: PDOutlineItem? = item
    while (current != null) {
        val destination = current.destination ?: (current.action as? PDActionGoTo)?.destination
        resolvePageIndex(document, destination)?.takeIf { it >= 0 }?.let { pageIndex ->
            add(PdfOutlineEntry(current.title.orEmpty(), level, pageIndex))
        }
        current.firstChild?.let { appendOutline(document, it, level + 1) }
        current = current.nextSibling
    }
}

private fun resolvePageIndex(
    document: PDDocument,
    destination: PDDestination?,
): Int? =
    when (destination) {
        is PDPageDestination -> destination.retrievePageNumber()
        is PDNamedDestination -> document.documentCatalog.findNamedDestinationPage(destination)?.retrievePageNumber()
        else -> null
    }

private const val PDF_BASE_DPI = 180f
private const val PDF_MAX_DPI = 300
private const val PDF_DPI_STEP = 15
private const val PDF_ACTIVE_PREVIEW_DPI = 72
private const val PDF_SEARCH_DEBOUNCE_MS = 140L
private const val PDF_LINE_TOLERANCE = 0.006f
private const val PDF_WORD_GAP = 0.004f
private const val PDF_SELECTION_HIT_SLOP = 3f
private val PDF_HIGHLIGHT_COLOR = Color(0x66FFF176)
private val PDF_ACTIVE_HIGHLIGHT_COLOR = Color(0x99FFB300)
private val PDF_SELECTION_COLOR = Color(0x665B9DFF)

private const val PDF_DEFAULT_ASPECT_RATIO = 0.707f
private val PDF_DIRECTIONAL_MARKS = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
private val PDF_SEARCH_WHITESPACE = Regex("\\s+")
private const val PDF_NEARBY_DPI = 84
private const val PDF_DISTANT_DPI = 66
private const val PDF_FAR_DPI = 54
private const val PDF_SCROLL_PREVIEW_NEAR_DPI = 48
private const val PDF_SCROLL_PREVIEW_MID_DPI = 36
private const val PDF_SCROLL_PREVIEW_FAR_DPI = 30
private const val PDF_PREFETCH_DISTANCE = 10
private const val PDF_QUALITY_UPGRADE_DISTANCE = 3
private const val PDF_CACHE_SIZE = 24
