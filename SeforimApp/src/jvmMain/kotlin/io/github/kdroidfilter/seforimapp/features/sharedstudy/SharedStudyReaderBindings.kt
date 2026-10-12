package io.github.kdroidfilter.seforimapp.features.sharedstudy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.shared_study
import java.util.UUID

/** Adapt remote background tabs to the upstream workspace, which owns selection and tear-off. */
@Composable
internal fun SharedStudyWindowBindings(
    tabs: TabsViewModel,
    active: Boolean,
) {
    val graph = LocalAppGraph.current
    val coordinator = graph.sharedStudyCoordinator
    val state by coordinator.state.collectAsState()
    val tabsState by tabs.state.collectAsState()
    val autoOpened = remember(state.sessionId) { mutableMapOf<String, Long>() }
    val dismissed = remember(state.sessionId) { mutableSetOf<Long>() }
    LaunchedEffect(tabsState.tabs, state.locations, state.sessionId, active) {
        if (!active || !state.isConnected) return@LaunchedEffect
        val openIds = tabsState.tabs.map { it.destination.tabId }.toSet()
        val closed = autoOpened.keys.filter { it !in openIds }
        closed.forEach { id -> autoOpened.remove(id)?.let(dismissed::add) }
        val openBooks = tabsState.tabs.mapNotNull { (it.destination as? TabsDestination.BookContent)?.bookId }.toMutableSet()
        for ((participantId, location) in state.locations) {
            if (participantId == coordinator.localParticipantId || location.bookId in dismissed || location.bookId in openBooks) continue
            if (graph.repository.getBookCore(location.bookId) == null) continue
            val tabId = UUID.randomUUID().toString()
            tabs.openBackgroundTab(TabsDestination.BookContent(location.bookId, tabId, location.lineId))
            autoOpened[tabId] = location.bookId
            openBooks += location.bookId
        }
    }
}

@Composable
internal fun SharedStudyReaderBar(
    state: BookContentState,
    selected: Boolean,
    onEvent: (BookContentEvent) -> Unit,
) {
    val coordinator = LocalAppGraph.current.sharedStudyCoordinator
    val study by coordinator.state.collectAsState()
    val book = state.navigation.selectedBook ?: return
    val lineId = state.content.primarySelectedLineId
    LaunchedEffect(book.id, lineId, selected, study.sessionId) {
        if (selected && lineId != null && study.isConnected) coordinator.publishLocation(StudyLocation(book.id, lineId))
    }
    val remote = study.locations.filter { (id, location) -> id != coordinator.localParticipantId && location.bookId == book.id }
    if (remote.isNotEmpty()) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.shared_study))
            remote.forEach { (id, location) ->
                val participant = study.participants.firstOrNull { it.id == id }
                if (participant != null) {
                    OutlinedButton(onClick = { onEvent(BookContentEvent.LoadAndSelectLine(location.lineId)) }) {
                        Text(participant.displayName)
                    }
                }
            }
        }
    }
}
