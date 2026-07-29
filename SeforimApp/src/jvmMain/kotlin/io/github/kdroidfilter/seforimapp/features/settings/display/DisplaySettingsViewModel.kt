package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DisplaySettingsViewModel : ViewModel() {
    private val showZmanim = MutableStateFlow(AppSettings.isShowZmanimWidgetsEnabled())
    private val showTempleCountdown = MutableStateFlow(AppSettings.isShowTempleCountdownEnabled())
    private val showHomeWallpaper = MutableStateFlow(AppSettings.isShowHomeWallpaperEnabled())
    private val compactMode = MutableStateFlow(AppSettings.isCompactModeEnabled())
    private val maxCommentatorsPerPage = MutableStateFlow(AppSettings.getMaxCommentatorsPerPage())
    private val homeWidgetsVisibility =
        combine(showZmanim, showTempleCountdown) { zmanim, temple -> listOf(zmanim, temple) }

    val state =
        combine(homeWidgetsVisibility, showHomeWallpaper, compactMode, maxCommentatorsPerPage, AppSettings.linkLoadLevelFlow) {
                widgets, wallpaper, compact, maxCommentators, linkLoadLevel ->
            DisplaySettingsState(
                showZmanimWidgets = widgets[0],
                showTempleCountdown = widgets[1],
                showHomeWallpaper = wallpaper,
                compactMode = compact,
                maxCommentatorsPerPage = maxCommentators,
                linkLoadLevel = linkLoadLevel,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DisplaySettingsState(
                showZmanimWidgets = showZmanim.value,
                showTempleCountdown = showTempleCountdown.value,
                showHomeWallpaper = showHomeWallpaper.value,
                compactMode = compactMode.value,
                maxCommentatorsPerPage = maxCommentatorsPerPage.value,
            ),
        )

    fun onEvent(event: DisplaySettingsEvents) {
        when (event) {
            is DisplaySettingsEvents.SetShowZmanimWidgets -> {
                AppSettings.setShowZmanimWidgetsEnabled(event.value)
                showZmanim.value = event.value
            }
            is DisplaySettingsEvents.SetShowTempleCountdown -> {
                AppSettings.setShowTempleCountdownEnabled(event.value)
                showTempleCountdown.value = event.value
            }
            is DisplaySettingsEvents.SetShowHomeWallpaper -> {
                AppSettings.setShowHomeWallpaperEnabled(event.value)
                showHomeWallpaper.value = event.value
            }
            is DisplaySettingsEvents.SetCompactMode -> {
                AppSettings.setCompactModeEnabled(event.value)
                compactMode.value = event.value
            }
            is DisplaySettingsEvents.SetMaxCommentatorsPerPage -> {
                AppSettings.setMaxCommentatorsPerPage(event.value)
                maxCommentatorsPerPage.value = AppSettings.getMaxCommentatorsPerPage()
            }
            is DisplaySettingsEvents.SetLinkLoadLevel -> AppSettings.setLinkLoadLevel(event.value)
        }
    }
}
