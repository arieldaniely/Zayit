package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
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
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.core.presentation.components.SelectableRow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbItem
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel.SearchTreeCategory
import io.github.kdroidfilter.seforimapp.icons.Book_2
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.search_scope_catalog
import seforimapp.seforimapp.generated.resources.search_scope_sections

@Composable
internal fun SearchScopeBreadcrumbBar(
    state: SearchUiState,
    searchTree: List<SearchTreeCategory>,
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
    onCategorySelect: (Category) -> Unit,
    onBookSelect: (Book) -> Unit,
    onTocSelect: (TocEntry) -> Unit,
) {
    val path =
        remember(state.scopeCategoryPath, state.scopeBook, state.scopeTocId, searchTree, tocTree) {
            buildScopePath(state, searchTree, tocTree)
        }
    if (path.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground)) {
        HorizontalDivider()
        SearchScopePath(
            path = path,
            state = state,
            searchTree = searchTree,
            tocTree = tocTree,
            onCategorySelect = onCategorySelect,
            onBookSelect = onBookSelect,
            onTocSelect = onTocSelect,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
        )
    }
}

@Composable
private fun SearchScopePath(
    path: List<BreadcrumbItem>,
    state: SearchUiState,
    searchTree: List<SearchTreeCategory>,
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
    onCategorySelect: (Category) -> Unit,
    onBookSelect: (Book) -> Unit,
    onTocSelect: (TocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var openItemKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        scrollState.scrollTo(Int.MAX_VALUE)
        if (path.none { it.key == openItemKey }) openItemKey = null
    }

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { index, item ->
            if (index > 0) {
                Text(text = " > ", modifier = Modifier.padding(horizontal = 4.dp), fontSize = 12.sp)
            }
            Box {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = if (index == path.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    modifier =
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { openItemKey = item.key },
                )
                if (openItemKey == item.key) {
                    SearchScopePopup(
                        item = item,
                        state = state,
                        searchTree = searchTree,
                        tocTree = tocTree,
                        onCategorySelect = {
                            openItemKey = null
                            onCategorySelect(it)
                        },
                        onBookSelect = {
                            openItemKey = null
                            onBookSelect(it)
                        },
                        onTocSelect = {
                            openItemKey = null
                            onTocSelect(it)
                        },
                        onDismiss = { openItemKey = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScopePopup(
    item: BreadcrumbItem,
    state: SearchUiState,
    searchTree: List<SearchTreeCategory>,
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
    onCategorySelect: (Category) -> Unit,
    onBookSelect: (Book) -> Unit,
    onTocSelect: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = SearchScopeAboveAnchorPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            modifier =
                Modifier
                    .shadow(12.dp, shape)
                    .background(JewelTheme.globalColors.panelBackground, shape)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                    .padding(vertical = 6.dp),
        ) {
            when (item) {
                is BreadcrumbItem.CategoryItem ->
                    ScopePopupPane(title = stringResource(Res.string.search_scope_catalog)) {
                        CatalogScopeTree(
                            searchTree = searchTree,
                            state = state,
                            onCategorySelect = onCategorySelect,
                            onBookSelect = onBookSelect,
                        )
                    }

                is BreadcrumbItem.BookItem ->
                    Row(modifier = Modifier.widthIn(min = 420.dp, max = 560.dp)) {
                        ScopePopupPane(
                            title = stringResource(Res.string.search_scope_catalog),
                            modifier = Modifier.weight(1f),
                        ) {
                            CatalogScopeTree(
                                searchTree = searchTree,
                                state = state,
                                onCategorySelect = onCategorySelect,
                                onBookSelect = onBookSelect,
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .heightIn(min = 200.dp, max = 300.dp)
                                .background(JewelTheme.globalColors.borders.normal),
                        )
                        ScopePopupPane(
                            title = stringResource(Res.string.search_scope_sections),
                            modifier = Modifier.weight(1f),
                        ) {
                            TocScopeTree(
                                tocTree = tocTree,
                                selectedTocId = state.scopeTocId,
                                onTocSelect = onTocSelect,
                            )
                        }
                    }

                is BreadcrumbItem.TocItem ->
                    ScopePopupPane(title = stringResource(Res.string.search_scope_sections)) {
                        TocScopeTree(
                            tocTree = tocTree,
                            selectedTocId = state.scopeTocId,
                            onTocSelect = onTocSelect,
                        )
                    }
            }
        }
    }
}

@Composable
private fun ScopePopupPane(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.widthIn(min = 200.dp, max = 280.dp)) {
        Text(
            text = title,
            color = JewelTheme.globalColors.text.disabledSelected,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
        content()
    }
}

private sealed interface CatalogScopeRow {
    val level: Int

    data class CategoryRow(
        val node: SearchTreeCategory,
        override val level: Int,
    ) : CatalogScopeRow

    data class BookRow(
        val book: Book,
        override val level: Int,
    ) : CatalogScopeRow
}

@Composable
private fun CatalogScopeTree(
    searchTree: List<SearchTreeCategory>,
    state: SearchUiState,
    onCategorySelect: (Category) -> Unit,
    onBookSelect: (Book) -> Unit,
) {
    val selectedCategoryId = state.scopeCategoryPath.lastOrNull()?.id
    val initialExpanded =
        remember(searchTree, selectedCategoryId, state.scopeBook?.categoryId) {
            val targetId = state.scopeBook?.categoryId ?: selectedCategoryId
            targetId?.let { findCategoryPath(searchTree, it).mapTo(mutableSetOf()) { category -> category.id } }
                ?: emptySet()
        }
    var expanded by remember(searchTree, initialExpanded) { mutableStateOf(initialExpanded) }
    val rows = remember(searchTree, expanded) { buildCatalogRows(searchTree, expanded) }

    LazyColumn(modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).heightIn(max = 260.dp)) {
        items(
            items = rows,
            key = {
                when (it) {
                    is CatalogScopeRow.CategoryRow -> "category_${it.node.category.id}"
                    is CatalogScopeRow.BookRow -> "book_${it.book.id}"
                }
            },
        ) { row ->
            when (row) {
                is CatalogScopeRow.CategoryRow -> {
                    val hasChildren = row.node.children.isNotEmpty() || row.node.books.isNotEmpty()
                    SelectableRow(
                        isSelected = row.node.category.id == selectedCategoryId,
                        onClick = { onCategorySelect(row.node.category) },
                        modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
                    ) {
                        if (hasChildren) {
                            ChevronIcon(
                                expanded = row.node.category.id in expanded,
                                contentDescription = "",
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clickable {
                                            expanded =
                                                if (row.node.category.id in expanded) {
                                                    expanded - row.node.category.id
                                                } else {
                                                    expanded + row.node.category.id
                                                }
                                        },
                                tint = JewelTheme.globalColors.text.normal,
                            )
                        } else {
                            Spacer(Modifier.width(18.dp))
                        }
                        Icon(key = AllIconsKeys.Nodes.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(row.node.category.title, fontSize = 12.sp)
                    }
                }

                is CatalogScopeRow.BookRow ->
                    SelectableRow(
                        isSelected = row.book.id == state.scopeBook?.id,
                        onClick = { onBookSelect(row.book) },
                        modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
                    ) {
                        Spacer(Modifier.width(18.dp))
                        Icon(imageVector = Book_2, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(row.book.title, fontSize = 12.sp)
                    }
            }
        }
    }
}

private data class TocScopeRow(
    val entry: TocEntry,
    val level: Int,
)

@Composable
private fun TocScopeTree(
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
    selectedTocId: Long?,
    onTocSelect: (TocEntry) -> Unit,
) {
    if (tocTree == null) return
    val initialExpanded = remember(tocTree, selectedTocId) { tocAncestorIds(tocTree, selectedTocId) }
    var expanded by remember(tocTree, initialExpanded) { mutableStateOf(initialExpanded) }
    val rows = remember(tocTree, expanded) { buildTocRows(tocTree, expanded) }

    LazyColumn(modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).heightIn(max = 260.dp)) {
        items(rows, key = { it.entry.id }) { row ->
            val hasChildren = tocTree.children[row.entry.id].orEmpty().isNotEmpty()
            SelectableRow(
                isSelected = row.entry.id == selectedTocId,
                onClick = { onTocSelect(row.entry) },
                modifier = Modifier.fillMaxWidth().padding(start = (row.level * 16).dp),
            ) {
                if (hasChildren) {
                    ChevronIcon(
                        expanded = row.entry.id in expanded,
                        contentDescription = "",
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clickable {
                                    expanded =
                                        if (row.entry.id in expanded) {
                                            expanded - row.entry.id
                                        } else {
                                            expanded + row.entry.id
                                        }
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

private fun buildScopePath(
    state: SearchUiState,
    searchTree: List<SearchTreeCategory>,
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
): List<BreadcrumbItem> {
    val book = state.scopeBook
    if (book == null) return state.scopeCategoryPath.map { BreadcrumbItem.CategoryItem(it) }

    val categories = findCategoryPath(searchTree, book.categoryId).ifEmpty { state.scopeCategoryPath }
    return buildList {
        categories.forEach { add(BreadcrumbItem.CategoryItem(it)) }
        add(BreadcrumbItem.BookItem(book))
        val tocPath = buildTocPath(tocTree, state.scopeTocId)
        val adjusted = if (tocPath.firstOrNull()?.text == book.title) tocPath.drop(1) else tocPath
        adjusted.forEach { add(BreadcrumbItem.TocItem(it)) }
    }
}

private fun findCategoryPath(
    nodes: List<SearchTreeCategory>,
    categoryId: Long,
): List<Category> {
    for (node in nodes) {
        if (node.category.id == categoryId) return listOf(node.category)
        val childPath = findCategoryPath(node.children, categoryId)
        if (childPath.isNotEmpty()) return listOf(node.category) + childPath
    }
    return emptyList()
}

private fun buildCatalogRows(
    roots: List<SearchTreeCategory>,
    expanded: Set<Long>,
): List<CatalogScopeRow> =
    buildList {
        fun addNode(
            node: SearchTreeCategory,
            level: Int,
        ) {
            add(CatalogScopeRow.CategoryRow(node, level))
            if (node.category.id in expanded) {
                node.books.forEach { add(CatalogScopeRow.BookRow(it.book, level + 1)) }
                node.children.forEach { addNode(it, level + 1) }
            }
        }
        roots.forEach { addNode(it, 0) }
    }

private fun buildTocPath(
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree?,
    selectedTocId: Long?,
): List<TocEntry> {
    if (tocTree == null || selectedTocId == null) return emptyList()
    val entries = (tocTree.rootEntries + tocTree.children.values.flatten()).associateBy { it.id }
    return buildList {
        var current = entries[selectedTocId]
        while (current != null) {
            add(current)
            current = current.parentId?.let(entries::get)
        }
    }.asReversed()
}

private fun tocAncestorIds(
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree,
    selectedTocId: Long?,
): Set<Long> = buildTocPath(tocTree, selectedTocId).dropLast(1).mapTo(mutableSetOf()) { it.id }

private fun buildTocRows(
    tocTree: io.github.kdroidfilter.seforimapp.features.search.domain.TocTree,
    expanded: Set<Long>,
): List<TocScopeRow> =
    buildList {
        fun addEntry(
            entry: TocEntry,
            level: Int,
        ) {
            add(TocScopeRow(entry, level))
            if (entry.id in expanded) {
                tocTree.children[entry.id].orEmpty().forEach { addEntry(it, level + 1) }
            }
        }
        tocTree.rootEntries.forEach { addEntry(it, 0) }
    }

private object SearchScopeAboveAnchorPositionProvider : PopupPositionProvider {
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
