package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
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
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        stringResource(Res.string.search_mode_exact),
        stringResource(Res.string.search_mode_flexible),
        stringResource(Res.string.search_mode_smart),
    )
    val modes = listOf(SearchMode.EXACT, SearchMode.FLEXIBLE, SearchMode.SMART)
    fun selectMode(index: Int) {
        expanded = false
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
    }
    val shape = RoundedCornerShape(18.dp)
    val background = JewelTheme.globalColors.panelBackground
    val border = JewelTheme.globalColors.borders.disabled
    val textColor = JewelTheme.globalColors.text.normal
    Box {
        Row(
            modifier =
                Modifier
                    .width(76.dp)
                    .height(34.dp)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, border, shape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                options[modes.indexOf(mode)],
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
            Text("⌄", color = textColor, fontSize = 12.sp)
        }
        if (expanded) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset = IntOffset(
                        anchorBounds.left.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                        if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) {
                            anchorBounds.bottom + 4
                        } else {
                            (anchorBounds.top - popupContentSize.height - 4).coerceAtLeast(0)
                        },
                    )
                },
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false },
            ) {
                Column(
                    modifier =
                        Modifier.width(88.dp)
                            .shadow(6.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(background)
                            .border(1.dp, border, RoundedCornerShape(10.dp))
                            .padding(3.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { selectMode(index) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
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
