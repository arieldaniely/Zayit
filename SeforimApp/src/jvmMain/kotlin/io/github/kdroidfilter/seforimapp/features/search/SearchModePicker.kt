package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
fun SearchModePicker(mode: SearchMode, onModeChange: (SearchMode) -> Unit) {
    val scope = rememberCoroutineScope()
    var showInstall by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkVersion by remember { mutableIntStateOf(0) }
    val options = listOf(
        stringResource(Res.string.search_mode_exact),
        stringResource(Res.string.search_mode_flexible),
        stringResource(Res.string.search_mode_smart),
    )
    val modes = listOf(SearchMode.EXACT, SearchMode.FLEXIBLE, SearchMode.SMART)
    ListComboBox(
        items = options,
        selectedIndex = modes.indexOf(mode),
        onSelectedItemChange = { index ->
            val selected = modes[index]
            if (selected == SearchMode.SMART) {
                if (!checking) {
                    checking = true
                    val version = ++checkVersion
                    scope.launch {
                        val ready = withContext(Dispatchers.IO) {
                            runCatching { SemanticAssetsManager.validate() }.isSuccess
                        }
                        if (version != checkVersion) return@launch
                        checking = false
                        if (ready) onModeChange(selected) else showInstall = true
                    }
                }
            } else {
                checkVersion++
                checking = false
                onModeChange(selected)
            }
        },
        modifier = Modifier.width(108.dp),
    )
    if (showInstall) {
        SemanticInstallDialog(
            onDismiss = { showInstall = false },
            onReady = {
                showInstall = false
                onModeChange(SearchMode.SMART)
            },
        )
    }
}

@Composable
private fun SemanticInstallDialog(onDismiss: () -> Unit, onReady: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun install(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { action() }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
                .onSuccess { onReady() }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.width(490.dp).background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.semantic_install_title))
            Text(stringResource(Res.string.semantic_install_body))
            if (busy) Text(stringResource(Res.string.semantic_install_busy))
            error?.let { Text(it, color = Color(0xFFB00020)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !busy, onClick = {
                    install { withContext(Dispatchers.IO) { SemanticAssetsManager.downloadBundle() } }
                }) { Text(stringResource(Res.string.semantic_download_bundle)) }
                OutlinedButton(enabled = !busy, onClick = {
                    val files = chooseFiles()
                    if (files.isNotEmpty()) {
                        install { withContext(Dispatchers.IO) { SemanticAssetsManager.importBundle(files) } }
                    }
                }) { Text(stringResource(Res.string.semantic_import_bundle)) }
            }
            OutlinedButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(Res.string.semantic_install_close))
            }
        }
    }
}

private fun chooseFiles(): List<Path> = JFileChooser().run {
    isMultiSelectionEnabled = true
    if (showOpenDialog(null) == JFileChooser.APPROVE_OPTION) selectedFiles.map { it.toPath() } else emptyList()
}
