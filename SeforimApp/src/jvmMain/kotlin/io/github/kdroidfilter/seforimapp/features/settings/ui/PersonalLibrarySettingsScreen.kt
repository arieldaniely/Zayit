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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalFolderPlacement
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalLibraryViewModel
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.personal_library_add_folder
import seforimapp.seforimapp.generated.resources.personal_library_change_to_merge
import seforimapp.seforimapp.generated.resources.personal_library_change_to_personal
import seforimapp.seforimapp.generated.resources.personal_library_description
import seforimapp.seforimapp.generated.resources.personal_library_empty
import seforimapp.seforimapp.generated.resources.personal_library_merge_mode
import seforimapp.seforimapp.generated.resources.personal_library_personal_mode
import seforimapp.seforimapp.generated.resources.personal_library_reindex
import seforimapp.seforimapp.generated.resources.personal_library_remove
import seforimapp.seforimapp.generated.resources.personal_library_stats
import seforimapp.seforimapp.generated.resources.personal_library_success
import seforimapp.seforimapp.generated.resources.personal_library_title
import seforimapp.seforimapp.generated.resources.personal_library_working
import java.io.File

@Composable
fun PersonalLibrarySettingsScreen() {
    val viewModel: PersonalLibraryViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.personal_library_title), fontSize = 18.sp)
            Text(
                stringResource(Res.string.personal_library_description),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(
                    enabled = !state.isWorking,
                    onClick = {
                        scope.launch {
                            val selected = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                            selected?.let {
                                viewModel.addFolder(File(it.path), PersonalFolderPlacement.PERSONAL_BOOKS)
                            }
                        }
                    },
                ) { Text(stringResource(Res.string.personal_library_add_folder)) }
                OutlinedButton(
                    enabled = !state.isWorking && state.configuration.folders.isNotEmpty(),
                    onClick = viewModel::reindex,
                ) { Text(stringResource(Res.string.personal_library_reindex)) }
            }

            if (state.configuration.folders.isEmpty()) {
                Text(stringResource(Res.string.personal_library_empty), color = JewelTheme.globalColors.text.info)
            }
            state.configuration.folders.forEach { folder ->
                val shape = RoundedCornerShape(8.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(shape)
                        .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                        .background(JewelTheme.globalColors.panelBackground).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(folder.displayName, fontSize = 15.sp)
                    Text(
                        folder.path, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp, color = JewelTheme.globalColors.text.info,
                    )
                    Text(
                        stringResource(
                            if (folder.placement == PersonalFolderPlacement.PERSONAL_BOOKS) {
                                Res.string.personal_library_personal_mode
                            } else Res.string.personal_library_merge_mode,
                        ),
                        fontSize = 12.sp,
                    )
                    Text(
                        stringResource(Res.string.personal_library_stats, folder.lastBookCount, folder.lastLinkCount),
                        fontSize = 11.sp, color = JewelTheme.globalColors.text.info,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(enabled = !state.isWorking, onClick = {
                            val next = if (folder.placement == PersonalFolderPlacement.PERSONAL_BOOKS) {
                                PersonalFolderPlacement.MERGE_WITH_LIBRARY
                            } else PersonalFolderPlacement.PERSONAL_BOOKS
                            viewModel.setPlacement(folder.id, next)
                        }) {
                            Text(stringResource(
                                if (folder.placement == PersonalFolderPlacement.PERSONAL_BOOKS) {
                                    Res.string.personal_library_change_to_merge
                                } else Res.string.personal_library_change_to_personal,
                            ))
                        }
                        OutlinedButton(enabled = !state.isWorking, onClick = { viewModel.remove(folder.id) }) {
                            Text(stringResource(Res.string.personal_library_remove))
                        }
                    }
                }
            }
            if (state.isWorking) Text(stringResource(Res.string.personal_library_working))
            if (state.success) InlineSuccessBanner(stringResource(Res.string.personal_library_success), Modifier.fillMaxWidth())
            state.error?.let { InlineErrorBanner(it, Modifier.fillMaxWidth()) }
        }
    }
}
