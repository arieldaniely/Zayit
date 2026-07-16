package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PdfEditionMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val color =
        if (selected) {
            JewelTheme.globalColors.text.selected
        } else {
            Color(0xFFB3261E)
        }
    Text(
        text = "PDF",
        modifier =
            modifier
                .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}
