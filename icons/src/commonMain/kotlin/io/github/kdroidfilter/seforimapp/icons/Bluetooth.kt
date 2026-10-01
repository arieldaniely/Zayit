package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Bluetooth: ImageVector
    get() {
        if (_Bluetooth != null) return _Bluetooth!!

        _Bluetooth =
            ImageVector
                .Builder(
                    name = "Bluetooth",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.5f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(5f, 4f)
                        lineTo(11f, 10f)
                        lineTo(8f, 13f)
                        lineTo(8f, 3f)
                        lineTo(11f, 6f)
                        lineTo(5f, 12f)
                    }
                }.build()

        return _Bluetooth!!
    }

private var _Bluetooth: ImageVector? = null
