package io.github.kdroidfilter.seforimapp.features.home.widgets

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.Setting
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Widget preferences share the application's injected store and survive a restart. */
@Inject
@SingleIn(AppScope::class)
class WidgetPreferences(
    private val settings: Settings,
) {
    internal val homeWidgetsLayoutFlow get() = flow(LayoutSettings.saved)

    internal fun setHomeWidgetsLayout(value: String?) {
        this[LayoutSettings.saved] = value
    }

    private val states = ConcurrentHashMap<String, MutableStateFlow<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> state(setting: Setting<T>): MutableStateFlow<T> =
        states.getOrPut(setting.key) {
            MutableStateFlow(settings.getStringOrNull(setting.key)?.let(setting.codec.decode) ?: setting.default)
        } as MutableStateFlow<T>

    operator fun <T> get(setting: Setting<T>): T = state(setting).value

    operator fun <T> set(
        setting: Setting<T>,
        value: T,
    ) {
        settings.putString(setting.key, setting.codec.encode(value))
        state(setting).value = value
    }

    fun <T> flow(setting: Setting<T>): StateFlow<T> = state(setting).asStateFlow()

    fun reset(group: SettingsGroup) {
        group.all.forEach { reset(it) }
    }

    private fun <T> reset(setting: Setting<T>) {
        settings.remove(setting.key)
        state(setting).value = setting.default
    }
}
