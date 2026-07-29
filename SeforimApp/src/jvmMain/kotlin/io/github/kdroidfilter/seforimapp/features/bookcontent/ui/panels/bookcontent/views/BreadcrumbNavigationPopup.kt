package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforimapp.core.presentation.components.ChevronIcon
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentEvent
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.icons.Book_2
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private sealed interface CatalogPopupRow {
    val level: Int

    data class CategoryRow(
        val category: Category,
        override val level: Int,
    ) : CatalogPopupRow

    data class BookRow(
        val book: Book,
        override val level: Int,
    ) : CatalogPopupRow
}

private data class TocPopupRow(
    val entry: TocEntry,
    val level: Int,
)

@Composable
fun BreadcrumbNavigationPopup(
    uiState: BookContentState,
    item: BreadcrumbItem,
    onEvent: (BookContentEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = BreadcrumbAboveAnchorPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(min = 180.dp, max = 280.dp)
                    .heightIn(max = 300.dp)
                    .shadow(12.dp, RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
        ) {
            when (item) {
                is BreadcrumbItem.CategoryItem,
                is BreadcrumbItem.BookItem,
                -> CatalogBreadcrumbTree(uiState, item, onEvent, onDismiss)

                is BreadcrumbItem.TocItem -> TocBreadcrumbTree(uiState, item.tocEntry, onEvent, onDismiss)
            }
        }
    }
}

@Composable
private fun CatalogBreadcrumbTree(
    uiState: BookContentState,
    item: BreadcrumbItem,
    onEvent: (BookContentEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val navigation = uiState.navigation
    val activeCategory = (item as? BreadcrumbItem.CategoryItem)?.category
    var expanded by remember(item.key) { mutableStateOf(emptySet<Long>()) }

    val rows =
        remember(item.key, expanded, navigation.rootCategories, navigation.categoryChildren, navigation.booksInCategory) {
            when (item) {
                is BreadcrumbItem.CategoryItem -> {
                    val siblings =
                        item.category.parentId?.let { navigation.categoryChildren[it].orEmpty() }
                            ?: navigation.rootCategories
                    buildCatalogRows(
                        rootCategories = siblings,
                        rootBooks = emptyList(),
                        expanded = expanded,
                        children = navigation.categoryChildren,
                        books = navigation.booksInCategory,
                    )
                }

                is BreadcrumbItem.BookItem -> {
                    val categoryId = item.book.categoryId
                    buildCatalogRows(
                        rootCategories = navigation.categoryChildren[categoryId].orEmpty(),
                        rootBooks = navigation.booksInCategory.filter { it.categoryId == categoryId },
                        expanded = expanded,
                        children = navigation.categoryChildren,
                        books = navigation.booksInCategory,
                    )
                }

                is BreadcrumbItem.TocItem -> emptyList()
            }
        }

    LazyColumn(modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)) {
        items(
            items = rows,
            key = {
                when (it) {
                    is CatalogPopupRow.CategoryRow -> "category_${it.category.id}"
                    is CatalogPopupRow.BookRow -> "book_${it.book.id}"
                }
            },
        ) { row ->
            when (row) {
                is CatalogPopupRow.CategoryRow ->
                    CatalogCategoryRow(
                        row = row,
                        expanded = row.category.id in expanded,
                        selected = row.category.id == activeCategory?.id,
                        onClick = {
                            expanded =
                                if (row.category.id in expanded) {
                                    expanded -
                                        row.category.id -
                                        categoryDescendantIds(row.category.id, navigation.categoryChildren)
                                } else {
                                    expanded + row.category.id
                                }
                        },
                    )

                is CatalogPopupRow.BookRow ->
                    CatalogBookRow(
                        row = row,
                        selected = row.book.id == navigation.selectedBook?.id,
                        onClick = {
                            onDismiss()
                            onEvent(BookContentEvent.BookSelected(row.book))
                        },
                    )
            }
        }
    }
}

