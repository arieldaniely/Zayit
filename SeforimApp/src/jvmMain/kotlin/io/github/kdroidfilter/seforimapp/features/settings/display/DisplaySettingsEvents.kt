package io.github.kdroidfilter.seforimapp.features.settings.display

sealed interface DisplaySettingsEvents {
    data class SetAntiAliasing(
        val value: io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing,
    ) : DisplaySettingsEvents

    data class SetShowHomeWallpaper(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetCompactMode(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetMaxCommentatorsPerPage(
        val value: Int,
    ) : DisplaySettingsEvents

    data class SetLinkLoadLevel(
        val value: Int,
    ) : DisplaySettingsEvents
}
