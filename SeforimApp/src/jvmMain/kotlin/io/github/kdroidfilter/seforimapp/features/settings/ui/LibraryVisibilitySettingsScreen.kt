package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryEntryKind
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibility
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibilityEntry
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibilitySelection
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibilitySnapshot
import io.github.kdroidfilter.seforimapp.core.catalog.filterLibraryEntries
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import seforimapp.seforimapp.generated.resources.*

@Composable
fun LibraryVisibilitySettingsScreen() {
    LibraryVisibilitySettingsView(LocalAppGraph.current.libraryVisibility)
}

@Composable
internal fun LibraryVisibilitySettingsView(visibility: LibraryVisibility) {
    val revision by CatalogCache.revision.collectAsState()
    val selection by visibility.selection.collectAsState()
    val entries by produceState<List<LibraryVisibilityEntry>?>(null, visibility, revision) {
        value = withContext(Dispatchers.IO) { visibility.entries() }
    }
    val snapshot by produceState(LibraryVisibilitySnapshot(), visibility, selection, revision) {
        value = withContext(Dispatchers.IO) { visibility.snapshot() }
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val save: (suspend () -> Unit) -> Unit = { change ->
        if (!saving) {
            saving = true
            scope.launch {
                saveFailed = runSuspendCatching { change() }.isFailure
                saving = false
            }
        }
    }
    LibraryVisibilityControls(
        entries = entries,
        selection = selection,
        snapshot = snapshot,
        saving = saving,
        saveFailed = saveFailed,
        onVisibilityChange = { kind, ids, hidden -> save { visibility.setHidden(kind, ids, hidden) } },
        onRestoreAll = { save { visibility.restoreAll() } },
    )
}

@Composable
private fun LibraryVisibilityControls(
    entries: List<LibraryVisibilityEntry>?,
    selection: LibraryVisibilitySelection,
    snapshot: LibraryVisibilitySnapshot,
    saving: Boolean,
    saveFailed: Boolean,
    onVisibilityChange: (LibraryEntryKind, Collection<Long>, Boolean) -> Unit,
    onRestoreAll: () -> Unit,
) {
    var kind by remember { mutableStateOf(LibraryEntryKind.BOOK) }
    var hiddenOnly by remember { mutableStateOf(false) }
    val name = rememberTextFieldState()
    val author = rememberTextFieldState()
    val nameQuery = name.text.toString()
    val authorQuery = author.text.toString()
    val directlyHidden =
        when (kind) {
            LibraryEntryKind.BOOK -> selection.bookIds
            LibraryEntryKind.CATEGORY -> selection.categoryIds
            LibraryEntryKind.AUTHOR -> selection.authorIds
        }
    val effectivelyHidden =
        when (kind) {
            LibraryEntryKind.BOOK -> snapshot.hiddenBookIds
            LibraryEntryKind.CATEGORY -> snapshot.hiddenCategoryIds
            LibraryEntryKind.AUTHOR -> snapshot.hiddenAuthorIds
        }
    val matches = remember(entries, kind, nameQuery, authorQuery) { filterLibraryEntries(entries.orEmpty(), kind, nameQuery, authorQuery) }
    val rows =
        remember(matches, hiddenOnly, directlyHidden, effectivelyHidden) {
            if (hiddenOnly) matches.filter { it.id in effectivelyHidden || it.id in directlyHidden } else matches
        }
    val narrowed = nameQuery.isNotBlank() || (kind == LibraryEntryKind.BOOK && authorQuery.isNotBlank())
    val listState = rememberLazyListState()
    val nameLabel = stringResource(Res.string.settings_library_visibility_name_filter)
    val authorLabel = stringResource(Res.string.settings_library_visibility_author_filter)
    LaunchedEffect(kind, nameQuery, authorQuery, hiddenOnly) {
        if (rows.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.settings_library_visibility_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.settings_library_visibility_description), color = JewelTheme.globalColors.text.info)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val kinds = listOf(LibraryEntryKind.BOOK, LibraryEntryKind.CATEGORY, LibraryEntryKind.AUTHOR)
            ListComboBox(
                items =
                    listOf(
                        stringResource(Res.string.settings_library_visibility_books),
                        stringResource(Res.string.settings_library_visibility_categories),
                        stringResource(Res.string.search_scope_kind_authors),
                    ),
                selectedIndex = kinds.indexOf(kind),
                onSelectedItemChange = { kind = kinds[it] },
                modifier = Modifier.width(124.dp),
            )
            TextField(
                state = name,
                placeholder = { Text(nameLabel) },
                modifier = Modifier.weight(1f).semantics { contentDescription = nameLabel },
            )
            if (kind == LibraryEntryKind.BOOK) {
                TextField(
                    state = author,
                    placeholder = { Text(authorLabel) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = authorLabel },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = hiddenOnly, onCheckedChange = { hiddenOnly = it })
            Text(
                stringResource(Res.string.settings_library_visibility_hidden_only),
                modifier = Modifier.clickable { hiddenOnly = !hiddenOnly },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onRestoreAll, enabled = !saving && !selection.isEmpty) {
                Text(stringResource(Res.string.settings_library_visibility_restore_all))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.settings_library_visibility_count, rows.size), modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    onVisibilityChange(kind, rows.filterNot { it.id in effectivelyHidden || it.id in directlyHidden }.map { it.id }, true)
                },
                enabled = !saving && narrowed && rows.any { it.id !in effectivelyHidden && it.id !in directlyHidden },
            ) {
                Text(stringResource(Res.string.settings_library_visibility_hide_results))
            }
            OutlinedButton(
                onClick = { onVisibilityChange(kind, rows.filter { it.id in directlyHidden }.map { it.id }, false) },
                enabled = !saving && (narrowed || hiddenOnly) && rows.any { it.id in directlyHidden },
            ) {
                Text(stringResource(Res.string.settings_library_visibility_show_results))
            }
        }
        if (saveFailed) InlineErrorBanner(text = stringResource(Res.string.settings_library_visibility_error))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                entries == null -> CircularProgressIndicator()
                rows.isEmpty() -> Text(stringResource(Res.string.settings_library_visibility_empty))
                else ->
                    VerticallyScrollableContainer(scrollState = listState as ScrollableState, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = scrollbarContentSafePadding()),
                        ) {
                            items(rows, key = { "${it.kind}:${it.id}" }) { entry ->
                                val direct = entry.id in directlyHidden
                                val hidden = direct || entry.id in effectivelyHidden
                                LibraryVisibilityRow(entry, hidden, hidden && !direct, saving) { checked ->
                                    onVisibilityChange(entry.kind, listOf(entry.id), checked)
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun LibraryVisibilityRow(
    entry: LibraryVisibilityEntry,
    hidden: Boolean,
    inherited: Boolean,
    saving: Boolean,
    onHiddenChange: (Boolean) -> Unit,
) {
    val enabled = !inherited && !saving
    val details = listOf(entry.authors.joinToString(", "), entry.path.joinToString(" / ")).filter(String::isNotBlank).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onHiddenChange(!hidden) }.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = hidden, enabled = enabled, onCheckedChange = onHiddenChange)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.title,
                fontWeight = FontWeight.Medium,
                color = if (hidden) JewelTheme.globalColors.text.info else JewelTheme.globalColors.text.normal,
            )
            if (details.isNotBlank()) {
                Text(details, color = JewelTheme.globalColors.text.info, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (inherited) {
                Text(
                    stringResource(
                        if (entry.kind == LibraryEntryKind.BOOK) {
                            Res.string.settings_library_visibility_book_inherited
                        } else {
                            Res.string.settings_library_visibility_empty_group
                        },
                    ),
                    color = JewelTheme.globalColors.text.info,
                )
            }
        }
    }
}
