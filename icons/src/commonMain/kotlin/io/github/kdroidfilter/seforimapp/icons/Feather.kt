package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Feather: ImageVector by lazy {
    ImageVector
        .Builder("Feather", 24.dp, 24.dp, 24f, 24f)
        .apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(20f, 3f)
                curveTo(12f, 1f, 5f, 8f, 6f, 17f)
                curveTo(15f, 18f, 22f, 11f, 20f, 3f)
                close()
                moveTo(3f, 21f)
                lineTo(17f, 7f)
                moveTo(9f, 15f)
                lineTo(9f, 10f)
                moveTo(12f, 12f)
                lineTo(17f, 12f)
            }
        }.build()
}
