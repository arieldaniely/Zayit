package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

internal data class SettingsSearchEntry(
    val destination: SettingsDestination,
    val category: String,
    val title: String,
    val description: String,
    val key: StringResource? = null,
    val keywords: String = "",
) {
    fun matches(query: String): Boolean {
        val text = "$category $title $description $keywords".searchKey()
        return query.searchKey().split(Regex("\\s+")).all { it in text }
    }
}

@Composable
internal fun settingsSearchEntries(): List<SettingsSearchEntry> =
    listOf(
        SettingsSearchEntry(
            SettingsDestination.General,
            stringResource(Res.string.settings_category_general),
            stringResource(Res.string.close_book_tree_on_new_book),
            stringResource(Res.string.close_book_tree_on_new_book_description),
            key = Res.string.close_book_tree_on_new_book,
        ),
        SettingsSearchEntry(
            SettingsDestination.General,
            stringResource(Res.string.settings_category_general),
            stringResource(Res.string.settings_persist_session),
            stringResource(Res.string.settings_persist_session_description),
            key = Res.string.settings_persist_session,
        ),
        SettingsSearchEntry(
            SettingsDestination.General,
            stringResource(Res.string.settings_category_general),
            stringResource(Res.string.settings_keep_screen_awake),
            stringResource(Res.string.settings_keep_screen_awake_description),
            key = Res.string.settings_keep_screen_awake,
        ),
        SettingsSearchEntry(
            SettingsDestination.General,
            stringResource(Res.string.settings_category_general),
            stringResource(Res.string.settings_search_feedback),
            stringResource(Res.string.settings_search_feedback_description),
            key = Res.string.settings_search_feedback,
        ),
        SettingsSearchEntry(
            SettingsDestination.General,
            stringResource(Res.string.settings_category_general),
            stringResource(Res.string.app_name),
            stringResource(Res.string.settings_info_fork_notice),
            key = Res.string.app_name,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_theme_style_label),
            stringResource(Res.string.settings_description_display),
            key = Res.string.settings_theme_style_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_accent_color_label),
            stringResource(Res.string.settings_description_display),
            key = Res.string.settings_accent_color_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_antialiasing),
            stringResource(Res.string.settings_antialiasing_description),
            key = Res.string.settings_antialiasing,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_show_home_wallpaper),
            stringResource(Res.string.settings_show_home_wallpaper_description),
            key = Res.string.settings_show_home_wallpaper,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_compact_mode),
            stringResource(Res.string.settings_compact_mode_description),
            key = Res.string.settings_compact_mode,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_link_load_title),
            stringResource(Res.string.settings_link_load_description),
            key = Res.string.settings_link_load_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Display,
            stringResource(Res.string.settings_category_display),
            stringResource(Res.string.settings_max_commentators_per_page),
            stringResource(Res.string.settings_max_commentators_per_page_description),
            key = Res.string.settings_max_commentators_per_page,
        ),
        SettingsSearchEntry(
            SettingsDestination.Fonts,
            stringResource(Res.string.settings_category_fonts),
            stringResource(Res.string.settings_font_book_label),
            stringResource(Res.string.settings_description_fonts),
            key = Res.string.settings_font_book_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Fonts,
            stringResource(Res.string.settings_category_fonts),
            stringResource(Res.string.settings_font_commentary_label),
            stringResource(Res.string.settings_description_fonts),
            key = Res.string.settings_font_commentary_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Fonts,
            stringResource(Res.string.settings_category_fonts),
            stringResource(Res.string.settings_font_targum_label),
            stringResource(Res.string.settings_description_fonts),
            key = Res.string.settings_font_targum_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Fonts,
            stringResource(Res.string.settings_category_fonts),
            stringResource(Res.string.settings_font_sources_label),
            stringResource(Res.string.settings_description_fonts),
            key = Res.string.settings_font_sources_label,
        ),
        SettingsSearchEntry(
            SettingsDestination.Profile,
            stringResource(Res.string.settings_category_profile),
            stringResource(Res.string.settings_category_profile),
            stringResource(Res.string.settings_description_profile),
            key = Res.string.settings_category_profile,
        ),
        SettingsSearchEntry(
            SettingsDestination.Profile,
            stringResource(Res.string.settings_category_profile),
            stringResource(Res.string.onboarding_region_title),
            stringResource(Res.string.onboarding_region_description),
            key = Res.string.onboarding_region_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.database_location_title),
            stringResource(Res.string.database_location_description),
            key = Res.string.database_location_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.optional_vectors),
            stringResource(Res.string.semantic_install_body),
            key = Res.string.optional_vectors,
            keywords = stringResource(Res.string.settings_search_vectors_keywords),
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.settings_pdf_library_title),
            stringResource(Res.string.settings_pdf_library_description),
            key = Res.string.settings_pdf_library_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.data_export_title),
            stringResource(Res.string.data_export_description),
            key = Res.string.data_export_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.data_import_title),
            stringResource(Res.string.data_import_description),
            key = Res.string.data_import_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.Data,
            stringResource(Res.string.settings_category_data),
            stringResource(Res.string.settings_reset_app),
            stringResource(Res.string.data_reset_description),
            key = Res.string.settings_reset_app,
        ),
        SettingsSearchEntry(
            SettingsDestination.PersonalLibrary,
            stringResource(Res.string.settings_category_personal_library),
            stringResource(Res.string.personal_library_title),
            stringResource(Res.string.settings_description_personal),
            key = Res.string.personal_library_title,
        ),
        SettingsSearchEntry(
            SettingsDestination.PersonalLibrary,
            stringResource(Res.string.settings_category_personal_library),
            stringResource(Res.string.personal_semantic_title),
            stringResource(Res.string.personal_semantic_description),
            key = Res.string.personal_semantic_title,
            keywords = stringResource(Res.string.settings_search_vectors_keywords),
        ),
        SettingsSearchEntry(
            SettingsDestination.About,
            stringResource(Res.string.settings_category_about),
            stringResource(Res.string.settings_category_about),
            stringResource(Res.string.settings_info_fork_notice),
            key = Res.string.settings_category_about,
        ),
        SettingsSearchEntry(
            SettingsDestination.Conditions,
            stringResource(Res.string.settings_category_conditions),
            stringResource(Res.string.settings_category_conditions),
            stringResource(Res.string.settings_description_conditions),
            key = Res.string.settings_category_conditions,
        ),
    )

