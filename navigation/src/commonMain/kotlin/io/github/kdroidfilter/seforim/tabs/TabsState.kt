package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class TabItem(
    val id: Int,
    val title: String = "Default Tab",
    val destination: TabsDestination = TabsDestination.Home(UUID.randomUUID().toString()),
    val tabType: TabType = TabType.SEARCH,
    val isPinned: Boolean = false,
)

@Immutable
data class TabsState(
    val tabs: List<TabItem>,
    val selectedTabIndex: Int,
)
