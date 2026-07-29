package io.github.kdroidfilter.seforimapp.features.settings.display

sealed interface DisplaySettingsEvents {
    data class SetShowZmanimWidgets(
        val value: Boolean,
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

    data class SetShowContextTargumim(val value: Boolean) : DisplaySettingsEvents
    data class SetShowContextMentions(val value: Boolean) : DisplaySettingsEvents
    data class SetShowContextSources(val value: Boolean) : DisplaySettingsEvents
    data class SetShowContextCommentaries(val value: Boolean) : DisplaySettingsEvents
}
