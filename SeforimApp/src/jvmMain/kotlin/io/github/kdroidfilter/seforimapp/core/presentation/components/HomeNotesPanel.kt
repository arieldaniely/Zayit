package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.PaneHeader
import io.github.kdroidfilter.seforimapp.features.notes.noteDestination
import io.github.kdroidfilter.seforimapp.features.notes.openNotesTab
import io.github.kdroidfilter.seforimapp.features.notes.rememberUserNotes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.PathIconKey
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notes_empty
import seforimapp.seforimapp.generated.resources.notes_pane
import seforimapp.seforimapp.generated.resources.notes_search_placeholder
import seforimapp.seforimapp.generated.resources.notes_show_all

private object NotesIconAnchor

private val NotesIcon = PathIconKey("icons/notes.svg", NotesIconAnchor::class.java)

/** All notes on Home, searchable by book, passage or note, with a link to the full notes page. */
@Composable
internal fun HomeNotesPanel(
    tabsViewModel: TabsViewModel,
    onHide: () -> Unit,
) {
    val userNotes = rememberUserNotes()
    var query by remember { mutableStateOf("") }
    val byBook = remember(userNotes, query) { userNotes.matching(query).groupBy { it.bookId }.toList() }
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        PaneHeader(label = stringResource(Res.string.notes_pane), onHide = onHide)
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            PopupSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(Res.string.notes_search_placeholder),
                focusRequester = focusRequester,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            byBook.forEach { (bookId, notes) ->
                item(key = "book-$bookId") { PopupSectionHeader(userNotes.bookTitles[bookId].orEmpty()) }
                items(notes, key = { "note-" + it.note.id }) { bookNote ->
                    PopupRow(
                        label = bookNote.note.note,
                        tabType = TabType.NOTES,
                        onClick = { tabsViewModel.openTab(noteDestination(bookNote)) },
                        onClose = null,
                    )
                }
            }
            if (byBook.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.notes_empty), fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                    }
                }
            }
        }
        PopupFooterRow(
            label = stringResource(Res.string.notes_show_all),
            shortcutHint = null,
            icon = NotesIcon,
            onClick = { openNotesTab(tabsViewModel) },
        )
    }
}
