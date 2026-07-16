package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.back_to_text_edition
import seforimapp.seforimapp.generated.resources.pdf_edition_title
import seforimapp.seforimapp.generated.resources.pdf_loading
import seforimapp.seforimapp.generated.resources.pdf_missing
import seforimapp.seforimapp.generated.resources.pdf_pages_loading
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_import_dialog_title
import seforimapp.seforimapp.generated.resources.pdf_installing
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import java.awt.FileDialog
import java.awt.Frame

@Composable
fun PdfContentScreen(bookId: Long, lineId: Long?, tabId: String) {
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
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = {
                graph.tabsViewModel.replaceCurrentTabDestination(TabsDestination.BookContent(bookId, tabId, lineId))
            }) { Text(stringResource(Res.string.back_to_text_edition)) }
            val currentTitle = title
            Text(
                if (currentTitle == null) {
                    stringResource(Res.string.pdf_loading)
                } else {
                    stringResource(Res.string.pdf_edition_title, currentTitle)
                },
            )
        }
        val file = pdf
        if (file == null) {
            Text(stringResource(Res.string.pdf_missing))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !installing, onClick = {
                    downloadRequested = true
                    installing = true
                    error = null
                }) { Text(stringResource(Res.string.pdf_download_library)) }
                DefaultButton(enabled = !installing, onClick = { showPicker = true }) {
                    Text(stringResource(Res.string.pdf_import_archive))
                }
            }
            if (installing) {
                Text(stringResource(Res.string.pdf_installing))
            }
            if (downloadRequested) {
                LaunchedEffect(Unit) {
                    runCatching { withContext(Dispatchers.IO) { TalmudPdfService.downloadAndInstall() } }
                        .onFailure { error = it.message }
                    downloadRequested = false
                    installing = false
                    reloadToken++
                }
            }
            val installError = error
            if (installError != null) {
                Text(stringResource(Res.string.pdf_install_failed, installError))
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
private fun PdfPages(file: File) {
    var pageCount by remember(file) { mutableStateOf<Int?>(null) }
    LaunchedEffect(file) {
        pageCount = withContext(Dispatchers.IO) { Loader.loadPDF(file).use { it.numberOfPages } }
    }
    val count = pageCount
    if (count == null) {
        Text(stringResource(Res.string.pdf_pages_loading))
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items((0 until count).toList(), key = { it }) { pageIndex ->
                val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file, key2 = pageIndex) {
                    value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex) }
                }
                val rendered = bitmap
                if (rendered == null) {
                    Text(stringResource(Res.string.pdf_pages_loading), modifier = Modifier.padding(24.dp))
                } else {
                    Image(bitmap = rendered, contentDescription = null, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): ImageBitmap =
    Loader.loadPDF(file).use { document ->
        val renderer = PDFRenderer(document)
        renderer.renderImageWithDPI(pageIndex, 130f, ImageType.RGB).let { image ->
            val bytes = ByteArrayOutputStream()
            ImageIO.write(image, "png", bytes)
            SkiaImage.makeFromEncoded(bytes.toByteArray()).toComposeImageBitmap()
        }
    }
