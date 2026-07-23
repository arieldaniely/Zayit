package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val History: ImageVector
    get() {
        if (_History != null) return _History!!

        _History =
            ImageVector
                .Builder(
                    name = "History",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                        pathFillType = PathFillType.EvenOdd,
                    ) {
                        moveTo(8f, 1.5f)
                        curveTo(4.41015f, 1.5f, 1.5f, 4.41015f, 1.5f, 8f)
                        curveTo(1.5f, 11.5899f, 4.41015f, 14.5f, 8f, 14.5f)
                        curveTo(11.5899f, 14.5f, 14.5f, 11.5899f, 14.5f, 8f)
                        curveTo(14.5f, 4.41015f, 11.5899f, 1.5f, 8f, 1.5f)
                        close()
                        moveTo(0.25f, 8f)
                        curveTo(0.25f, 3.71979f, 3.71979f, 0.25f, 8f, 0.25f)
                        curveTo(12.2802f, 0.25f, 15.75f, 3.71979f, 15.75f, 8f)
                        curveTo(15.75f, 12.2802f, 12.2802f, 15.75f, 8f, 15.75f)
                        curveTo(3.71979f, 15.75f, 0.25f, 12.2802f, 0.25f, 8f)
                        close()
                        moveTo(7.375f, 4.25f)
                        curveTo(7.375f, 3.90482f, 7.65482f, 3.625f, 8f, 3.625f)
                        curveTo(8.34518f, 3.625f, 8.625f, 3.90482f, 8.625f, 4.25f)
                        verticalLineTo(7.48223f)
                        lineTo(10.7929f, 9.65013f)
                        curveTo(11.037f, 9.8942f, 11.037f, 10.2899f, 10.7929f, 10.534f)
                        curveTo(10.5488f, 10.7781f, 10.1531f, 10.7781f, 9.90901f, 10.534f)
                        lineTo(7.45806f, 8.08304f)
                        curveTo(7.40465f, 8.02964f, 7.375f, 7.95721f, 7.375f, 7.88171f)
                        verticalLineTo(4.25f)
                        close()
                    }
                }.build()

        return _History!!
    }

private var _History: ImageVector? = null
