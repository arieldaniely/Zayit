package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.icons.Book_2
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon

@Composable
fun PdfEditionMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Icon(
        imageVector = Book_2,
        contentDescription = null,
        modifier = modifier.size(16.dp),
        tint =
            if (selected) {
                JewelTheme.globalColors.text.selected
            } else {
                JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
            },
    )
}
