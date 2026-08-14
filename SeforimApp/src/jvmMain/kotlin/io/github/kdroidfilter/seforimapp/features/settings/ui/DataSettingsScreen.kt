package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.pdf.TalmudPdfService
import io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsViewModel
import io.github.kdroidfilter.seforimapp.features.settings.data.SelectedDatabaseStatus
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.data_export_action
import seforimapp.seforimapp.generated.resources.data_export_description
import seforimapp.seforimapp.generated.resources.data_export_error
import seforimapp.seforimapp.generated.resources.data_export_success
import seforimapp.seforimapp.generated.resources.data_export_title
import seforimapp.seforimapp.generated.resources.data_exporting
import seforimapp.seforimapp.generated.resources.data_import_action
import seforimapp.seforimapp.generated.resources.data_import_description
import seforimapp.seforimapp.generated.resources.data_import_error
import seforimapp.seforimapp.generated.resources.data_import_success
import seforimapp.seforimapp.generated.resources.data_import_title
import seforimapp.seforimapp.generated.resources.data_importing
import seforimapp.seforimapp.generated.resources.data_reset_description
import seforimapp.seforimapp.generated.resources.database_location_action_choose
import seforimapp.seforimapp.generated.resources.database_location_action_choose_again
import seforimapp.seforimapp.generated.resources.database_location_action_install
import seforimapp.seforimapp.generated.resources.database_location_action_update
import seforimapp.seforimapp.generated.resources.database_location_action_use
import seforimapp.seforimapp.generated.resources.database_location_current
import seforimapp.seforimapp.generated.resources.database_location_description
import seforimapp.seforimapp.generated.resources.database_location_invalid
import seforimapp.seforimapp.generated.resources.database_location_missing
import seforimapp.seforimapp.generated.resources.database_location_ready
import seforimapp.seforimapp.generated.resources.database_location_selected
import seforimapp.seforimapp.generated.resources.database_location_title
import seforimapp.seforimapp.generated.resources.database_location_update_required
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import seforimapp.seforimapp.generated.resources.pdf_install_success
import seforimapp.seforimapp.generated.resources.pdf_installing
import seforimapp.seforimapp.generated.resources.pdf_remove_failed
import seforimapp.seforimapp.generated.resources.pdf_remove_library
import seforimapp.seforimapp.generated.resources.pdf_remove_success
import seforimapp.seforimapp.generated.resources.pdf_removing_library
import seforimapp.seforimapp.generated.resources.settings_cancel
import seforimapp.seforimapp.generated.resources.settings_pdf_library_description
import seforimapp.seforimapp.generated.resources.settings_pdf_library_installed_description
import seforimapp.seforimapp.generated.resources.settings_pdf_library_title
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_reset_warning
import java.io.File

