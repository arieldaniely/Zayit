package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Optional Dialog wrapper for [NotesLibraryPanel] if needed as a modal dialog.
 */
@Composable
fun NotesLibraryDialog(
    noteStore: NoteStore,
    onOpenNote: (bookId: Long, lineId: Long) -> Unit,
    onDismiss: () -> Unit,
    repository: SeforimRepository = LocalAppGraph.current.repository,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .width(700.dp)
                    .height(620.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                    .padding(8.dp),
        ) {
            NotesLibraryPanel(
                noteStore = noteStore,
                onOpenNote = { bookId, lineId ->
                    onOpenNote(bookId, lineId)
                    onDismiss()
                },
                onHide = onDismiss,
                repository = repository,
            )
        }
    }
}
