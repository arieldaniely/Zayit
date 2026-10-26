package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Immutable

@Immutable
data class DisplaySettingsState(
    val antiAliasing: io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing =
        io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing.OFF,
    val showHomeWallpaper: Boolean = true,
    val compactMode: Boolean = false,
    val maxCommentatorsPerPage: Int = 0,
    val linkLoadLevel: Int = 2,
) {
    companion object {
        val preview =
            DisplaySettingsState(
                showHomeWallpaper = true,
                compactMode = false,
                maxCommentatorsPerPage = 0,
            )
    }
}
