@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.kdroidfilter.seforimapp.icons.Manage_search
import io.github.kdroidfilter.seforimapp.icons.MaterialSymbolsMagicButton
import io.github.kdroidfilter.seforimapp.icons.Target
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import seforimapp.seforimapp.generated.resources.*
import java.nio.file.Path

@Composable
fun SearchModePicker(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInstall by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val availability by SemanticAssetsManager.availability.collectAsState()
    val options =
        listOf(
            stringResource(Res.string.search_mode_exact),
            stringResource(Res.string.search_mode_flexible),
            stringResource(Res.string.search_mode_smart),
        )
    val descriptions =
        listOf(
            stringResource(Res.string.search_mode_exact_description),
            stringResource(Res.string.search_mode_flexible_description),
            stringResource(Res.string.search_mode_smart_description),
        )
    val modes = listOf(SearchMode.EXACT, SearchMode.FLEXIBLE, SearchMode.SMART)
    val selectedIndex = modes.indexOf(mode)
    val smartActive = mode == SearchMode.SMART && availability == SemanticAssetsManager.Availability.READY
    val accent = JewelTheme.globalColors.outlines.focused
    val background = JewelTheme.globalColors.panelBackground
    val border = JewelTheme.globalColors.borders.normal
    val textColor = JewelTheme.globalColors.text.normal
    val shape = RoundedCornerShape(18.dp)

    fun selectMode(index: Int) {
        if (checking && modes[index] == SearchMode.SMART) return
        val revision = ++selectionRevision
        if (modes[index] != SearchMode.SMART) {
            onModeChange(modes[index])
        } else {
            checking = true
            scope.launch {
                try {
                    val ready = withContext(Dispatchers.IO) { SemanticAssetsManager.validatedReady() }
                    if (revision != selectionRevision) return@launch
                    if (ready) {
                        onModeChange(SearchMode.SMART)
                    } else {
                        onModeChange(SearchMode.FLEXIBLE)
                        expanded = false
                        showInstall = true
                    }
                } finally {
                    checking = false
                }
            }
        }
    }

    Box(modifier = modifier.width(36.dp), contentAlignment = Alignment.Center) {
        Tooltip(tooltip = {
            Text(if (mode == SearchMode.SMART && !smartActive) stringResource(Res.string.semantic_validating) else options[selectedIndex])
        }) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp, 28.dp)
                        .clip(shape)
                        .background(background)
                        .border(1.dp, border, shape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.search_mode_choose)) {
                            expanded = !expanded
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (smartActive) SmartModeGlow(accent, Modifier.fillMaxSize())
                Icon(
                    imageVector =
                        when (mode) {
                            SearchMode.EXACT -> Target
                            SearchMode.FLEXIBLE -> Manage_search
                            SearchMode.SMART -> MaterialSymbolsMagicButton
                        },
                    contentDescription = options[selectedIndex],
                    tint = if (smartActive) Color.White else textColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded) {
            Popup(
                popupPositionProvider =
                    remember {
                        object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize,
                            ): IntOffset =
                                IntOffset(
                                    (anchorBounds.right - popupContentSize.width).coerceIn(
                                        0,
                                        (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                                    ),
                                    (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(0),
                                )
                        }
                    },
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false },
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(272.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(background)
                            .border(1.dp, border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(options[selectedIndex], color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(descriptions[selectedIndex], color = textColor, fontSize = 12.sp)
                    val validating = checking || availability == SemanticAssetsManager.Availability.VALIDATING
                    if (validating) Text(stringResource(Res.string.semantic_validating), fontSize = 12.sp)
                    if (availability == SemanticAssetsManager.Availability.INVALID) {
                        Text(stringResource(Res.string.semantic_invalid_bundle), fontSize = 12.sp)
                    }
                    val position by animateFloatAsState(selectedIndex.toFloat(), tween(220))
                    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(border.copy(alpha = 0.35f)),
                    ) {
                        if (smartActive) SmartModeGlow(accent, Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            val step = size.width / 3f
                            val center = step * (if (rtl) 2f - position else position) + step / 2f
                            if (mode != SearchMode.SMART) {
                                val edge = if (rtl) size.width else 0f
                                drawRoundRect(
                                    accent,
                                    topLeft = Offset(minOf(edge, center), 0f),
                                    size =
                                        androidx.compose.ui.geometry
                                            .Size(kotlin.math.abs(edge - center), size.height),
                                    cornerRadius = CornerRadius(size.height / 2),
                                )
                            }
                            repeat(3) { index ->
                                drawCircle(textColor.copy(alpha = 0.25f), 2.dp.toPx(), Offset(step * (index + 0.5f), size.height / 2))
                            }
                            drawCircle(Color.Black.copy(alpha = 0.12f), 15.dp.toPx(), Offset(center, size.height / 2 + 1.dp.toPx()))
                            drawCircle(Color.White, 14.dp.toPx(), Offset(center, size.height / 2))
                        }
                        Row(Modifier.fillMaxSize().selectableGroup()) {
                            modes.forEachIndexed { index, _ ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .selectable(
                                            selected = index == selectedIndex,
                                            enabled = index != 2 || !validating,
                                            role = Role.RadioButton,
                                            onClick = { selectMode(index) },
                                        ).semantics { contentDescription = options[index] }
                                        .pointerHoverIcon(PointerIcon.Hand),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, label ->
                            Text(
                                label,
                                color = if (index == selectedIndex) accent else textColor,
                                fontSize = 12.sp,
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .selectable(
                                            selected = index == selectedIndex,
                                            enabled = index != 2 || !validating,
                                            role = Role.RadioButton,
                                            onClick = { selectMode(index) },
                                        ).padding(4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
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

/** All colors follow the user's interface accent; only the active smart state animates. */
@Composable
private fun SmartModeGlow(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
    )
    Canvas(modifier) {
        val light = lerp(accent, Color.White, 0.35f)
        val dark = lerp(accent, Color.Black, 0.2f)
        val shift = kotlin.math.sin(phase * 2f * kotlin.math.PI).toFloat() * size.width * 0.3f
        drawRect(
            Brush.linearGradient(
                listOf(dark, accent, light, accent),
                start = Offset(shift - size.width / 2f, 0f),
                end = Offset(shift + size.width, size.height),
            ),
        )
        repeat(12) { index ->
            val x = ((index * 0.173f + phase) % 1f) * size.width
            val y = (0.2f + (index * 0.237f % 0.6f)) * size.height
            val alpha = 0.25f + 0.4f * kotlin.math.abs(kotlin.math.sin((phase + index * 0.13f) * kotlin.math.PI)).toFloat()
            drawCircle(Color.White.copy(alpha = alpha), (if (index % 3 == 0) 1.4f else 0.8f).dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun SemanticInstallDialog(
    onDismiss: () -> Unit,
    onReady: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadPart by remember { mutableIntStateOf(0) }
    var downloadTotal by remember { mutableIntStateOf(0) }
    val missingPartsMessage = stringResource(Res.string.semantic_import_missing_parts)
    val invalidManifestMessage = stringResource(Res.string.semantic_import_invalid_manifest)
    val invalidSelectionMessage = stringResource(Res.string.semantic_import_invalid_selection)
    val damagedArchiveMessage = stringResource(Res.string.semantic_import_damaged_archive)
    val genericErrorMessage = stringResource(Res.string.semantic_install_error)
    val downloadErrorMessage = stringResource(Res.string.semantic_download_error)
    val incompatibleMessage = stringResource(Res.string.semantic_no_compatible_bundle)
    val invalidBundleMessage = stringResource(Res.string.semantic_invalid_bundle)

    fun install(action: suspend () -> Boolean) {
        busy = true
        error = null
        downloadPart = 0
        scope.launch {
            runCatching { action() }
                .onFailure {
                    if (it is CancellationException) throw it
                    warnln(it) { "Semantic installation failed" }
                    error =
                        if (it is SemanticBundleImportException) {
                            when (it.problem) {
                                SemanticBundleImportProblem.MISSING_PARTS ->
                                    missingPartsMessage.format(it.missingParts.joinToString(", "))
                                SemanticBundleImportProblem.INVALID_MANIFEST -> invalidManifestMessage
                                SemanticBundleImportProblem.INVALID_SELECTION -> invalidSelectionMessage
                                SemanticBundleImportProblem.DAMAGED_ARCHIVE -> damagedArchiveMessage
                                SemanticBundleImportProblem.DOWNLOAD_FAILED -> downloadErrorMessage
                                SemanticBundleImportProblem.NO_COMPATIBLE_BUNDLE -> incompatibleMessage
                                SemanticBundleImportProblem.INVALID_BUNDLE -> invalidBundleMessage
                            }
                        } else {
                            genericErrorMessage
                        }
                }.onSuccess { installed -> if (installed) onReady() }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .width(490.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.semantic_install_title))
            Text(stringResource(Res.string.semantic_install_body))
            if (busy) {
                Text(
                    if (downloadPart > 0) {
                        stringResource(Res.string.semantic_download_progress, downloadPart, downloadTotal)
                    } else {
                        stringResource(Res.string.semantic_install_busy)
                    },
                )
            }
            error?.let {
                Text(it, color = Color(0xFFB00020), modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !busy, onClick = {
                    install {
                        withContext(Dispatchers.IO) {
                            SemanticAssetsManager.downloadBundle { part, total ->
                                scope.launch {
                                    downloadPart = part
                                    downloadTotal = total
                                }
                            }
                            if (!SemanticAssetsManager.validatedReady()) {
                                throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_BUNDLE)
                            }
                        }
                        true
                    }
                }) { Text(stringResource(Res.string.semantic_download_bundle)) }
                OutlinedButton(enabled = !busy, onClick = {
                    install {
                        // Native pickers can block; keep the Compose event loop free while they are open.
                        val files = withContext(Dispatchers.IO) { chooseFiles() }
                        if (files.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                SemanticAssetsManager.importBundle(files)
                                if (!SemanticAssetsManager.validatedReady()) {
                                    throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_BUNDLE)
                                }
                            }
                        }
                        files.isNotEmpty()
                    }
                }) { Text(stringResource(Res.string.semantic_import_bundle)) }
            }
            OutlinedButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(Res.string.semantic_install_close))
            }
        }
    }
}

private suspend fun chooseFiles(): List<Path> =
    FileKit
        .openFilePicker(mode = FileKitMode.Multiple())
        ?.map { Path.of(it.path) }
        .orEmpty()
