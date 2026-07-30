package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import java.io.File
import java.util.Properties

/** Portable, non-personal visual preferences used by screenshot recording and replay. */
object ScreenshotVisualSettings {
    fun exportTo(destination: File) {
        val values = Properties().apply {
            setProperty("textSize", AppSettings.getTextSize().toString())
            setProperty("lineHeight", AppSettings.getLineHeight().toString())
            setProperty("maxCommentatorsPerPage", AppSettings.getMaxCommentatorsPerPage().toString())
            setProperty("closeTreeOnNewBook", AppSettings.getCloseBookTreeOnNewBookSelected().toString())
            setProperty("bookFont", AppSettings.getBookFontCode())
            setProperty("commentaryFont", AppSettings.getCommentaryFontCode())
            setProperty("targumFont", AppSettings.getTargumFontCode())
            setProperty("sourceFont", AppSettings.getSourceFontCode())
            setProperty("linkLoadLevel", AppSettings.getLinkLoadLevel().toString())
            setProperty("showZmanimWidgets", AppSettings.isShowZmanimWidgetsEnabled().toString())
            setProperty("showTempleCountdown", AppSettings.isShowTempleCountdownEnabled().toString())
            setProperty("showHomeWallpaper", AppSettings.isShowHomeWallpaperEnabled().toString())
            setProperty("compactMode", AppSettings.isCompactModeEnabled().toString())
            setProperty("themeStyle", AppSettings.getThemeStyle().name)
            setProperty("accentColor", AppSettings.getAccentColor().name)
        }
        destination.parentFile?.mkdirs()
        destination.outputStream().use { values.store(it, "Zayit screenshot visual settings (no personal data)") }
    }

    fun importFrom(source: File, appGraph: AppGraph) {
        val values = Properties().apply { source.inputStream().use(::load) }
        fun value(name: String) = requireNotNull(values.getProperty(name)) { "Missing visual setting: $name" }
        AppSettings.setTextSize(value("textSize").toFloat())
        AppSettings.setLineHeight(value("lineHeight").toFloat())
        AppSettings.setMaxCommentatorsPerPage(value("maxCommentatorsPerPage").toInt())
        AppSettings.setCloseBookTreeOnNewBookSelected(value("closeTreeOnNewBook").toBooleanStrict())
        AppSettings.setBookFontCode(value("bookFont"))
        AppSettings.setCommentaryFontCode(value("commentaryFont"))
        AppSettings.setTargumFontCode(value("targumFont"))
        AppSettings.setSourceFontCode(value("sourceFont"))
        AppSettings.setLinkLoadLevel(value("linkLoadLevel").toInt())
        AppSettings.setShowZmanimWidgetsEnabled(value("showZmanimWidgets").toBooleanStrict())
        AppSettings.setShowTempleCountdownEnabled(value("showTempleCountdown").toBooleanStrict())
        AppSettings.setShowHomeWallpaperEnabled(value("showHomeWallpaper").toBooleanStrict())
        AppSettings.setCompactModeEnabled(value("compactMode").toBooleanStrict())
        appGraph.mainAppState.setThemeStyle(ThemeStyle.valueOf(value("themeStyle")))
        appGraph.mainAppState.setAccentColor(AccentColor.valueOf(value("accentColor")))
    }
}