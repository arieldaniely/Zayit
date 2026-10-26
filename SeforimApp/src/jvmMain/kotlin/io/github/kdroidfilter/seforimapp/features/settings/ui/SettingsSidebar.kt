package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.icons.BootstrapPersonLines
import io.github.kdroidfilter.seforimapp.icons.FileArrowDown
import io.github.kdroidfilter.seforimapp.icons.Library_books
import io.github.kdroidfilter.seforimapp.icons.LucideInspectionPanel
import io.github.kdroidfilter.seforimapp.icons.MaterialIconsDisplay_settings
import io.github.kdroidfilter.seforimapp.icons.RadixFontRoman
import io.github.kdroidfilter.seforimapp.icons.TablerInfoSquare
import io.github.kdroidfilter.seforimapp.icons.TablerLicense
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_category_about
import seforimapp.seforimapp.generated.resources.settings_category_conditions
import seforimapp.seforimapp.generated.resources.settings_category_data
import seforimapp.seforimapp.generated.resources.settings_category_display
import seforimapp.seforimapp.generated.resources.settings_category_fonts
import seforimapp.seforimapp.generated.resources.settings_category_general
import seforimapp.seforimapp.generated.resources.settings_category_personal_library
import seforimapp.seforimapp.generated.resources.settings_category_profile
import seforimapp.seforimapp.generated.resources.settings_description_about
import seforimapp.seforimapp.generated.resources.settings_description_conditions
import seforimapp.seforimapp.generated.resources.settings_description_data
import seforimapp.seforimapp.generated.resources.settings_description_display
import seforimapp.seforimapp.generated.resources.settings_description_fonts
import seforimapp.seforimapp.generated.resources.settings_description_general
import seforimapp.seforimapp.generated.resources.settings_description_personal
import seforimapp.seforimapp.generated.resources.settings_description_profile
import seforimapp.seforimapp.generated.resources.settings_group_information
import seforimapp.seforimapp.generated.resources.settings_group_library
import seforimapp.seforimapp.generated.resources.settings_group_reading
import seforimapp.seforimapp.generated.resources.settings_search_clear
import seforimapp.seforimapp.generated.resources.settings_search_empty
import seforimapp.seforimapp.generated.resources.settings_search_placeholder

private data class SettingsItem(
    val label: String,
    val destination: SettingsDestination,
    val icon: ImageVector,
    val description: String,
    val group: Int,
)

@Composable
fun SettingsSidebar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    searchState: TextFieldState = rememberTextFieldState(),
) {
    val backStack = navController.currentBackStackEntryAsState()
    val currentRoute =
        backStack.value
            ?.destination
            ?.route
            .orEmpty()

    SettingsSidebarView(currentRoute, onSelect = { destination ->
        searchState.clearText()
        navController.navigate(destination) {
            launchSingleTop = true
            popUpTo(SettingsDestination.General) { saveState = true }
            restoreState = true
        }
    }, modifier = modifier, searchState = searchState)
}

@Composable
internal fun SettingsSidebarView(
    currentRoute: String,
    onSelect: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
    searchState: TextFieldState = rememberTextFieldState(),
) {
    val allItems =
        listOf(
            SettingsItem(
                label = stringResource(Res.string.settings_category_general),
                destination = SettingsDestination.General,
                icon = LucideInspectionPanel,
                description = stringResource(Res.string.settings_description_general),
                group = 0,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_display),
                destination = SettingsDestination.Display,
                icon = MaterialIconsDisplay_settings,
                description = stringResource(Res.string.settings_description_display),
                group = 0,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_profile),
                destination = SettingsDestination.Profile,
                icon = BootstrapPersonLines,
                description = stringResource(Res.string.settings_description_profile),
                group = 0,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_fonts),
                destination = SettingsDestination.Fonts,
                icon = RadixFontRoman,
                description = stringResource(Res.string.settings_description_fonts),
                group = 0,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_data),
                destination = SettingsDestination.Data,
                icon = FileArrowDown,
                description = stringResource(Res.string.settings_description_data),
                group = 1,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_personal_library),
                destination = SettingsDestination.PersonalLibrary,
                icon = Library_books,
                description = stringResource(Res.string.settings_description_personal),
                group = 1,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_about),
                destination = SettingsDestination.About,
                icon = TablerInfoSquare,
                description = stringResource(Res.string.settings_description_about),
                group = 2,
            ),
            SettingsItem(
                label = stringResource(Res.string.settings_category_conditions),
                destination = SettingsDestination.Conditions,
                icon = TablerLicense,
                description = stringResource(Res.string.settings_description_conditions),
                group = 2,
            ),
        )

    val search = searchState
    val focus = remember { FocusRequester() }
    val query = search.text.toString().trim()
    val searchEntries = settingsSearchEntries()
    val visible =
        allItems.filter { item ->
            query.split(Regex("\\s+")).all { word ->
                item.label.contains(word, ignoreCase = true) || item.description.contains(word, ignoreCase = true)
            } ||
                searchEntries.any { it.destination == item.destination && it.matches(query) }
        }
    val groups =
        listOf(
            stringResource(Res.string.settings_group_reading),
            stringResource(Res.string.settings_group_library),
            stringResource(Res.string.settings_group_information),
        )
    Column(
        modifier =
            modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.F && (it.isCtrlPressed || it.isMetaPressed)) {
                    focus.requestFocus()
                    true
                } else {
                    false
                }
            },
    ) {
        TextField(
            state = search,
            placeholder = { Text(stringResource(Res.string.settings_search_placeholder)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (query.isNotEmpty()) {
            Link(stringResource(Res.string.settings_search_clear), onClick = { search.edit { replace(0, length, "") } })
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) Text(stringResource(Res.string.settings_search_empty))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for ((group, entries) in visible.groupBy { it.group }) {
                item(key = "group-$group") {
                    Text(
                        groups[group],
                        color = JewelTheme.globalColors.text.info,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 8.dp),
                    )
                }
                items(entries, key = { it.destination::class.simpleName.orEmpty() }) { item ->
                    val selected =
                        when (item.destination) {
                            is SettingsDestination.General -> currentRoute.contains("General")
                            is SettingsDestination.Display -> currentRoute.contains("Display")
                            is SettingsDestination.Profile -> currentRoute.contains("Profile")
                            is SettingsDestination.Fonts -> currentRoute.contains("Fonts")
                            is SettingsDestination.About -> currentRoute.contains("About")
                            is SettingsDestination.Conditions -> currentRoute.contains("Conditions")
                            is SettingsDestination.Data -> currentRoute.contains("Data")
                            is SettingsDestination.PersonalLibrary -> currentRoute.contains("PersonalLibrary")
                        }
                    SidebarItem(
                        label = item.label,
                        icon = item.icon,
                        selected = selected,
                        onClick = {
                            if (!selected || query.isNotBlank()) {
                                onSelect(item.destination)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val selectedBackground = accent.copy(alpha = 0.15f)
    val bg = if (selected) selectedBackground else JewelTheme.globalColors.panelBackground
    val iconTint = if (selected) JewelTheme.globalColors.text.selected else JewelTheme.globalColors.text.normal

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg, RoundedCornerShape(8.dp))
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(
                        if (selected) accent else Color.Transparent,
                    ),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
