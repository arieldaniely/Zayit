package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(6.5f, 6.5f)
                        lineToRelative(4f, -2.5f)
                        lineToRelative(-4f, -2.5f)
                        verticalLineToRelative(5f)
                        close()
                        moveTo(6.5f, 12f)
                        lineToRelative(4f, 2.5f)
                        lineToRelative(-4f, 2.5f)
                        lineTo(6.5f, 12f)
                        close()
                        moveTo(5.034f, 2.536f)
                        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.034f, 0.214f)
                        verticalLineToRelative(13.5f)
                        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.768f, 0.423f)
                        lineToRelative(6f, -3.75f)
                        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.148f, -0.687f)
                        lineTo(8.986f, 8.5f)
                        lineToRelative(2.932f, -3.736f)
                        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.148f, -0.687f)
                        lineToRelative(-6f, -3.75f)
                        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.736f, 0.21f)
                        close()
                        moveTo(6.5f, 3.38f)
                        lineToRelative(3.197f, 1.998f)
                        lineTo(6.5f, 7.376f)
                        lineTo(6.5f, 3.38f)
                        close()
                        moveTo(6.5f, 9.622f)
                        lineToRelative(3.197f, 1.998f)
                        lineTo(6.5f, 13.618f)
                        lineTo(6.5f, 9.622f)
                        close()
                    }
                }.build()

        return _Bluetooth!!
    }

private var _Bluetooth: ImageVector? = null
