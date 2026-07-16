package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Icon
import io.github.kdroidfilter.seforimapp.icons.Book
import org.jetbrains.skia.Image as SkiaImage
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.back_to_text_edition
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_edition_title
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_import_dialog_title
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import seforimapp.seforimapp.generated.resources.pdf_installing
import seforimapp.seforimapp.generated.resources.pdf_loading
import seforimapp.seforimapp.generated.resources.pdf_missing
import seforimapp.seforimapp.generated.resources.pdf_pages_loading
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
fun PdfContentScreen(
    bookId: Long,
    lineId: Long?,
    tabId: String,
) {
    val graph = LocalAppGraph.current
    var title by remember(bookId) { mutableStateOf<String?>(null) }
    var pdf by remember(bookId) { mutableStateOf<File?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var downloadRequested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, reloadToken) {
        val book = withContext(Dispatchers.IO) { graph.repository.getBookCore(bookId) }
        title = book?.title
        pdf = book?.title?.let { TalmudPdfService.pdfForTitle(it) }
        if (book != null) {
            graph.tabTitleUpdateManager.updateTabTitle(tabId, book.title, TabType.BOOK)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    ) {
        PdfHeader(
            title = title,
            onBackToText = {
                graph.tabsViewModel.replaceCurrentTabDestination(TabsDestination.BookContent(bookId, tabId, lineId))
            },
        )

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
            PdfPages(file)
        }
    }

    val importDialogTitle = stringResource(Res.string.pdf_import_dialog_title)
    LaunchedEffect(showPicker) {
        if (!showPicker) return@LaunchedEffect
        val selected = FileDialog(null as Frame?, importDialogTitle, FileDialog.LOAD).run {
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
    onBackToText: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DefaultButton(onClick = onBackToText) {
            Text(stringResource(Res.string.back_to_text_edition))
        }
        Icon(Book, contentDescription = null, modifier = Modifier.size(18.dp))
        val currentTitle = title
        Text(
            text = if (currentTitle == null) {
                stringResource(Res.string.pdf_loading)
            } else {
                stringResource(Res.string.pdf_edition_title).format(currentTitle)
            },
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal,
        )
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
                    .background(JewelTheme.globalColors.borders.disabled.copy(alpha = 0.18f))
                    .padding(24.dp),
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
private fun PdfPages(file: File) {
    var pageCount by remember(file) { mutableStateOf<Int?>(null) }
    LaunchedEffect(file) {
        pageCount = withContext(Dispatchers.IO) { Loader.loadPDF(file).use { it.numberOfPages } }
    }
    val count = pageCount
    if (count == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.pdf_pages_loading))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items((0 until count).toList(), key = { it }) { pageIndex ->
                val page by produceState<RenderedPdfPage?>(initialValue = null, key1 = file, key2 = pageIndex) {
                    value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
                }
                PdfPageCard(page = page)
            }
        }
    }
}

@Composable
private fun PdfPageCard(page: RenderedPdfPage?) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (page == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.pdf_pages_loading), color = JewelTheme.globalColors.text.disabled)
            }
        } else {
            Image(
                bitmap = page.bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(page.aspectRatio),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private data class RenderedPdfPage(
    val bitmap: ImageBitmap,
    val aspectRatio: Float,
)

private fun renderPdfPage(file: File, pageIndex: Int): RenderedPdfPage =
    Loader.loadPDF(file).use { document ->
        val renderer = PDFRenderer(document)
        val image = renderer.renderImageWithDPI(pageIndex, 160f, ImageType.RGB)
        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "png", bytes)
        RenderedPdfPage(
            bitmap = SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap(),
            aspectRatio = image.width.toFloat() / image.height.toFloat(),
        )
    }
