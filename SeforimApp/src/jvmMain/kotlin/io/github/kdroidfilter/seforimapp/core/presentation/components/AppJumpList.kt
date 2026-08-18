package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kdroid.gematria.converter.toHebrewNumeral
import dev.nucleusframework.launcher.windows.JumpListCategory
import dev.nucleusframework.launcher.windows.JumpListItem
import dev.nucleusframework.launcher.windows.WindowsJumpListManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.deeplink.bookShareLink
import io.github.kdroidfilter.seforimapp.core.deeplink.toShareLink
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.desktop_new
import seforimapp.seforimapp.generated.resources.desktops_label
import seforimapp.seforimapp.generated.resources.favorites_title
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.menu_new_tab
import seforimapp.seforimapp.generated.resources.open_tabs
import seforimapp.seforimapp.generated.resources.recently_closed
import seforimapp.seforimapp.generated.resources.search_results_tab_title

private const val SCHEME_TAB = "seforim://tab/"
private const val SCHEME_DESKTOP = "seforim://desktop/"
private const val SCHEME_NEW_TAB = "seforim://new-tab"
private const val SCHEME_NEW_DESKTOP = "seforim://new-desktop"
private const val MAX_DYNAMIC_ITEMS = 4

@Composable
fun AppJumpList(
    desktopManager: DesktopManager,
    tabsViewModel: TabsViewModel,
    favoritesStore: FavoritesStore,
    pendingDeepLink: StateFlow<String?>,
    onClearDeepLink: () -> Unit,
) {
    if (!PlatformInfo.isWindows || !WindowsJumpListManager.isAvailable) return

    val isDark = JewelTheme.isDark
    val desktops by desktopManager.desktops.collectAsState()
    val activeDesktopId by desktopManager.activeDesktopId.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()
    val recentlyClosedTabs by tabsViewModel.recentlyClosedTabs.collectAsState()
    val favoritesRevision by favoritesStore.revision.collectAsState()
    var favorites by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }

    LaunchedEffect(favoritesRevision) {
        favorites = favoritesStore.query().take(MAX_DYNAMIC_ITEMS)
    }

    val openTabsLabel = stringResource(Res.string.open_tabs)
    val desktopsLabel = stringResource(Res.string.desktops_label)
    val favoritesLabel = stringResource(Res.string.favorites_title)
    val recentlyClosedLabel = stringResource(Res.string.recently_closed)
    val homeLabel = stringResource(Res.string.home)
    val searchResultsFormat = stringResource(Res.string.search_results_tab_title, "%1\$s")
    val newTabLabel = stringResource(Res.string.menu_new_tab)
    val newDesktopLabel = stringResource(Res.string.desktop_new)
    val nextHebrewIndex = (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"
    val nextDesktopName = stringResource(Res.string.desktop_default_name, nextHebrewIndex)
    val currentClearDeepLink by rememberUpdatedState(onClearDeepLink)

    // Re-register handler when nextDesktopName changes so the captured name stays current
    LaunchedEffect(nextDesktopName) {
        pendingDeepLink.filterNotNull().collect { action ->
            // Only handle (and clear) seforim:// jump-list actions. Other schemes — notably
            // shareable zayita:// content links — belong to ContentDeepLinkHandler; clearing them
            // here would race that collector and swallow the link (StateFlow is conflated).
            val handled =
                when {
                    action.startsWith(SCHEME_TAB) -> {
                        val index = action.removePrefix(SCHEME_TAB).toIntOrNull()
                        if (index != null) {
                            val tabs = tabsViewModel.state.value.tabs
                            if (index in tabs.indices) tabsViewModel.onEvent(TabsEvents.OnSelect(index))
                        }
                        true
                    }
                    action.startsWith(SCHEME_DESKTOP) -> {
                        val index = action.removePrefix(SCHEME_DESKTOP).toIntOrNull()
                        if (index != null) {
                            val desktop = desktopManager.desktops.value.getOrNull(index)
                            if (desktop != null) desktopManager.switchTo(desktop.id)
                        }
                        true
                    }
                    action == SCHEME_NEW_TAB -> {
                        tabsViewModel.onEvent(TabsEvents.OnAdd)
                        true
                    }
                    action == SCHEME_NEW_DESKTOP -> {
                        desktopManager.createDesktop(nextDesktopName)
                        true
                    }
                    else -> false
                }
            if (handled) currentClearDeepLink()
        }
    }

    // Rebuild the persisted Windows jump list whenever any displayed source changes.
    LaunchedEffect(desktops, activeDesktopId, tabsState, favorites, recentlyClosedTabs, isDark) {
        val tabs = tabsState.tabs

        val tabItems =
            tabs.mapIndexed { index, tab ->
                val rawTitle = tab.title
                val title =
                    when {
                        rawTitle.isEmpty() -> homeLabel
                        tab.tabType == TabType.SEARCH -> searchResultsFormat.replace("%1\$s", rawTitle)
                        else -> rawTitle
                    }
                JumpListItem(
                    title = title,
                    arguments = "$SCHEME_TAB$index",
                    description = title,
                    icon = JumpListIcons.iconForTab(tab.tabType, rawTitle, isDark),
                )
            }

        val desktopItems =
            desktops.mapIndexed { index, desktop ->
                JumpListItem(
                    title = desktop.name,
                    arguments = "$SCHEME_DESKTOP$index",
                    description = desktop.name,
                    icon = JumpListIcons.iconForDesktop(isDark),
                )
            }

        val favoriteItems =
            favorites.map { favorite ->
                JumpListItem(
                    title = favorite.title,
                    arguments = bookShareLink(favorite.bookId, favorite.lineId),
                    description = favorite.title,
                    icon = JumpListIcons.iconForFavorite(isDark),
                )
            }

        val recentlyClosedItems =
            recentlyClosedTabs
                .mapNotNull { tab ->
                    tab.destination.toShareLink()?.let { link ->
                        val title = tab.title.ifBlank { homeLabel }
                        JumpListItem(
                            title = title,
                            arguments = link,
                            description = title,
                            icon = JumpListIcons.iconForRecentlyClosed(isDark),
                        )
                    }
                }.take(MAX_DYNAMIC_ITEMS)

        val categories =
            buildList {
                if (favoriteItems.isNotEmpty()) add(JumpListCategory(name = favoritesLabel, items = favoriteItems))
                if (recentlyClosedItems.isNotEmpty()) {
                    add(JumpListCategory(name = recentlyClosedLabel, items = recentlyClosedItems))
                }
                if (tabItems.isNotEmpty()) add(JumpListCategory(name = openTabsLabel, items = tabItems))
                if (desktopItems.isNotEmpty()) add(JumpListCategory(name = desktopsLabel, items = desktopItems))
            }

        val tasks =
            listOf(
                JumpListItem(
                    title = newTabLabel,
                    arguments = SCHEME_NEW_TAB,
                    icon = JumpListIcons.iconForNewTab(isDark),
                ),
                JumpListItem(
                    title = newDesktopLabel,
                    arguments = SCHEME_NEW_DESKTOP,
                    icon = JumpListIcons.iconForNewDesktop(isDark),
                ),
            )

        WindowsJumpListManager.setJumpList(categories = categories, tasks = tasks)
    }
}
