package io.github.kdroidfilter.seforimapp.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Wifi: ImageVector
    get() {
        if (_Wifi != null) return _Wifi!!

        _Wifi =
            ImageVector
                .Builder(
                    name = "Wifi",
                    defaultWidth = 16.dp,
                    defaultHeight = 16.dp,
                    viewportWidth = 16f,
                    viewportHeight = 16f,
                ).apply {
                    path(
                        fill = SolidColor(Color.Black),
                    ) {
                        moveTo(15.384f, 6.115f)
                        arcToRelative(0.485f, 0.485f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.047f, -0.736f)
                        arcTo(12.444f, 12.444f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8f, 3f)
                        arcToRelative(12.44f, 12.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -7.337f, 2.38f)
                        arcToRelative(0.485f, 0.485f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.048f, 0.736f)
                        arcToRelative(0.518f, 0.518f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.736f, 0.047f)
                        arcTo(11.46f, 11.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.648f, 6.162f)
                        curveToRelative(0.234f, 0.18f, 0.57f, 0.16f, 0.736f, -0.048f)
                        close()
                        moveTo(13.23f, 8.27f)
                        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.048f, -0.738f)
                        arcToRelative(9.463f, 9.463f, 0f, isMoreThanHalf = false, isPositiveArc = false, -10.364f, 0f)
                        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.049f, 0.738f)
                        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.738f, 0.048f)
                        arcToRelative(8.468f, 8.468f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.986f, 0f)
                        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.737f, -0.048f)
                        close()
                        moveTo(11.075f, 10.425f)
                        arcToRelative(0.493f, 0.493f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, -0.74f)
                        arcToRelative(6.476f, 6.476f, 0f, isMoreThanHalf = false, isPositiveArc = false, -6.05f, 0f)
                        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.049f, 0.74f)
                        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.74f, 0.048f)
                        arcToRelative(5.478f, 5.478f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.67f, 0f)
                        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.74f, -0.048f)
                        close()
                        moveTo(9.5f, 13f)
                        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3f, 0f)
                        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 0f)
                        close()
                    }
                }.build()

        return _Wifi!!
    }

private var _Wifi: ImageVector? = null
