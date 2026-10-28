package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.SemanticSearchPreferences
import io.github.kdroidfilter.seforimapp.features.search.SemanticSearchSettings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.semantic_search_cache
import seforimapp.seforimapp.generated.resources.semantic_search_candidates
import seforimapp.seforimapp.generated.resources.semantic_search_description
import seforimapp.seforimapp.generated.resources.semantic_search_exact
import seforimapp.seforimapp.generated.resources.semantic_search_limit
import seforimapp.seforimapp.generated.resources.semantic_search_number_invalid
import seforimapp.seforimapp.generated.resources.semantic_search_prefer_meaning
import seforimapp.seforimapp.generated.resources.semantic_search_settings
import seforimapp.seforimapp.generated.resources.semantic_search_threshold
import seforimapp.seforimapp.generated.resources.semantic_search_threshold_mode
import seforimapp.seforimapp.generated.resources.semantic_search_topk_mode

@Composable
internal fun SemanticSearchSettingsCard() {
    val preferences by AppSettings.flow(SemanticSearchSettings.preferences).collectAsState()
    val update: (SemanticSearchPreferences) -> Unit = { AppSettings[SemanticSearchSettings.preferences] = it }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground, shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(Res.string.semantic_search_settings))
        Text(stringResource(Res.string.semantic_search_description), color = JewelTheme.globalColors.text.info)
        ListComboBox(
            items =
                listOf(
                    stringResource(Res.string.semantic_search_topk_mode),
                    stringResource(Res.string.semantic_search_threshold_mode),
                ),
            selectedIndex = if (preferences.thresholdEnabled) 1 else 0,
            onSelectedItemChange = { update(preferences.copy(thresholdEnabled = it == 1)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (preferences.thresholdEnabled) {
            SemanticNumberField(
                Res.string.semantic_search_threshold,
                preferences.minimumSimilarity.toString(),
                valid = { it.toFloatOrNull()?.let { number -> number.isFinite() && number in 0f..1f } == true },
            ) { update(preferences.copy(minimumSimilarity = it.toFloat())) }
            SemanticNumberField(
                Res.string.semantic_search_limit,
                preferences.maximumResults.toString(),
                valid = { it.toIntOrNull()?.let { number -> number >= 0 } == true },
            ) { update(preferences.copy(maximumResults = it.toInt())) }
        }
        SemanticNumberField(
            Res.string.semantic_search_candidates,
            preferences.candidates.toString(),
            valid = { it.toIntOrNull()?.let { number -> number > 0 } == true },
        ) { update(preferences.copy(candidates = it.toInt())) }
        CheckboxRow(
            checked = preferences.cacheQueries,
            onCheckedChange = { update(preferences.copy(cacheQueries = it)) },
            text = stringResource(Res.string.semantic_search_cache),
        )
        if (!preferences.thresholdEnabled) {
            CheckboxRow(
                checked = preferences.exactTopK,
                onCheckedChange = { update(preferences.copy(exactTopK = it)) },
                text = stringResource(Res.string.semantic_search_exact),
            )
        }
        CheckboxRow(
            checked = preferences.preferMeaning,
            onCheckedChange = { update(preferences.copy(preferMeaning = it)) },
            text = stringResource(Res.string.semantic_search_prefer_meaning),
        )
    }
}

@Composable
private fun SemanticNumberField(
    label: StringResource,
    value: String,
    valid: (String) -> Boolean,
    onValue: (String) -> Unit,
) {
    val field = rememberTextFieldState(value)
    val currentOnValue by rememberUpdatedState(onValue)
    val currentValid by rememberUpdatedState(valid)
    LaunchedEffect(value) {
        if (!currentValid(field.text.toString())) field.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(field.text) {
        val text = field.text.toString()
        if (currentValid(text) && text != value) currentOnValue(text)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(label), modifier = Modifier.weight(1f))
            TextField(state = field, modifier = Modifier.width(120.dp))
        }
        if (!valid(field.text.toString())) Text(stringResource(Res.string.semantic_search_number_invalid))
    }
}
