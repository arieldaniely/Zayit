@file:OptIn(dev.nucleusframework.window.ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.tao.DockLayout
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.SatellitePlacement
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.handleBookShortcut
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BreadcrumbSection
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.LocalBookTextReady
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.isBookTextShown
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.DockedPaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineSideOrder
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.NavigationLayeredSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderSplitter
import io.github.kdroidfilter.seforimapp.framework.desktop.DockSizes
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Under the tab strip, laid out as the reader's split panes were: the activity bars around the
 * outer dock (book tree, contents, notes — full height on the start side), whose centre is the
 * inner dock (links, commentaries, sources) around the selected tab's text, with the book's
 * breadcrumb under it. Every pane is a satellite drawing the selected tab.
 */
@Composable
fun WindowBody(openWindow: OpenWindow) {
    val tabs by openWindow.tabsViewModel.state.collectAsState()
    val selected = tabs.tabs.getOrNull(tabs.selectedTabIndex)
    val destination = selected?.destination
    // Home, a book or a search: the tabs with the reader's bars and panes (not History / Favorites).
    val readerDestination =
        destination?.takeIf {
            it is TabsDestination.Home ||
                it is TabsDestination.BookContent ||
                it is TabsDestination.PdfContent ||
                it is TabsDestination.Search
        }
    val lineWorkspace = openWindow.panesOf(navigation = false)
    // The book view's shortcuts (Ctrl/Cmd+B, K, J) act on the selected reader tab, as they did
    // over the whole book screen.
    val readerEvents =
        if (readerDestination != null) {
            key(readerDestination.tabId) { tabBookViewModel(openWindow.ownerOf(readerDestination.tabId), readerDestination)::onEvent }
        } else {
            null
        }
    // Also from the window itself, which gets the keys when nothing has focus (no pane, no text).
    val bottomOpen =
        lineWorkspace.satellites.any { entry ->
            entry.isOpen && (entry.placement as? SatellitePlacement.Docked)?.side == DockSide.Bottom
        }

    val density = LocalDensity.current
    var outerWidth by remember { mutableIntStateOf(0) }
    var innerSize by remember { mutableStateOf(IntSize.Zero) }
    // The panes are sized as fractions of these, as the split panes were (see PaneSizeSync).
    LaunchedEffect(outerWidth, innerSize, density) {
        if (outerWidth > 0 && innerSize.width > 0) {
            openWindow.dockSizes = DockSizes(outerWidth, innerSize.width, innerSize.height, density.density)
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(canvasBackground())
            .onPreviewKeyEvent { event -> readerEvents?.let { handleBookShortcut(event, it) } ?: false },
    ) {
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(openWindow, readerDestination, start = true) }
        }
        DockLayout(
            workspace = openWindow.panesOf(navigation = true),
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).fillMaxHeight()
                    .dockPreviewGlow(openWindow.panesOf(true))
                    .onSizeChanged { outerWidth = it.width },
            layeredSides = NavigationLayeredSides,
            splitter = { ReaderSplitter() },
            panel = { panel -> PaneCard { panel() } },
        ) {
            Column(Modifier.fillMaxSize()) {
                DockLayout(
                    workspace = lineWorkspace,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .dockPreviewGlow(lineWorkspace)
                            .onSizeChanged { innerSize = it },
                    sideOrder = LineSideOrder,
                    splitter = { ReaderSplitter() },
                    panel = { panel -> DockedPaneCard(bottomOpen) { panel() } },
                ) {
                    // A book's text first lays out once its panes are in place, as with the split panes.
                    CompositionLocalProvider(LocalBookTextReady provides { tabId -> openWindow.panesReadyFor == tabId }) {
                        TabsContent()
                    }
                }
                if (readerDestination != null) {
                    key(readerDestination.tabId) { ReaderBreadcrumb(openWindow, readerDestination) }
                }
            }
        }
        if (readerDestination != null) {
            key(readerDestination.tabId) { ReaderBar(openWindow, readerDestination, start = false) }
        }
    }
}

