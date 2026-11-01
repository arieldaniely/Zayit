package io.github.kdroidfilter.seforimapp.features.onboarding.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.installation_choose_root

@Composable
fun ChooseInstallationLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrowColor = JewelTheme.globalColors.text.normal
    OutlinedButton(onClick = onClick, modifier = modifier.widthIn(min = 240.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(Res.string.installation_choose_root))
            Canvas(Modifier.width(96.dp).height(12.dp)) {
                val strokeWidth = 1.dp.toPx()
                val tip = Offset(size.width / 2, size.height - strokeWidth)
                drawLine(arrowColor, Offset(strokeWidth, strokeWidth), tip, strokeWidth, StrokeCap.Round)
                drawLine(arrowColor, tip, Offset(size.width - strokeWidth, strokeWidth), strokeWidth, StrokeCap.Round)
            }
        }
    }
}
