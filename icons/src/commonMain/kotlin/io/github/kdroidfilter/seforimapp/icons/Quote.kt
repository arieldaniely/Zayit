package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Quote: ImageVector
    get() {
        if (_Quote != null) return _Quote!!
        _Quote =
            ImageVector
                .Builder(
                    name = "Quote",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(6f, 17f)
                    horizontalLineTo(10f)
                    lineTo(13f, 11f)
                    verticalLineTo(5f)
                    horizontalLineTo(5f)
                    verticalLineTo(13f)
                    horizontalLineTo(9f)
                    close()
                    moveTo(15f, 17f)
                    horizontalLineTo(19f)
                    lineTo(22f, 11f)
                    verticalLineTo(5f)
                    horizontalLineTo(14f)
                    verticalLineTo(13f)
                    horizontalLineTo(18f)
                    close()
                }
            }.build()
        return _Quote!!
    }

private var _Quote: ImageVector? = null
