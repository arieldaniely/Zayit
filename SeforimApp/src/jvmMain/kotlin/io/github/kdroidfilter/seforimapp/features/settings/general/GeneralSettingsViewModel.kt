package io.github.kdroidfilter.seforimapp.features.settings.general

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
class GeneralSettingsViewModel : ViewModel() {
    private val dbPath = MutableStateFlow(AppSettings.getDatabasePath())
    private val closeTree = MutableStateFlow(AppSettings.getCloseBookTreeOnNewBookSelected())
    private val persist = MutableStateFlow(AppSettings.isPersistSessionEnabled())
    private val keepAwake = MutableStateFlow(AppSettings.isKeepScreenAwakeOnBookEnabled())

    private val privacy =
        combine(AppSettings.searchFeedbackEnabledFlow, AppSettings.autoUpdateCheckFlow) { feedback, auto ->
            feedback to
                auto
        }

    val state =
        combine(dbPath, closeTree, persist, keepAwake, privacy) { path, c, p, k, options ->
            GeneralSettingsState(
                databasePath = path,
                closeTreeOnNewBook = c,
                persistSession = p,
                keepScreenAwakeOnBook = k,
                searchFeedbackEnabled = options.first,
                autoUpdateCheck = options.second,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GeneralSettingsState(
                databasePath = dbPath.value,
                closeTreeOnNewBook = closeTree.value,
                persistSession = persist.value,
                keepScreenAwakeOnBook = keepAwake.value,
                searchFeedbackEnabled = AppSettings.isSearchFeedbackEnabled(),
                autoUpdateCheck = AppSettings.isAutoUpdateCheckEnabled(),
            ),
        )

    fun onEvent(event: GeneralSettingsEvents) {
        when (event) {
            is GeneralSettingsEvents.SetSearchFeedbackEnabled -> AppSettings.setSearchFeedbackEnabled(event.value)
            is GeneralSettingsEvents.SetCloseTreeOnNewBook -> {
                AppSettings.setCloseBookTreeOnNewBookSelected(event.value)
                closeTree.value = event.value
            }
            is GeneralSettingsEvents.SetPersistSession -> {
                AppSettings.setPersistSessionEnabled(event.value)
                persist.value = event.value
            }
            is GeneralSettingsEvents.SetKeepScreenAwakeOnBook -> {
                AppSettings.setKeepScreenAwakeOnBookEnabled(event.value)
                keepAwake.value = event.value
            }
            is GeneralSettingsEvents.SetAutoUpdateCheck -> AppSettings.setAutoUpdateCheckEnabled(event.value)
        }
    }
}
