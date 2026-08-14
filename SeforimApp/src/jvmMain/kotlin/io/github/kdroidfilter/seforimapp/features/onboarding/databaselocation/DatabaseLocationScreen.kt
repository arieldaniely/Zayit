package io.github.kdroidfilter.seforimapp.features.onboarding.databaselocation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.databaseFileIn
import io.github.kdroidfilter.seforimapp.framework.database.databaseInstallDirectory
import io.github.kdroidfilter.seforimapp.framework.database.selectDatabaseDirectory
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
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.database_location_action_choose_again
import seforimapp.seforimapp.generated.resources.database_location_action_install
import seforimapp.seforimapp.generated.resources.database_location_action_update
import seforimapp.seforimapp.generated.resources.database_location_action_use
import seforimapp.seforimapp.generated.resources.database_location_description
import seforimapp.seforimapp.generated.resources.database_location_missing
import seforimapp.seforimapp.generated.resources.database_location_ready
import seforimapp.seforimapp.generated.resources.database_location_selected
import seforimapp.seforimapp.generated.resources.database_location_title
import seforimapp.seforimapp.generated.resources.database_location_update_required
import java.io.File

private enum class DatabaseLocationStatus {
    READY,
    UPDATE_REQUIRED,
    NOT_FOUND,
}

@Composable
fun DatabaseLocationScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    val scope = rememberCoroutineScope()
    var directory by remember { mutableStateOf(databaseInstallDirectory()) }
    val status = remember(directory) { inspectDirectory(directory) }

    LaunchedEffect(Unit) { progressBarState.setProgress(0.15f) }

    fun continueWithDirectory() {
        selectDatabaseDirectory(directory)
        val destination =
            if (status == DatabaseLocationStatus.READY) {
                OnBoardingDestination.PdfLibrarySetupScreen
            } else {
                OnBoardingDestination.AvailableDiskSpaceScreen
            }
        navController.navigate(destination)
    }

    OnBoardingScaffold(
        title = stringResource(Res.string.database_location_title),
        bottomAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val selected = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                            selected?.let { directory = File(it.path) }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.database_location_action_choose_again))
                }
                DefaultButton(onClick = ::continueWithDirectory) {
                    Text(
                        stringResource(
                            when (status) {
                                DatabaseLocationStatus.READY -> Res.string.database_location_action_use
                                DatabaseLocationStatus.UPDATE_REQUIRED -> Res.string.database_location_action_update
                                DatabaseLocationStatus.NOT_FOUND -> Res.string.database_location_action_install
                            },
                        ),
                    )
                }
            }
        },
    ) {
        Text(
            text = stringResource(Res.string.database_location_description),
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.fillMaxWidth(0.85f),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.database_location_selected, directory.absolutePath))
            when (status) {
                DatabaseLocationStatus.READY ->
                    InlineSuccessBanner(
                        text = stringResource(Res.string.database_location_ready),
                        modifier = Modifier.fillMaxWidth(),
                    )
                DatabaseLocationStatus.UPDATE_REQUIRED ->
                    InlineErrorBanner(
                        text = stringResource(Res.string.database_location_update_required),
                        modifier = Modifier.fillMaxWidth(),
                    )
                DatabaseLocationStatus.NOT_FOUND ->
                    InlineErrorBanner(
                        text = stringResource(Res.string.database_location_missing),
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
    }
}

private fun inspectDirectory(directory: File): DatabaseLocationStatus {
    val databaseFile = databaseFileIn(directory)
    return when {
        !databaseFile.isFile -> DatabaseLocationStatus.NOT_FOUND
        DatabaseVersionManager.isDatabaseVersionCompatible(databaseFile) -> DatabaseLocationStatus.READY
        else -> DatabaseLocationStatus.UPDATE_REQUIRED
    }
}