@Composable
internal fun SettingsSearchResults(
    query: String,
    onSelect: (SettingsDestination) -> Unit,
) {
    val matches = settingsSearchEntries().filter { it.matches(query) }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (matches.isEmpty()) item { Text(stringResource(Res.string.settings_search_empty)) }
        items(matches) { entry ->
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                    .clickable { onSelect(entry.destination) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(entry.category, color = JewelTheme.globalColors.text.info, fontSize = 12.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    SettingsSearchControl(entry.key)
                }
                Text(entry.description, color = JewelTheme.globalColors.text.info, fontSize = 13.sp)
            }
        }
    }
}

/** Real controls in the result preview: changes remain visible on the full settings page. */
@Composable
private fun SettingsSearchControl(key: StringResource?) {
    when (key) {
        Res.string.close_book_tree_on_new_book ->
            SettingsSearchCheckbox(AppSettings.closeBookTreeOnNewBookSelectedFlow, AppSettings::setCloseBookTreeOnNewBookSelected)
        Res.string.settings_persist_session ->
            SettingsSearchCheckbox(AppSettings.persistSessionFlow, AppSettings::setPersistSessionEnabled)
        Res.string.settings_keep_screen_awake ->
            SettingsSearchCheckbox(AppSettings.keepScreenAwakeOnBookFlow, AppSettings::setKeepScreenAwakeOnBookEnabled)
        Res.string.settings_search_feedback ->
            SettingsSearchCheckbox(AppSettings.searchFeedbackEnabledFlow, AppSettings::setSearchFeedbackEnabled)
        Res.string.settings_show_home_wallpaper ->
            SettingsSearchCheckbox(AppSettings.showHomeWallpaperFlow, AppSettings::setShowHomeWallpaperEnabled)
        Res.string.settings_compact_mode ->
            SettingsSearchCheckbox(AppSettings.compactModeFlow, AppSettings::setCompactModeEnabled)
        Res.string.settings_antialiasing -> {
            val mode by AppSettings.antiAliasingFlow.collectAsState()
            val modes = AppSettings.AVAILABLE_ANTI_ALIASING
            val off = stringResource(Res.string.settings_antialiasing_off)
            ListComboBox(
                items = modes.map { antiAliasingLabel(it, off) },
                selectedIndex = modes.indexOf(mode).coerceAtLeast(0),
                onSelectedItemChange = { AppSettings.setAntiAliasing(modes[it]) },
                modifier = Modifier.width(180.dp),
            )
        }
        Res.string.settings_theme_style_label -> {
            val main = LocalAppGraph.current.mainAppState
            val style by main.themeStyle.collectAsState()
            val styles = io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle.entries
            ListComboBox(
                items =
                    listOf(
                        stringResource(Res.string.settings_theme_style_classic),
                        stringResource(Res.string.settings_theme_style_islands),
                    ),
                selectedIndex = styles.indexOf(style),
                onSelectedItemChange = { main.setThemeStyle(styles[it]) },
                modifier = Modifier.width(180.dp),
            )
        }
        else -> Unit
    }
}

@Composable
private fun SettingsSearchCheckbox(
    value: StateFlow<Boolean>,
    onChange: (Boolean) -> Unit,
) {
    val checked by value.collectAsState()
    Checkbox(checked = checked, onCheckedChange = onChange)
}
