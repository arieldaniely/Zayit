package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.icons.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_import_dialog_title
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_body
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_later
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_skip
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_title
import seforimapp.seforimapp.generated.resources.pdf_installing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun TalmudPdfInstallPrompt(onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    var installing by remember { mutableStateOf(false) }
    var downloadRequested by remember { mutableStateOf(false) }
    var importRequested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = currentOnDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(18.dp))
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Book, contentDescription = null, modifier = Modifier.size(44.dp))
            Text(stringResource(Res.string.pdf_install_prompt_title), color = JewelTheme.globalColors.text.normal)
            Text(stringResource(Res.string.pdf_install_prompt_body), color = JewelTheme.globalColors.text.normal)
            if (installing) {
                Text(stringResource(Res.string.pdf_installing), color = JewelTheme.globalColors.text.normal)
            }
            val installError = error
            if (installError != null) {
                Text(stringResource(Res.string.pdf_install_failed).format(installError), color = Color(0xFFB00020))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !installing, onClick = {
                    installing = true
                    error = null
                    downloadRequested = true
                }) { Text(stringResource(Res.string.pdf_download_library)) }
                OutlinedButton(enabled = !installing, onClick = { importRequested = true }) {
                    Text(stringResource(Res.string.pdf_import_archive))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !installing, onClick = currentOnDone) {
                    Text(stringResource(Res.string.pdf_install_prompt_later))
                }
                OutlinedButton(enabled = !installing, onClick = {
                    AppSettings.setTalmudPdfInstallSkipped(true)
                    currentOnDone()
                }) { Text(stringResource(Res.string.pdf_install_prompt_skip)) }
            }
        }
    }

    if (downloadRequested) {
        LaunchedEffect(Unit) {
            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.downloadAndInstall() } }
                .onFailure { error = it.message }
                .onSuccess { currentOnDone() }
            installing = false
            downloadRequested = false
        }
    }

    val importDialogTitle = stringResource(Res.string.pdf_import_dialog_title)
    LaunchedEffect(importRequested) {
        if (!importRequested) return@LaunchedEffect
        val selected =
            FileDialog(null as Frame?, importDialogTitle, FileDialog.LOAD).run {
                isVisible = true
                val selectedFile = file?.let { File(directory, it) }
                dispose()
                selectedFile
            }
        importRequested = false
        if (selected != null) {
            installing = true
            error = null
            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.importArchive(selected) } }
                .onFailure { error = it.message }
                .onSuccess { currentOnDone() }
            installing = false
        }
    }
}
