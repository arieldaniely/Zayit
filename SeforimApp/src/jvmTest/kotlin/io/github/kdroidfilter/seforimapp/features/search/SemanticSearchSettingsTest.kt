package io.github.kdroidfilter.seforimapp.features.search

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.search.SemanticSearchOptions
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticSearchSettingsTest {
    @Test
    fun defaultsMatchBaselineAndThresholdHasNoImplicitCap() {
        assertEquals(SemanticSearchOptions(), SemanticSearchPreferences().options())
        val options = SemanticSearchPreferences(thresholdEnabled = true, minimumSimilarity = 0.92f).options()
        assertEquals(0.92f, options.minimumSimilarity)
        assertNull(options.maximumResults)
    }

    @Test
    fun preferencesPersistAndAreIncludedInPortableBackup() {
        val storage = PropertiesSettings(Properties())
        AppSettings.initialize(storage)
        try {
            val saved = SemanticSearchPreferences(thresholdEnabled = true, cacheQueries = true, maximumResults = 500)
            AppSettings[SemanticSearchSettings.preferences] = saved
            AppSettings.initialize(storage)
            assertEquals(saved, AppSettings[SemanticSearchSettings.preferences])
            val portable = AppSettings.exportPortablePreferences(storage)
            assertTrue(SemanticSearchSettings.preferences.key in portable)
            val restored = PropertiesSettings(Properties())
            AppSettings.importPortablePreferences(restored, portable)
            AppSettings.initialize(restored)
            assertEquals(saved, AppSettings[SemanticSearchSettings.preferences])
        } finally {
            AppSettings.initialize(PropertiesSettings(Properties()))
        }
    }
}
