package io.github.kdroidfilter.seforimapp.features.onboarding.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.installation_choose_root

@Composable
fun ChooseInstallationLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.widthIn(min = 240.dp)) {
        Text(stringResource(Res.string.installation_choose_root))
    }
}
