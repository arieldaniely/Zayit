package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableIconButtonWithToolip
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.VerticalLateralBarPosition
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.kdroidfilter.seforimapp.icons.Library
import io.github.kdroidfilter.seforimapp.icons.TableOfContents
import io.github.kdroidfilter.seforimapp.icons.ZoomIn
import io.github.kdroidfilter.seforimapp.icons.ZoomOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.back_to_text_edition
import seforimapp.seforimapp.generated.resources.pdf_book_list_hint
import seforimapp.seforimapp.generated.resources.pdf_commentaries_hint
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_edition_title
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_import_dialog_title
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import seforimapp.seforimapp.generated.resources.pdf_installing
import seforimapp.seforimapp.generated.resources.pdf_loading
import seforimapp.seforimapp.generated.resources.pdf_missing
import seforimapp.seforimapp.generated.resources.pdf_no_outline
import seforimapp.seforimapp.generated.resources.pdf_pages_loading
import seforimapp.seforimapp.generated.resources.pdf_printed_view
import seforimapp.seforimapp.generated.resources.pdf_table_of_contents
import seforimapp.seforimapp.generated.resources.pdf_text_edition_tooltip
import seforimapp.seforimapp.generated.resources.pdf_zoom_in_tooltip
import seforimapp.seforimapp.generated.resources.pdf_zoom_out_tooltip
import seforimapp.seforimapp.generated.resources.table_of_contents
import seforimapp.seforimapp.generated.resources.zoom_in
import seforimapp.seforimapp.generated.resources.zoom_out
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun PdfContentScreen(
    bookId: Long,
    lineId: Long?,
    tabId: String,
) {
    val graph = LocalAppGraph.current
    var title by remember(bookId) { mutableStateOf<String?>(null) }
    var pdf by remember(bookId) { mutableStateOf<File?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var downloadRequested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(0.78f) }

    LaunchedEffect(bookId, reloadToken) {
        val book = withContext(Dispatchers.IO) { graph.repository.getBookCore(bookId) }
        title = book?.title
        pdf = book?.title?.let { TalmudPdfService.pdfForTitle(it) }
        if (book != null) graph.tabTitleUpdateManager.updateTabTitle(tabId, book.title, TabType.BOOK)
    }

    Row(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
        VerticalLateralBar(
            position = VerticalLateralBarPosition.Start,
            topContent = {
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.pdf_book_list_hint),
                    onClick = { showLibrary = !showLibrary },
                    isSelected = showLibrary,
                    icon = Library,
                    iconDescription = stringResource(Res.string.pdf_book_list_hint),
                    label = stringResource(Res.string.pdf_book_list_hint),
                    shortcutHint = if (PlatformInfo.isMacOS) "B+⌘" else "B+Ctrl",
                )
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.pdf_table_of_contents),
                    onClick = { showToc = !showToc },
                    isSelected = showToc,
                    icon = TableOfContents,
                    iconDescription = stringResource(Res.string.table_of_contents),
                    label = stringResource(Res.string.table_of_contents),
                    shortcutHint = if (PlatformInfo.isMacOS) "B+⇧+⌘" else "B+Shift+Ctrl",
                )
            },
            bottomContent = {},
        )
        if (showLibrary || showToc) PdfSidePane(title, pdf, showLibrary, showToc)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PdfHeader(title) {
                graph.tabsViewModel.replaceCurrentTabDestination(TabsDestination.BookContent(bookId, tabId, lineId))
            }
            val file = pdf
            if (file == null) {
                MissingPdfPanel(
                    installing = installing,
                    error = error,
                    onDownload = {
                        downloadRequested = true
                        installing = true
                        error = null
                    },
                    onImport = { showPicker = true },
                )
                if (downloadRequested) {
                    LaunchedEffect(Unit) {
                        runCatching { withContext(Dispatchers.IO) { TalmudPdfService.downloadAndInstall() } }
                            .onFailure { error = it.message }
                        downloadRequested = false
                        installing = false
                        reloadToken++
                    }
                }
            } else {
                PdfPages(file, zoom)
            }
        }
        VerticalLateralBar(
            position = VerticalLateralBarPosition.End,
            topContent = {
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.pdf_zoom_in_tooltip),
                    onClick = { zoom = (zoom + PDF_ZOOM_STEP).coerceAtMost(PDF_ZOOM_MAX) },
                    isSelected = false,
                    icon = ZoomIn,
                    iconDescription = stringResource(Res.string.zoom_in),
                    label = stringResource(Res.string.zoom_in),
                    shortcutHint = if (PlatformInfo.isMacOS) "+⌘" else "+Ctrl",
                )
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.pdf_zoom_out_tooltip),
                    onClick = { zoom = (zoom - PDF_ZOOM_STEP).coerceAtLeast(PDF_ZOOM_MIN) },
                    isSelected = false,
                    icon = ZoomOut,
                    iconDescription = stringResource(Res.string.zoom_out),
                    label = stringResource(Res.string.zoom_out),
                    shortcutHint = if (PlatformInfo.isMacOS) "-⌘" else "-Ctrl",
                )
                SelectableIconButtonWithToolip(
                    toolTipText = stringResource(Res.string.pdf_text_edition_tooltip),
                    onClick = {
                        graph.tabsViewModel.replaceCurrentTabDestination(TabsDestination.BookContent(bookId, tabId, lineId))
                    },
                    isSelected = false,
                    icon = Book,
                    iconDescription = stringResource(Res.string.back_to_text_edition),
                    label = stringResource(Res.string.back_to_text_edition),
                )
            },
            bottomContent = {},
        )
    }

    val importDialogTitle = stringResource(Res.string.pdf_import_dialog_title)
    LaunchedEffect(showPicker) {
        if (!showPicker) return@LaunchedEffect
        val selected =
            FileDialog(null as Frame?, importDialogTitle, FileDialog.LOAD).run {
                isVisible = true
                val selectedFile = file?.let { File(directory, it) }
                dispose()
                selectedFile
            }
        showPicker = false
        if (selected != null) {
            installing = true
            error = null
            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.importArchive(selected) } }
                .onFailure { error = it.message }
            installing = false
            reloadToken++
        }
    }
}

