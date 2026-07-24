package io.github.kdroidfilter.seforimapp.features.history

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.history.HistoryEntry
import io.github.kdroidfilter.seforimapp.framework.history.HistoryManager
import io.github.kdroidfilter.seforimapp.framework.history.HistoryType
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.kdroidfilter.seforimapp.icons.History
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.clear_history
import seforimapp.seforimapp.generated.resources.confirm_clear_history
import seforimapp.seforimapp.generated.resources.delete_history_item
import seforimapp.seforimapp.generated.resources.history
import seforimapp.seforimapp.generated.resources.history_in_desktop
import seforimapp.seforimapp.generated.resources.no_history_items
import seforimapp.seforimapp.generated.resources.open_in_new_tab
import seforimapp.seforimapp.generated.resources.search_history_placeholder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatHebrewDayOfWeek(day: DayOfWeek): String =
    when (day) {
        DayOfWeek.SUNDAY -> "יום ראשון"
        DayOfWeek.MONDAY -> "יום שני"
        DayOfWeek.TUESDAY -> "יום שלישי"
        DayOfWeek.WEDNESDAY -> "יום רביעי"
        DayOfWeek.THURSDAY -> "יום חמישי"
        DayOfWeek.FRIDAY -> "יום שישי"
        DayOfWeek.SATURDAY -> "שבת"
    }

private fun formatHebrewMonth(monthValue: Int): String =
    when (monthValue) {
        1 -> "בינואר"
        2 -> "בפברואר"
        3 -> "במרץ"
        4 -> "באפריל"
        5 -> "במאי"
        6 -> "ביוני"
        7 -> "ביולי"
        8 -> "באוגוסט"
        9 -> "בספטמבר"
        10 -> "באוקטובר"
        11 -> "בנובמבר"
        12 -> "בדצמבר"
        else -> ""
    }

private fun formatHebrewFullDate(date: LocalDate): String {
    val dayOfWeekStr = formatHebrewDayOfWeek(date.dayOfWeek)
    val dayOfMonth = date.dayOfMonth
    val monthStr = formatHebrewMonth(date.monthValue)
    val year = date.year
    return "$dayOfWeekStr, $dayOfMonth $monthStr $year"
}

private data class HistoryDateGroup(
    val dateHeader: String,
    val entries: List<HistoryEntry>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryView(modifier: Modifier = Modifier) {
    val appGraph = LocalAppGraph.current
    val historyManager: HistoryManager = appGraph.historyManager
    val entries by historyManager.entries.collectAsState()

    val searchQueryState = remember { TextFieldState() }
    val searchQuery by remember { derivedStateOf { searchQueryState.text.toString() } }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

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

    val groupedHistory =
        remember(filteredEntries) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            val map = linkedMapOf<String, MutableList<HistoryEntry>>()

            for (entry in filteredEntries) {
                val entryDate = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val header =
                    when (entryDate) {
                        today -> "היום - ${formatHebrewFullDate(today)}"
                        yesterday -> "אתמול - ${formatHebrewFullDate(yesterday)}"
                        else -> formatHebrewFullDate(entryDate)
                    }
                map.getOrPut(header) { mutableListOf() }.add(entry)
            }

            map.map { (header, list) -> HistoryDateGroup(header, list) }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 940.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = History,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = JewelTheme.globalColors.text.info,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(Res.string.history),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Centered Search Bar
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            state = searchQueryState,
                            placeholder = { Text(stringResource(Res.string.search_history_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    key = AllIconsKeys.Actions.Find,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = JewelTheme.globalColors.text.info,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (searchQuery.isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .clickable { searchQueryState.edit { replace(0, length, "") } }
                                        .padding(4.dp),
                            ) {
                                Icon(
                                    key = AllIconsKeys.Windows.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                // Clear History Action Button
                if (entries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                key = AllIconsKeys.Actions.GC,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.clear_history), fontSize = 12.sp)
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                HistoryTabEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    groupedHistory.forEach { group ->
                        item(key = group.dateHeader) {
                            HistoryDateCard(group = group)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        if (showClearConfirmDialog) {
            ClearHistoryConfirmDialog(
                onConfirm = {
                    historyManager.clearAll()
                    showClearConfirmDialog = false
                },
                onDismiss = { showClearConfirmDialog = false },
            )
        }
    }
}

@Composable
private fun HistoryDateCard(group: HistoryDateGroup) {
    val shape = RoundedCornerShape(12.dp)
    val appGraph = LocalAppGraph.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f), shape)
                .padding(16.dp),
    ) {
        // Date Group Header
        Text(
            text = group.dateHeader,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
        )

        // List of entries with timeline on the side
        group.entries.forEachIndexed { index, entry ->
            val isFirst = index == 0
            val isLast = index == group.entries.lastIndex

            HistoryTimelineItemRow(
                entry = entry,
                isFirst = isFirst,
                isLast = isLast,
                onOpen = {
                    openHistoryItem(entry, appGraph, inNewTab = false)
                },
                onOpenNewTab = {
                    openHistoryItem(entry, appGraph, inNewTab = true)
                },
                onDelete = {
                    appGraph.historyManager.deleteEntry(entry.id)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTimelineItemRow(
    entry: HistoryEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onOpenNewTab: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val openNewTabLabel = stringResource(Res.string.open_in_new_tab)
    val deleteItemLabel = stringResource(Res.string.delete_history_item)

    val timeLabel = TIME_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))

    val rowBackground =
        if (isHovered) {
            JewelTheme.globalColors.borders.normal.copy(alpha = 0.15f)
        } else {
            androidx.compose.ui.graphics.Color.Unspecified
        }

    ContextMenuDataProvider(
        items = {
            listOf(
                ContextMenuItem(openNewTabLabel, onOpenNewTab),
                ContextMenuItem(deleteItemLabel, onDelete),
            )
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(rowBackground)
                    .hoverable(interactionSource)
                    .clickable { onOpen() }
                    .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Timeline Column: Timestamp + Vertical Line + Node Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(90.dp),
            ) {
                // Timestamp
                Text(
                    text = timeLabel,
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.8f),
                    modifier = Modifier.width(45.dp),
                )

                // Node Dot
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(JewelTheme.globalColors.text.info.copy(alpha = 0.6f)),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Item Icon (Search or Book)
            if (entry.type == HistoryType.BOOK) {
                Icon(
                    imageVector = Book,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = JewelTheme.globalColors.text.info,
                )
            } else {
                Icon(
                    key = AllIconsKeys.Actions.Find,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = JewelTheme.globalColors.text.info,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                // Title
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

                // Detail / Subtitle
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
            }

            // Desktop / Workspace Name Badge
            if (entry.desktopName.isNotBlank()) {
                val desktopLabel = stringResource(Res.string.history_in_desktop, entry.desktopName)
                Text(
                    text = desktopLabel,
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Hover Delete Button
            if (isHovered) {
                Spacer(Modifier.width(8.dp))
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

@Composable
private fun HistoryTabEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = JewelTheme.globalColors.text.info.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(Res.string.no_history_items),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.info,
            )
        }
    }
}

@Composable
private fun ClearHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier =
                Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(12.dp))
                    .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(Res.string.clear_history),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.confirm_clear_history),
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("ביטול")
                    }
                    Spacer(Modifier.width(8.dp))
                    DefaultButton(onClick = onConfirm) {
                        Text(stringResource(Res.string.clear_history))
                    }
                }
            }
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
            val tabId = UUID.randomUUID().toString()
            val dest =
                TabsDestination.BookContent(
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
            val tabId = UUID.randomUUID().toString()
            val dest =
                TabsDestination.Search(
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