@Composable
fun DataSettingsScreen() {
    val viewModel: DataSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DatabaseLocationCard(
                state = state,
                onChoose = {
                    scope.launch {
                        val directory = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                        directory?.let { viewModel.inspectBooksDatabaseDirectory(File(it.path)) }
                    }
                },
                onApply = viewModel::useSelectedBooksDatabaseDirectory,
                onClearSelection = viewModel::clearSelectedBooksDatabaseDirectory,
            )

            DataActionCard(
                title = Res.string.data_export_title,
                description = Res.string.data_export_description,
                actionLabel = if (state.isExporting) Res.string.data_exporting else Res.string.data_export_action,
                enabled = !state.isExporting && !state.isImporting,
                onClick = {
                    scope.launch {
                        // Run the picker off the GTK event-loop thread: under the Tao backend
                        // Dispatchers.Main is that single thread, and the picker's blocking D-Bus
                        // (xdg-desktop-portal) work would freeze it. See pickDatabaseParts.
                        val directory =
                            withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                        directory?.let { viewModel.exportToFile(File(it.path)) }
                    }
                },
            )

            DataActionCard(
                title = Res.string.data_import_title,
                description = Res.string.data_import_description,
                actionLabel = if (state.isImporting) Res.string.data_importing else Res.string.data_import_action,
                enabled = !state.isExporting && !state.isImporting,
                onClick = {
                    scope.launch {
                        // Same rationale as the export picker: keep the Tao GTK event loop free.
                        val file =
                            withContext(Dispatchers.IO) {
                                FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("db")))
                            }
                        file?.let { viewModel.importFromFile(File(it.path)) }
                    }
                },
            )

            PdfLibrarySettingsCard()

            state.exportedFileName?.let {
                InlineSuccessBanner(
                    text = stringResource(Res.string.data_export_success, it),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.importSucceeded) {
                InlineSuccessBanner(
                    text = stringResource(Res.string.data_import_success),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.exportFailed) {
                InlineErrorBanner(
                    text = stringResource(Res.string.data_export_error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.importFailed) {
                InlineErrorBanner(
                    text = stringResource(Res.string.data_import_error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ResetCard(
                resetDone = state.resetDone,
                onReset = { viewModel.resetApp() },
            )
        }
    }
}

@Composable
private fun DatabaseLocationCard(
    state: DataSettingsState,
    onChoose: () -> Unit,
    onApply: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.database_location_title), fontSize = 15.sp)
        Text(
            text = stringResource(Res.string.database_location_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        Text(
            text = stringResource(Res.string.database_location_current, state.databaseDirectory),
            fontSize = 12.sp,
        )
        state.selectedDatabaseDirectory?.let { selectedDirectory ->
            Text(
                text = stringResource(Res.string.database_location_selected, selectedDirectory),
                fontSize = 12.sp,
            )
        }

        state.selectedDatabaseStatus?.let { status ->
            val message =
                when (status) {
                    SelectedDatabaseStatus.READY -> Res.string.database_location_ready
                    SelectedDatabaseStatus.UPDATE_REQUIRED -> Res.string.database_location_update_required
                    SelectedDatabaseStatus.NOT_FOUND -> Res.string.database_location_missing
                    SelectedDatabaseStatus.INVALID_DIRECTORY -> Res.string.database_location_invalid
                }
            if (status == SelectedDatabaseStatus.READY) {
                InlineSuccessBanner(text = stringResource(message), modifier = Modifier.fillMaxWidth())
            } else {
                InlineErrorBanner(text = stringResource(message), modifier = Modifier.fillMaxWidth())
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onChoose) {
                Text(
                    text =
                        stringResource(
                            if (state.selectedDatabaseStatus == null) {
                                Res.string.database_location_action_choose
                            } else {
                                Res.string.database_location_action_choose_again
                            },
                        ),
                )
            }
            state.selectedDatabaseStatus?.let { status ->
                if (status != SelectedDatabaseStatus.INVALID_DIRECTORY) {
                    DefaultButton(onClick = onApply) {
                        Text(
                            text =
                                stringResource(
                                    when (status) {
                                        SelectedDatabaseStatus.READY -> Res.string.database_location_action_use
                                        SelectedDatabaseStatus.UPDATE_REQUIRED -> Res.string.database_location_action_update
                                        SelectedDatabaseStatus.NOT_FOUND -> Res.string.database_location_action_install
                                        SelectedDatabaseStatus.INVALID_DIRECTORY ->
                                            Res.string.database_location_action_choose_again
                                    },
                                ),
                        )
                    }
                    OutlinedButton(onClick = onClearSelection) {
                        Text(text = stringResource(Res.string.settings_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun DataActionCard(
    title: StringResource,
    description: StringResource,
    actionLabel: StringResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(title), fontSize = 15.sp)
            Text(
                text = stringResource(description),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
            )
        }
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(text = stringResource(actionLabel))
        }
    }
}

@Composable
private fun PdfLibrarySettingsCard() {
    val scope = rememberCoroutineScope()
    val libraryVersion by TalmudPdfService.libraryVersion.collectAsState()
    val installed = remember(libraryVersion) { TalmudPdfService.isInstalled() }
    var working by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<StringResource?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf(Res.string.pdf_install_failed) }
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(Res.string.settings_pdf_library_title), fontSize = 15.sp)
                Text(
                    text =
                        stringResource(
                            if (installed) {
                                Res.string.settings_pdf_library_installed_description
                            } else {
                                Res.string.settings_pdf_library_description
                            },
                        ),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installed) {
                    OutlinedButton(enabled = !working, onClick = {
                        working = true
                        successMessage = null
                        error = null
                        errorMessage = Res.string.pdf_remove_failed
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.removeInstalledLibrary() } }
                                .onSuccess { successMessage = Res.string.pdf_remove_success }
                                .onFailure { error = it.message }
                            working = false
                        }
                    }) {
                        Text(stringResource(if (working) Res.string.pdf_removing_library else Res.string.pdf_remove_library))
                    }
                } else {
                    OutlinedButton(enabled = !working, onClick = {
                        working = true
                        successMessage = null
                        error = null
                        errorMessage = Res.string.pdf_install_failed
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.downloadAndInstall() } }
                                .onSuccess { successMessage = Res.string.pdf_install_success }
                                .onFailure { error = it.message }
                            working = false
                        }
                    }) { Text(stringResource(if (working) Res.string.pdf_installing else Res.string.pdf_download_library)) }
                    OutlinedButton(enabled = !working, onClick = {
                        scope.launch {
                            val file =
                                withContext(Dispatchers.IO) {
                                    FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("zst", "tar.zst")))
                                }
                            if (file != null) {
                                working = true
                                successMessage = null
                                error = null
                                errorMessage = Res.string.pdf_install_failed
                                runCatching { withContext(Dispatchers.IO) { TalmudPdfService.importArchive(File(file.path)) } }
                                    .onSuccess { successMessage = Res.string.pdf_install_success }
                                    .onFailure { error = it.message }
                                working = false
                            }
                        }
                    }) { Text(stringResource(Res.string.pdf_import_archive)) }
                }
            }
        }
        successMessage?.let { InlineSuccessBanner(text = stringResource(it), modifier = Modifier.fillMaxWidth()) }
        error?.let {
            InlineErrorBanner(text = stringResource(errorMessage).format(it), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResetCard(
    resetDone: Boolean,
    onReset: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var showConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = stringResource(Res.string.settings_reset_app), fontSize = 15.sp)
                Text(
                    text = stringResource(Res.string.data_reset_description),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            if (!showConfirmation) {
                OutlinedButton(onClick = { showConfirmation = true }) {
                    Text(text = stringResource(Res.string.settings_reset_app))
                }
            }
        }

        if (showConfirmation) {
            InlineErrorBanner(
                text = stringResource(Res.string.settings_reset_warning),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DefaultButton(
                    onClick = {
                        showConfirmation = false
                        onReset()
                    },
                ) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_yes))
                }
                OutlinedButton(onClick = { showConfirmation = false }) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_no))
                }
            }
        }

        if (resetDone) {
            InlineSuccessBanner(
                text = stringResource(Res.string.settings_reset_done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