@Composable
private fun PdfHeader(
    title: String?,
    onTextEdition: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Book, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            if (title ==
                null
            ) {
                stringResource(Res.string.pdf_loading)
            } else {
                stringResource(Res.string.pdf_edition_title).format(title)
            },
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onTextEdition) { Text(stringResource(Res.string.back_to_text_edition)) }
    }
}

@Composable
private fun PdfSidePane(
    title: String?,
    file: File?,
    showLibrary: Boolean,
    showToc: Boolean,
) {
    val outline by produceState<List<PdfOutlineEntry>>(emptyList(), file) {
        value = file?.let { withContext(Dispatchers.IO) { readPdfOutline(it) } }.orEmpty()
    }
    Column(
        modifier =
            Modifier
                .width(270.dp)
                .fillMaxHeight()
                .background(JewelTheme.globalColors.toolwindowBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showLibrary) {
            PanelCard(stringResource(Res.string.pdf_printed_view)) {
                Text(
                    text = title ?: stringResource(Res.string.pdf_loading),
                    color = JewelTheme.globalColors.text.normal,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (showToc) {
            PdfOutlinePanel(outline)
        }
        PanelCard(stringResource(Res.string.pdf_commentaries_hint)) {
            Text(stringResource(Res.string.pdf_commentaries_hint), color = JewelTheme.globalColors.text.disabled)
        }
    }
}

@Composable
private fun PdfOutlinePanel(outline: List<PdfOutlineEntry>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.pdf_table_of_contents),
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
        )
        if (outline.isEmpty()) {
            Text(stringResource(Res.string.pdf_no_outline), color = JewelTheme.globalColors.text.disabled)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(outline, key = { entry -> "${entry.level}-${entry.pageIndex}-${entry.title}" }) { entry ->
                    Text(
                        text = "  ".repeat(entry.level) + entry.title,
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.normal)
        content()
    }
}

@Composable
private fun MissingPdfPanel(
    installing: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onImport: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.42f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        JewelTheme.globalColors.borders.disabled
                            .copy(alpha = 0.18f),
                    ).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Book, contentDescription = null, modifier = Modifier.size(42.dp))
            Text(stringResource(Res.string.pdf_missing), color = JewelTheme.globalColors.text.normal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !installing, onClick = onDownload) {
                    Text(stringResource(Res.string.pdf_download_library))
                }
                OutlinedButton(enabled = !installing, onClick = onImport) {
                    Text(stringResource(Res.string.pdf_import_archive))
                }
            }
            if (installing) {
                Text(stringResource(Res.string.pdf_installing), color = JewelTheme.globalColors.text.normal)
            }
            if (error != null) {
                Text(stringResource(Res.string.pdf_install_failed).format(error), color = Color(0xFFB00020))
            }
        }
    }
}

