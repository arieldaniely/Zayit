package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Immutable

@Immutable
data class DisplaySettingsState(
    val showZmanimWidgets: Boolean = true,
    val showHomeWallpaper: Boolean = true,
    val compactMode: Boolean = false,
    val maxCommentatorsPerPage: Int = 0,
    val showContextTargumim: Boolean = true,
    val showContextMentions: Boolean = true,
    val showContextSources: Boolean = true,
    val showContextCommentaries: Boolean = true,
) {
    companion object {
        val preview =
            DisplaySettingsState(
                showZmanimWidgets = true,
                showHomeWallpaper = true,
                compactMode = false,
                maxCommentatorsPerPage = 0,
            )
    }
}
