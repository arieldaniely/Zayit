package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.annotations.BookUserNote
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.all_notes_empty
import seforimapp.seforimapp.generated.resources.all_notes_open
import seforimapp.seforimapp.generated.resources.all_notes_sort_book
import seforimapp.seforimapp.generated.resources.all_notes_sort_time
import seforimapp.seforimapp.generated.resources.all_notes_title
import seforimapp.seforimapp.generated.resources.all_notes_unknown_book
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class NotesLibrarySort { BOOK, TIME }

private val NOTES_LIBRARY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy · HH:mm")

/**
 * Resolves the hierarchical location path for a note:
 * Book title > Section 1 > Section 2 > ...
 * Deduplicates the root title if identical to the book, and strips consecutive identical items.
 */
suspend fun resolveNoteLocationPath(
    repository: SeforimRepository,
    bookTitle: String,
    lineId: Long,
): String =
    withContext(Dispatchers.IO) {
        val tocId = runSuspendCatching { repository.getTocEntryIdForLine(lineId) }.getOrNull()
        if (tocId == null) return@withContext bookTitle

        val tocPath = mutableListOf<String>()
        var current: Long? = tocId
        var guard = 0
        while (current != null && guard++ < 200) {
            currentCoroutineContext().ensureActive()
            val entry = repository.getTocEntry(current) ?: break
            tocPath.add(0, entry.text)
            current = entry.parentId
        }

        val adjusted = if (tocPath.firstOrNull() == bookTitle) tocPath.drop(1) else tocPath
        val deduplicated = adjusted.filterIndexed { index, s -> index == 0 || s != adjusted[index - 1] }
        val pieces = listOf(bookTitle) + deduplicated
        pieces.joinToString(" > ")
    }

/**
 * Side panel displaying all library notes across all books, with sorting and full section breadcrumbs.
 */
@Composable
fun NotesLibraryPanel(
    noteStore: NoteStore,
    onOpenNote: (bookId: Long, lineId: Long) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    repository: SeforimRepository = LocalAppGraph.current.repository,
) {
    LaunchedEffect(noteStore) { noteStore.loadAll() }
    val allNotes by noteStore.allNotes.collectAsState()
    val booksById = remember { CatalogCache.getAllBooks().orEmpty().associateBy { it.id } }
    var sort by remember { mutableStateOf(NotesLibrarySort.BOOK) }
    val unknownBook = stringResource(Res.string.all_notes_unknown_book)

    var locationPaths by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LaunchedEffect(allNotes, repository, booksById, unknownBook) {
        withContext(Dispatchers.IO) {
            val updatedPaths = locationPaths.toMutableMap()
            var changed = false
            for (entry in allNotes) {
                val lineId = entry.note.lineId
                if (lineId !in updatedPaths) {
                    val bookTitle = booksById[entry.bookId]?.title ?: unknownBook
                    updatedPaths[lineId] = resolveNoteLocationPath(repository, bookTitle, lineId)
                    changed = true
                }
            }
            if (changed) {
                locationPaths = updatedPaths
            }
        }
    }

    val sortedNotes =
        remember(allNotes, booksById, sort, unknownBook) {
            when (sort) {
                NotesLibrarySort.BOOK ->
                    allNotes.sortedWith(
                        compareBy<BookUserNote> { booksById[it.bookId]?.title ?: unknownBook }
                            .thenBy { it.note.lineId },
                    )
                NotesLibrarySort.TIME -> allNotes.sortedByDescending { it.note.updatedAt }
            }
        }

    val paneHoverSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .hoverable(paneHoverSource),
    ) {
        PaneHeader(
            label = stringResource(Res.string.all_notes_title),
            interactionSource = paneHoverSource,
            onHide = onHide,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SegmentedControl(
                buttons =
                    listOf(
                        SegmentedControlButtonData(
                            selected = sort == NotesLibrarySort.BOOK,
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        key = AllIconsKeys.Nodes.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = stringResource(Res.string.all_notes_sort_book),
                                        fontSize = 11.sp,
                                    )
                                }
                            },
                            onSelect = { sort = NotesLibrarySort.BOOK },
                        ),
                        SegmentedControlButtonData(
                            selected = sort == NotesLibrarySort.TIME,
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Icon(
                                        key = AllIconsKeys.Vcs.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = stringResource(Res.string.all_notes_sort_time),
                                        fontSize = 11.sp,
                                    )
                                }
                            },
                            onSelect = { sort = NotesLibrarySort.TIME },
                        ),
                    ),
            )

            if (sortedNotes.isNotEmpty()) {
                Text(
                    text = "(${sortedNotes.size})",
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (sortedNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Annotate,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint =
                                JewelTheme.globalColors.text.info
                                    .copy(alpha = 0.55f),
                        )
                        Text(
                            text = stringResource(Res.string.all_notes_empty),
                            textAlign = TextAlign.Center,
                            color = JewelTheme.globalColors.text.info,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sortedNotes, key = { it.note.id }) { entry ->
                        val defaultBookTitle = booksById[entry.bookId]?.title ?: unknownBook
                        val locationPath = locationPaths[entry.note.lineId] ?: defaultBookTitle
                        NoteLibraryCard(
                            entry = entry,
                            locationPath = locationPath,
                            onClick = { onOpenNote(entry.bookId, entry.note.lineId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteLibraryCard(
    entry: BookUserNote,
    locationPath: String,
    onClick: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = RoundedCornerShape(8.dp)

    val cardBackground by animateColorAsState(
        targetValue =
            if (hovered) {
                JewelTheme.globalColors.panelBackground
            } else {
                JewelTheme.globalColors.panelBackground.copy(alpha = 0.55f)
            },
        animationSpec = tween(150),
    )
    val borderColor by animateColorAsState(
        targetValue =
            if (hovered) {
                accent.copy(alpha = 0.5f)
            } else {
                JewelTheme.globalColors.borders.normal
            },
        animationSpec = tween(150),
    )
    val stripeColor by animateColorAsState(
        targetValue =
            if (hovered) {
                accent.copy(alpha = 0.8f)
            } else {
                accent.copy(alpha = 0.4f)
            },
        animationSpec = tween(150),
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardBackground)
                .border(1.dp, borderColor, shape)
                .hoverable(hoverSource)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .drawBehind {
                    val stripeWidth = 3.dp.toPx()
                    val verticalInset = 6.dp.toPx()
                    val edgeInset = 4.dp.toPx()
                    val x =
                        if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - edgeInset - stripeWidth
                        } else {
                            edgeInset
                        }
                    drawRoundRect(
                        color = stripeColor,
                        topLeft = Offset(x, verticalInset),
                        size = Size(stripeWidth, (size.height - 2 * verticalInset).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(stripeWidth / 2, stripeWidth / 2),
                    )
                }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = locationPath,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val time =
                NOTES_LIBRARY_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(entry.note.updatedAt).atZone(ZoneId.systemDefault()),
                )
            Text(time, color = JewelTheme.globalColors.text.info, fontSize = 10.sp)
        }
        if (entry.note.quote.isNotBlank()) {
            Text(
                text = "״${entry.note.quote}״",
                color = JewelTheme.globalColors.text.info,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = entry.note.note,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(1.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                key = AllIconsKeys.General.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(9.dp),
                tint = if (hovered) accent else JewelTheme.globalColors.text.info,
            )
            Text(
                text = stringResource(Res.string.all_notes_open),
                color = if (hovered) accent else JewelTheme.globalColors.text.info,
                fontSize = 10.sp,
                fontWeight = if (hovered) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