@Composable
private fun PdfPages(
    file: File,
    zoom: Float,
) {
    var pageCount by remember(file) { mutableStateOf<Int?>(null) }
    LaunchedEffect(file) { pageCount = withContext(Dispatchers.IO) { Loader.loadPDF(file).use { it.numberOfPages } } }
    val count = pageCount
    if (count ==
        null
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.pdf_pages_loading)) }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
        ) {
            items((0 until count).toList(), key = { it }) { pageIndex ->
                val page by produceState<RenderedPdfPage?>(null, file, pageIndex) {
                    value =
                        withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
                }
                PdfPageCard(page, zoom)
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    page: RenderedPdfPage?,
    zoom: Float,
) {
    Box(
        Modifier
            .fillMaxWidth(zoom)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (page ==
            null
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.pdf_pages_loading), color = JewelTheme.globalColors.text.disabled)
            }
        } else {
            Image(page.bitmap, null, Modifier.fillMaxWidth().aspectRatio(page.aspectRatio), contentScale = ContentScale.Fit)
        }
    }
}

private const val PDF_ZOOM_MIN = 0.52f
private const val PDF_ZOOM_MAX = 1f
private const val PDF_ZOOM_STEP = 0.08f

private data class RenderedPdfPage(
    val bitmap: ImageBitmap,
    val aspectRatio: Float,
)

private data class PdfOutlineEntry(
    val title: String,
    val level: Int,
    val pageIndex: Int?,
)

private fun readPdfOutline(file: File): List<PdfOutlineEntry> =
    Loader.loadPDF(file).use { document ->
        buildList {
            document.documentCatalog.documentOutline
                ?.firstChild
                ?.let { appendOutline(it, 0) }
        }
    }

private fun MutableList<PdfOutlineEntry>.appendOutline(
    item: PDOutlineItem,
    level: Int,
) {
    var current: PDOutlineItem? = item
    while (current != null) {
        val destination = current.destination ?: (current.action as? PDActionGoTo)?.destination
        val pageIndex = (destination as? PDPageDestination)?.retrievePageNumber()
        add(PdfOutlineEntry(current.title.orEmpty(), level, pageIndex?.takeIf { it >= 0 }))
        current.firstChild?.let { appendOutline(it, level + 1) }
        current = current.nextSibling
    }
}

private fun renderPdfPage(
    file: File,
    pageIndex: Int,
): RenderedPdfPage =
    Loader.loadPDF(file).use { document ->
        val image = PDFRenderer(document).renderImageWithDPI(pageIndex, 160f, ImageType.RGB)
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        RenderedPdfPage(
            SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap(),
            image.width.toFloat() / image.height.toFloat(),
        )
    }
