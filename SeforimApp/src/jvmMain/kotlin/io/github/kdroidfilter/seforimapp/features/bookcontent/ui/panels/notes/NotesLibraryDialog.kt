package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.core.annotations.BookUserNote
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.all_notes_close
import seforimapp.seforimapp.generated.resources.all_notes_empty
import seforimapp.seforimapp.generated.resources.all_notes_open
import seforimapp.seforimapp.generated.resources.all_notes_sort_book
import seforimapp.seforimapp.generated.resources.all_notes_sort_time
import seforimapp.seforimapp.generated.resources.all_notes_title
import seforimapp.seforimapp.generated.resources.all_notes_unknown_book
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class NotesLibrarySort { BOOK, TIME }

private val NOTES_LIBRARY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy · HH:mm")

@Composable
fun NotesLibraryDialog(
    noteStore: NoteStore,
    onOpenNote: (bookId: Long, lineId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(noteStore) { noteStore.loadAll() }
    val allNotes by noteStore.allNotes.collectAsState()
    val booksById = remember { CatalogCache.getAllBooks().orEmpty().associateBy { it.id } }
    var sort by remember { mutableStateOf(NotesLibrarySort.BOOK) }
    val unknownBook = stringResource(Res.string.all_notes_unknown_book)
    val sortedNotes =
        remember(allNotes, booksById, sort, unknownBook) {
            when (sort) {
                NotesLibrarySort.BOOK ->
                    allNotes.sortedWith(
                        compareBy<BookUserNote> { booksById[it.bookId]?.title ?: unknownBook }
                            .thenByDescending { it.note.updatedAt },
                    )
                NotesLibrarySort.TIME -> allNotes.sortedByDescending { it.note.updatedAt }
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .width(680.dp)
                    .height(620.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(18.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(18.dp))
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.all_notes_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                SortButton(
                    text = stringResource(Res.string.all_notes_sort_book),
                    selected = sort == NotesLibrarySort.BOOK,
                    onClick = { sort = NotesLibrarySort.BOOK },
                )
                SortButton(
                    text = stringResource(Res.string.all_notes_sort_time),
                    selected = sort == NotesLibrarySort.TIME,
                    onClick = { sort = NotesLibrarySort.TIME },
                )
            }

            if (sortedNotes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.all_notes_empty),
                        color = JewelTheme.globalColors.text.info,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedNotes, key = { it.note.id }) { entry ->
                        val bookTitle = booksById[entry.bookId]?.title ?: unknownBook
                        NoteLibraryCard(
                            entry = entry,
                            bookTitle = bookTitle,
                            onClick = {
                                onOpenNote(entry.bookId, entry.note.lineId)
                                onDismiss()
                            },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.all_notes_close)) }
            }
        }
    }
}

@Composable
private fun SortButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        DefaultButton(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun NoteLibraryCard(
    entry: BookUserNote,
    bookTitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(10.dp))
                .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bookTitle, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            val time =
                NOTES_LIBRARY_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(entry.note.updatedAt).atZone(ZoneId.systemDefault()),
                )
            Text(time, color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
        }
        if (entry.note.quote.isNotBlank()) {
            Text(
                text = "״${entry.note.quote}״",
                color = JewelTheme.globalColors.text.info,
                fontStyle = FontStyle.Italic,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(entry.note.note, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(1.dp))
        Text(
            text = stringResource(Res.string.all_notes_open),
            color = JewelTheme.globalColors.text.info,
            fontSize = 11.sp,
        )
    }
}
