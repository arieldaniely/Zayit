package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.history.HistoryEntry
import io.github.kdroidfilter.seforimapp.framework.history.HistoryType
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.kdroidfilter.seforimapp.icons.History
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.delete_history_item
import seforimapp.seforimapp.generated.resources.history_in_desktop
import seforimapp.seforimapp.generated.resources.recent_history
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeHistoryWidget(modifier: Modifier = Modifier) {
    val appGraph = LocalAppGraph.current
    val historyManager = appGraph.historyManager
    val entries by historyManager.entries.collectAsState()

    val recentEntries = remember(entries) { entries.take(4) }
    if (recentEntries.isEmpty()) return

    val recentHistoryTitle = stringResource(Res.string.recent_history)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                imageVector = History,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = JewelTheme.globalColors.text.info,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = recentHistoryTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = JewelTheme.globalColors.text.info,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recentEntries.forEach { entry ->
                HomeHistoryCard(
                    entry = entry,
                    modifier = Modifier.weight(1f),
                    onOpen = {
                        openHistoryEntry(entry, appGraph)
                    },
                    onDelete = {
                        historyManager.deleteEntry(entry.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeHistoryCard(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val deleteItemLabel = stringResource(Res.string.delete_history_item)

    val cardBackground =
        if (isHovered) {
            JewelTheme.globalColors.panelBackground
        } else {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.6f)
        }
    val borderColor =
        if (isHovered) {
            JewelTheme.globalColors.outlines.focused.copy(alpha = 0.5f)
        } else {
            JewelTheme.globalColors.borders.normal.copy(alpha = 0.4f)
        }

    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier =
            modifier
                .height(72.dp)
                .clip(shape)
                .background(cardBackground)
                .border(1.dp, borderColor, shape)
                .hoverable(interactionSource)
                .clickable { onOpen() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val iconKey = if (entry.type == HistoryType.BOOK) Book else History
                Icon(
                    imageVector = iconKey,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = JewelTheme.globalColors.text.info,
                )
                Spacer(Modifier.width(6.dp))

                val titleText =
                    when (entry.type) {
                        HistoryType.BOOK -> entry.bookTitle.orEmpty()
                        HistoryType.SEARCH -> "'${entry.searchQuery.orEmpty()}'"
                    }
                Text(
                    text = titleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (isHovered) {
                    IconActionButton(
                        key = AllIconsKeys.General.Delete,
                        onClick = onDelete,
                        contentDescription = deleteItemLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

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

            val timeLabel = TIME_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))
            val desktopLabel =
                if (entry.desktopName.isNotBlank()) {
                    stringResource(Res.string.history_in_desktop, entry.desktopName)
                } else {
                    ""
                }
            val metaString = if (desktopLabel.isNotBlank()) "$desktopLabel · $timeLabel" else timeLabel

            Text(
                text = metaString,
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
            )
        }
    }
}

private fun openHistoryEntry(
    entry: HistoryEntry,
    appGraph: io.github.kdroidfilter.seforimapp.framework.di.AppGraph,
) {
    val tabsVm = appGraph.tabsViewModel
    when (entry.type) {
        HistoryType.BOOK -> {
            val bookId = entry.bookId ?: return
            tabsVm.replaceCurrentTabDestination(
                TabsDestination.BookContent(
                    bookId = bookId,
                    tabId = UUID.randomUUID().toString(),
                    lineId = entry.lineId,
                ),
            )
        }

        HistoryType.SEARCH -> {
            val query = entry.searchQuery.orEmpty()
            if (query.isBlank()) return
            tabsVm.replaceCurrentTabDestination(
                TabsDestination.Search(
                    searchQuery = query,
                    tabId = UUID.randomUUID().toString(),
                ),
            )
        }
    }
}
