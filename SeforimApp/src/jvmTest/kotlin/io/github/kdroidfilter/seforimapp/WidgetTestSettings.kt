package io.github.kdroidfilter.seforimapp

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import java.util.Properties

internal fun testAppSettings() = AppSettings.also { it.initialize(PropertiesSettings(Properties())) }

internal fun testAppGraph(): io.github.kdroidfilter.seforimapp.framework.di.AppGraph {
    val graph = io.mockk.mockk<io.github.kdroidfilter.seforimapp.framework.di.AppGraph>()
    val main = io.mockk.mockk<io.github.kdroidfilter.seforimapp.core.MainAppState>()
    io.mockk.every { main.accentColor } returns
        kotlinx.coroutines.flow.MutableStateFlow(
            io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor.Default,
        )
    io.mockk.every { main.themeStyle } returns
        kotlinx.coroutines.flow.MutableStateFlow(
            io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle.Islands,
        )
    io.mockk.every { graph.appSettings } returns AppSettings
    io.mockk.every { graph.mainAppState } returns main
    io.mockk.every { graph.dictionaryIndex } returns
        io.github.kdroidfilter.seforimapp.features.home.widgets.library
            .DictionaryIndex()
    io.mockk.every { graph.repository } returns io.mockk.mockk(relaxed = true)
    val driver =
        app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
            app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY,
        )
    io.github.kdroidfilter.seforimapp.db.UserSettingsDb.Schema
        .create(driver)
    val database =
        io.github.kdroidfilter.seforimapp.db
            .UserSettingsDb(driver)
    io.mockk.every { graph.historyStore } returns
        io.github.kdroidfilter.seforimapp.core.history
            .HistoryStore(database)
    io.mockk.every { graph.favoritesStore } returns
        io.github.kdroidfilter.seforimapp.core.favorites
            .FavoritesStore(database)
    io.mockk.every { graph.noteStore } returns
        io.github.kdroidfilter.seforimapp.core.annotations
            .NoteStore(database)
    io.mockk.every { graph.shnayimMikraStore } returns
        io.github.kdroidfilter.seforimapp.core.shnayimmikra
            .ShnayimMikraStore(database)
    return graph
}