@Composable
private fun CatalogCategoryRow(
    row: CatalogPopupRow.CategoryRow,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableRow(
        isSelected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
    ) {
        ChevronIcon(
            expanded = expanded,
            contentDescription = "",
            modifier = Modifier.size(18.dp),
            tint = JewelTheme.globalColors.text.normal,
        )
        Icon(
            key = AllIconsKeys.Nodes.Folder,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(row.category.title, fontSize = 12.sp)
    }
}

@Composable
private fun CatalogBookRow(
    row: CatalogPopupRow.BookRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableRow(
        isSelected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
    ) {
        Spacer(Modifier.width(18.dp))
        Icon(imageVector = Book_2, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(row.book.title, fontSize = 12.sp)
    }
}

@Composable
private fun TocBreadcrumbTree(
    uiState: BookContentState,
    activeEntry: TocEntry,
    onEvent: (BookContentEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val toc = uiState.toc
    var expanded by remember(activeEntry.id) { mutableStateOf(emptySet<Long>()) }
    val siblings =
        remember(activeEntry.id, toc.entries, toc.children) {
            toc.children[activeEntry.parentId ?: -1L].orEmpty().ifEmpty { toc.entries }
        }
    val rows = remember(siblings, expanded, toc.children) { buildTocRows(siblings, expanded, toc.children) }

    LazyColumn(modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)) {
        items(rows, key = { it.entry.id }) { row ->
            val selected = row.entry.id == toc.selectedEntryId || row.entry.id == activeEntry.id
            SelectableRow(
                isSelected = selected,
                onClick = {
                    val lineId = row.entry.lineId
                    if (lineId != null) {
                        onDismiss()
                        onEvent(BookContentEvent.LoadAndSelectLine(lineId))
                    } else if (row.entry.hasChildren) {
                        expanded = toggleTocExpansion(row.entry, expanded, toc.children)
                        requestTocChildrenIfNeeded(row.entry, toc.expandedEntries, toc.children, onEvent)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
            ) {
                if (row.entry.hasChildren) {
                    ChevronIcon(
                        expanded = row.entry.id in expanded,
                        contentDescription = "",
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clickable {
                                    expanded = toggleTocExpansion(row.entry, expanded, toc.children)
                                    requestTocChildrenIfNeeded(
                                        row.entry,
                                        toc.expandedEntries,
                                        toc.children,
                                        onEvent,
                                    )
                                },
                        tint = JewelTheme.globalColors.text.normal,
                    )
                } else {
                    Spacer(Modifier.width(18.dp))
                }
                Text(row.entry.text, fontSize = 12.sp)
            }
        }
    }
}

private fun requestTocChildrenIfNeeded(
    entry: TocEntry,
    globallyExpanded: Set<Long>,
    children: Map<Long, List<TocEntry>>,
    onEvent: (BookContentEvent) -> Unit,
) {
    if (entry.id !in globallyExpanded && children[entry.id] == null) {
        onEvent(BookContentEvent.TocEntryExpanded(entry))
    }
}

private fun buildCatalogRows(
    rootCategories: List<Category>,
    rootBooks: List<Book>,
    expanded: Set<Long>,
    children: Map<Long, List<Category>>,
    books: Set<Book>,
): List<CatalogPopupRow> =
    buildList {
        rootBooks.distinctBy { it.id }.forEach { add(CatalogPopupRow.BookRow(it, 0)) }

        fun addCategory(
            category: Category,
            level: Int,
        ) {
            add(CatalogPopupRow.CategoryRow(category, level))
            if (category.id in expanded) {
                books
                    .filter { it.categoryId == category.id }
                    .distinctBy { it.id }
                    .forEach { add(CatalogPopupRow.BookRow(it, level + 1)) }
                children[category.id].orEmpty().forEach { addCategory(it, level + 1) }
            }
        }

        rootCategories.forEach { addCategory(it, 0) }
    }

private fun buildTocRows(
    roots: List<TocEntry>,
    expanded: Set<Long>,
    children: Map<Long, List<TocEntry>>,
): List<TocPopupRow> =
    buildList {
        fun addEntry(
            entry: TocEntry,
            level: Int,
        ) {
            add(TocPopupRow(entry, level))
            if (entry.id in expanded) children[entry.id].orEmpty().forEach { addEntry(it, level + 1) }
        }
        roots.forEach { addEntry(it, 0) }
    }

private fun toggleTocExpansion(
    entry: TocEntry,
    expanded: Set<Long>,
    children: Map<Long, List<TocEntry>>,
): Set<Long> =
    if (entry.id in expanded) {
        expanded - entry.id - tocDescendantIds(entry.id, children)
    } else {
        expanded + entry.id
    }

private fun categoryDescendantIds(
    id: Long,
    children: Map<Long, List<Category>>,
): Set<Long> =
    buildSet {
        children[id].orEmpty().forEach {
            add(it.id)
            addAll(categoryDescendantIds(it.id, children))
        }
    }

private fun tocDescendantIds(
    id: Long,
    children: Map<Long, List<TocEntry>>,
): Set<Long> =
    buildSet {
        children[id].orEmpty().forEach {
            add(it.id)
            addAll(tocDescendantIds(it.id, children))
        }
    }

private object BreadcrumbAboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val desiredX =
            if (layoutDirection == LayoutDirection.Rtl) {
                anchorBounds.right - popupContentSize.width
            } else {
                anchorBounds.left
            }
        val desiredY = anchorBounds.top - popupContentSize.height - 4
        return IntOffset(
            x = desiredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = desiredY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
