package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimlibrary.search.SemanticSearchOptions
import kotlinx.serialization.Serializable

/** Persisted experiments; every existing behavior remains the default. */
@Serializable
data class SemanticSearchPreferences(
    val thresholdEnabled: Boolean = false,
    val minimumSimilarity: Float = 0.8f,
    val candidates: Int = 150,
    val maximumResults: Int = 0,
    val exactTopK: Boolean = false,
    val cacheQueries: Boolean = false,
    val preferMeaning: Boolean = false,
) {
    fun options(): SemanticSearchOptions =
        SemanticSearchOptions(
            candidates = candidates.coerceAtLeast(1),
            minimumSimilarity = if (thresholdEnabled) minimumSimilarity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.8f else null,
            maximumResults = maximumResults.takeIf { it > 0 },
            exactTopK = exactTopK,
            cacheQueries = cacheQueries,
            denseWeight = if (preferMeaning) 1.5 else 1.0,
        )
}

object SemanticSearchSettings : SettingsGroup("semantic_search") {
    val preferences by json(SemanticSearchPreferences())

    /** Called once when opening a session, so an in-progress search keeps its settings. */
    fun currentOptions(): SemanticSearchOptions = AppSettings[preferences].options()
}
