package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.all_notes_empty
import seforimapp.seforimapp.generated.resources.all_notes_sort_book
import seforimapp.seforimapp.generated.resources.all_notes_sort_time
import seforimapp.seforimapp.generated.resources.all_notes_title
import seforimapp.seforimapp.generated.resources.all_notes_unknown_book
import seforimapp.seforimapp.generated.resources.delete_note
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
    val scope = rememberCoroutineScope()

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
            val sortOptions =
                listOf(
                    stringResource(Res.string.all_notes_sort_book),
                    stringResource(Res.string.all_notes_sort_time),
                )
            ListComboBox(
                items = sortOptions,
                selectedIndex = if (sort == NotesLibrarySort.BOOK) 0 else 1,
                onSelectedItemChange = { index ->
                    sort = if (index == 0) NotesLibrarySort.BOOK else NotesLibrarySort.TIME
                },
                modifier = Modifier.width(160.dp),
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    items(sortedNotes, key = { it.note.id }) { entry ->
                        val defaultBookTitle = booksById[entry.bookId]?.title ?: unknownBook
                        val locationPath = locationPaths[entry.note.lineId] ?: defaultBookTitle
                        NoteLibraryCard(
                            entry = entry,
                            locationPath = locationPath,
                            onClick = { onOpenNote(entry.bookId, entry.note.lineId) },
                            onDelete = { scope.launch { noteStore.removeNote(entry.bookId, entry.note.id) } },
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val accent = JewelTheme.globalColors.outlines.focused
    val shape = RoundedCornerShape(10.dp)

    val stripeColor =
        if (hovered) {
            accent.copy(alpha = 0.7f)
        } else {
            accent.copy(alpha = 0.4f)
        }
    val cardBackground =
        if (hovered) {
            JewelTheme.globalColors.panelBackground
        } else {
            JewelTheme.globalColors.panelBackground.copy(alpha = 0.55f)
        }
    val borderColor =
        if (hovered) {
            accent.copy(alpha = 0.5f)
        } else {
            JewelTheme.globalColors.borders.normal
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(shape)
                .background(cardBackground)
                .border(1.dp, borderColor, shape)
                .hoverable(hoverSource)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .drawBehind {
                    val stripeWidth = 3.dp.toPx()
                    val verticalInset = 8.dp.toPx()
                    val edgeInset = 5.dp.toPx()
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
                }
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = locationPath,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        )
        if (entry.note.quote.isNotBlank()) {
            Text(
                text = entry.note.quote,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
                textAlign = TextAlign.Justify,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        Text(
            text = entry.note.note,
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val stamp = entry.note.updatedAt.takeIf { it > 0L }
            if (stamp != null) {
                Text(
                    text =
                        NOTES_LIBRARY_TIME_FORMATTER.format(
                            Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()),
                        ),
                    fontSize = 11.sp,
                    color =
                        JewelTheme.globalColors.text.info
                            .copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (hovered) {
                IconActionButton(
                    key = AllIconsKeys.General.Delete,
                    onClick = onDelete,
                    contentDescription = stringResource(Res.string.delete_note),
                )
            }
        }
    }
}

