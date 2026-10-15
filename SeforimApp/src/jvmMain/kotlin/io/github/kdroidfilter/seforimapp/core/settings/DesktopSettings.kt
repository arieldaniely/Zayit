package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/** Keep all fork preferences, including the library path, independent of Zayit. */
fun createDesktopSettings(): Settings =
    PreferencesSettings(Preferences.userRoot().node("io/github/arieldaniely/zayita"))
