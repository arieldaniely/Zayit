package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.history

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.history.HistoryEntry
import io.github.kdroidfilter.seforimapp.framework.history.HistoryManager
import io.github.kdroidfilter.seforimapp.framework.history.HistoryType
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.kdroidfilter.seforimapp.icons.History
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.clear_history
import seforimapp.seforimapp.generated.resources.confirm_clear_history
import seforimapp.seforimapp.generated.resources.delete_history_item
import seforimapp.seforimapp.generated.resources.history_in_desktop
import seforimapp.seforimapp.generated.resources.history_older
import seforimapp.seforimapp.generated.resources.history_today
import seforimapp.seforimapp.generated.resources.history_yesterday
import seforimapp.seforimapp.generated.resources.no_history_items
import seforimapp.seforimapp.generated.resources.open_in_new_tab
import seforimapp.seforimapp.generated.resources.search_history_placeholder
import seforimapp.seforimapp.generated.resources.study_history
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

private fun formatHistoryTime(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return if (date == today) {
        TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    } else {
        DATE_FORMATTER.format(date)
    }
}

private data class HistoryGroup(
    val title: String,
    val entries: List<HistoryEntry>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryPanel(
    uiState: BookContentState,
    onEvent: (BookContentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appGraph = LocalAppGraph.current
    val historyManager: HistoryManager = appGraph.historyManager
    val entries by historyManager.entries.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val paneHoverSource = remember { MutableInteractionSource() }

    val filteredEntries =
        remember(entries, searchQuery) {
            if (searchQuery.isBlank()) {
                entries
            } else {
                val q = searchQuery.trim().lowercase()
                entries.filter { entry ->
                    entry.bookTitle.orEmpty().lowercase().contains(q) ||
                        entry.searchQuery.orEmpty().lowercase().contains(q) ||
                        entry.lineDisplayLabel.orEmpty().lowercase().contains(q) ||
                        entry.desktopName.lowercase().contains(q)
                }
            }
        }

    val todayStr = stringResource(Res.string.history_today)
    val yesterdayStr = stringResource(Res.string.history_yesterday)
    val olderStr = stringResource(Res.string.history_older)

    val groupedHistory =
        remember(filteredEntries, todayStr, yesterdayStr, olderStr) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            val todayList = mutableListOf<HistoryEntry>()
            val yesterdayList = mutableListOf<HistoryEntry>()
            val olderList = mutableListOf<HistoryEntry>()

            for (entry in filteredEntries) {
                val entryDate = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                when {
                    entryDate == today -> todayList.add(entry)
                    entryDate == yesterday -> yesterdayList.add(entry)
                    else -> olderList.add(entry)
                }
            }

            buildList {
                if (todayList.isNotEmpty()) add(HistoryGroup(todayStr, todayList))
                if (yesterdayList.isNotEmpty()) add(HistoryGroup(yesterdayStr, yesterdayList))
                if (olderList.isNotEmpty()) add(HistoryGroup(olderStr, olderList))
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .hoverable(paneHoverSource),
    ) {
        PaneHeader(
            label = stringResource(Res.string.study_history),
            interactionSource = paneHoverSource,
            onHide = { onEvent(BookContentEvent.ToggleHistory) },
            actions = {
                if (entries.isNotEmpty()) {
                    val clearTooltip = stringResource(Res.string.clear_history)
                    IconActionButton(
                        key = AllIconsKeys.General.Delete,
                        onClick = { historyManager.clearAll() },
                        contentDescription = clearTooltip,
                    )
                }
            },
        )

        // Search History Filter Bar
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.search_history_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                trailingIcon =
                    if (searchQuery.isNotEmpty()) {
                        {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { searchQuery = "" }
                                        .padding(2.dp),
                            ) {
                                Icon(
                                    key = AllIconsKeys.Windows.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
            )
        }

        if (filteredEntries.isEmpty()) {
            HistoryEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                groupedHistory.forEach { group ->
                    item(key = "header_${group.title}") {
                        Text(
                            text = group.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JewelTheme.globalColors.text.info,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }

                    items(group.entries, key = { it.id }) { entry ->
                        HistoryItemCard(
                            entry = entry,
                            onOpen = {
                                openHistoryItem(entry, appGraph, inNewTab = false)
                            },
                            onOpenNewTab = {
                                openHistoryItem(entry, appGraph, inNewTab = true)
                            },
                            onDelete = {
                                historyManager.deleteEntry(entry.id)
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onOpenNewTab: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val openNewTabLabel = stringResource(Res.string.open_in_new_tab)
    val deleteItemLabel = stringResource(Res.string.delete_history_item)

    val cardBackground =
        if (isHovered) {
            JewelTheme.globalColors.panelBackground
        } else {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
        }
    val borderColor =
        if (isHovered) {
            JewelTheme.globalColors.outlines.focused.copy(alpha = 0.4f)
        } else {
            JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f)
        }

    val shape = RoundedCornerShape(8.dp)

    ContextMenuDataProvider(
        items = {
            listOf(
                ContextMenuItem(openNewTabLabel, onOpenNewTab),
                ContextMenuItem(deleteItemLabel, onDelete),
            )
        },
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(shape)
                    .background(cardBackground)
                    .border(1.dp, borderColor, shape)
                    .hoverable(interactionSource)
                    .clickable { onOpen() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Type Icon
                val iconKey =
                    if (entry.type == HistoryType.BOOK) {
                        Book
                    } else {
                        History
                    }
                Icon(
                    imageVector = iconKey,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = JewelTheme.globalColors.text.info,
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Main Title
                    val titleText =
                        when (entry.type) {
                            HistoryType.BOOK -> entry.bookTitle.orEmpty()
                            HistoryType.SEARCH -> "'${entry.searchQuery.orEmpty()}'"
                        }
                    Text(
                        text = titleText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Secondary detail line
                    val detailText =
                        when (entry.type) {
                            HistoryType.BOOK -> entry.lineDisplayLabel
                            HistoryType.SEARCH -> entry.searchScope
                        }
                    if (!detailText.isNullOrBlank()) {
                        Text(
                            text = detailText,
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.info,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Metadata row: Desktop + Timestamp
                    val desktopLabel =
                        if (entry.desktopName.isNotBlank()) {
                            stringResource(Res.string.history_in_desktop, entry.desktopName)
                        } else {
                            ""
                        }
                    val timeLabel = formatHistoryTime(entry.timestamp)
                    val metaString =
                        if (desktopLabel.isNotBlank()) {
                            "$desktopLabel · $timeLabel"
                        } else {
                            timeLabel
                        }

                    Text(
                        text = metaString,
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (isHovered) {
                    IconActionButton(
                        key = AllIconsKeys.General.Delete,
                        onClick = onDelete,
                        contentDescription = deleteItemLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = History,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(Res.string.no_history_items),
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

private fun openHistoryItem(
    entry: HistoryEntry,
    appGraph: io.github.kdroidfilter.seforimapp.framework.di.AppGraph,
    inNewTab: Boolean,
) {
    val tabsVm = appGraph.tabsViewModel
    when (entry.type) {
        HistoryType.BOOK -> {
            val bookId = entry.bookId ?: return
            val tabId = java.util.UUID.randomUUID().toString()
            val dest =
                io.github.kdroidfilter.seforim.tabs.TabsDestination.BookContent(
                    bookId = bookId,
                    tabId = tabId,
                    lineId = entry.lineId,
                )
            if (inNewTab) {
                tabsVm.openTab(dest)
            } else {
                tabsVm.replaceCurrentTabDestination(dest)
            }
        }

        HistoryType.SEARCH -> {
            val query = entry.searchQuery.orEmpty()
            if (query.isBlank()) return
            val tabId = java.util.UUID.randomUUID().toString()
            val dest =
                io.github.kdroidfilter.seforim.tabs.TabsDestination.Search(
                    searchQuery = query,
                    tabId = tabId,
                )
            if (inNewTab) {
                tabsVm.openTab(dest)
            } else {
                tabsVm.replaceCurrentTabDestination(dest)
            }
        }
    }
}
