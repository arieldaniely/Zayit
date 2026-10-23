package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.NucleusApplicationScope
import io.github.kdroidfilter.seforimapp.core.presentation.components.InstallerWindow
import io.github.kdroidfilter.seforimapp.icons.Install_desktop
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.retry_button
import seforimapp.seforimapp.generated.resources.startup_loading
import seforimapp.seforimapp.generated.resources.startup_preparation_failed

@Composable
internal fun NucleusApplicationScope.StartupWindow(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    InstallerWindow(
        titleBarIcon = Install_desktop,
        titleBarText = stringResource(Res.string.app_name),
        progress = remember { MutableStateFlow(0f) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(stringResource(Res.string.startup_preparation_failed))
                DefaultButton(onClick = onRetry) { Text(stringResource(Res.string.retry_button)) }
            } else {
                CircularProgressIndicator()
                Text(stringResource(Res.string.startup_loading))
            }
        }
    }
}