@Composable
private fun ReaderBreadcrumb(
    openWindow: OpenWindow,
    destination: TabsDestination,
) {
    val viewModel = tabBookViewModel(openWindow.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    val expanded by io.github.kdroidfilter.seforimapp.core.settings.AppSettings.bookDetailsPaneFlow
        .collectAsState()
    val details = if (expanded) viewModel.bookDetailsFlow.collectAsState().value else null
    if (uiState.navigation.selectedBook != null) {
        BreadcrumbSection(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            verticalPadding = 8.dp,
            isIslands =
                io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
                    .isIslandsStyle(),
            bookDetails = details,
        )
    }
}

/** One of the two activity bars of a reader tab (start: navigation panes; end: text and line panes). */
@Composable
private fun ReaderBar(
    openWindow: OpenWindow,
    destination: TabsDestination,
    start: Boolean,
) {
    val viewModel = tabBookViewModel(openWindow.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    val diacritics by viewModel.showDiacritics.collectAsState()
    // A search's results have no navigation bar (their tabs filter); the end bar keeps the zoom for the preview
    Box(Modifier.fillMaxHeight()) {
        if (start) {
            StartVerticalBar(uiState = uiState, onEvent = viewModel::onEvent, showNotes = destination !is TabsDestination.Search)
        } else if (uiState.navigation.selectedBook != null || destination is TabsDestination.Search) {
            EndVerticalBar(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                showDiacritics = diacritics,
                isPdfEdition = destination is TabsDestination.PdfContent,
                showBookDetailsAction = destination is TabsDestination.Search && !isBookTextShown(uiState),
                pdfCanZoomIn =
                    io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomControllerRegistry.canZoom(
                        destination.tabId,
                        io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomCommand.ZoomIn,
                    ),
                pdfCanZoomOut =
                    io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomControllerRegistry.canZoom(
                        destination.tabId,
                        io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomCommand.ZoomOut,
                    ),
                onPdfZoomIn = {
                    io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomControllerRegistry.dispatch(
                        destination.tabId,
                        io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomCommand.ZoomIn,
                    )
                },
                onPdfZoomOut = {
                    io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomControllerRegistry.dispatch(
                        destination.tabId,
                        io.github.kdroidfilter.seforimapp.features.pdf.PdfZoomCommand.ZoomOut,
                    )
                },
            )
        }
    }
}

@Composable
internal fun canvasBackground() = JewelTheme.globalColors.toolwindowBackground

/** Only the requested glow overrides the framework's preview appearance; Nucleus handles all drop targets. */
@Composable
private fun Modifier.dockPreviewGlow(workspace: dev.nucleusframework.window.tao.SatelliteWorkspace): Modifier {
    val accent = JewelTheme.globalColors.outlines.focused
    return this.then(
        Modifier.drawWithContent {
            drawContent()
            workspace.dockPreview?.let { target ->
                val rect =
                    when (target.side) {
                        DockSide.Left ->
                            androidx.compose.ui.geometry
                                .Rect(0f, 0f, size.width * 0.3f, size.height)
                        DockSide.Right ->
                            androidx.compose.ui.geometry
                                .Rect(size.width * 0.7f, 0f, size.width, size.height)
                        DockSide.Bottom ->
                            androidx.compose.ui.geometry
                                .Rect(0f, size.height * 0.65f, size.width, size.height)
                        DockSide.Top ->
                            androidx.compose.ui.geometry
                                .Rect(0f, 0f, size.width, size.height * 0.35f)
                    }
                listOf(18f to 0.05f, 10f to 0.1f, 5f to 0.22f, 1.5f to 0.9f).forEach { (width, alpha) ->
                    drawRect(
                        accent.copy(alpha = alpha),
                        rect.topLeft,
                        rect.size,
                        style =
                            androidx.compose.ui.graphics.drawscope
                                .Stroke(width.dp.toPx()),
                    )
                }
            }
        },
    )
}
