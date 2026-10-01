package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.categorytree.SearchResultCategoryTreeView
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.filter_books

@Composable
internal fun LinkFilterSidebar(
    availableBookIds: Set<Long>,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onBookCheckedChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalogCache = LocalAppGraph.current.catalogCache
    var expandedCategoryIds by remember(availableBookIds) { mutableStateOf(emptySet<Long>()) }
    val tree = remember(availableBookIds) { buildLinkFilterTree(availableBookIds, catalogCache) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.filter_books),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        SearchResultCategoryTreeView(
            expandedCategoryIds = expandedCategoryIds,
            scrollIndex = 0,
            scrollOffset = 0,
            searchTree = tree,
            isFiltering = false,
            selectedCategoryIds = selectedCategoryIds,
            selectedBookIds = selectedBookIds,
            onCategoryRowClick = { category ->
                expandedCategoryIds =
                    if (category.id in expandedCategoryIds) {
                        expandedCategoryIds - category.id
                    } else {
                        expandedCategoryIds + category.id
                    }
            },
            onPersistScroll = { _, _ -> },
            onCategoryCheckedChange = onCategoryCheckedChange,
            onBookCheckedChange = onBookCheckedChange,
            onEnsureScopeBookForToc = {},
        )
    }
}

internal fun resolveLinkFilterBookIds(
    availableBookIds: Set<Long>,
    catalogCache: CatalogCache,
    selectedCategoryIds: Set<Long>,
    selectedBookIds: Set<Long>,
): Set<Long> {
    if (selectedCategoryIds.isEmpty() && selectedBookIds.isEmpty()) return availableBookIds
    val booksById = catalogCache.getAllBooks().orEmpty().associateBy { it.id }
    val categoriesById = catalogCache.getCategoriesById().orEmpty()

    return availableBookIds.filterTo(mutableSetOf()) { bookId ->
        if (bookId in selectedBookIds) return@filterTo true
        var categoryId = booksById[bookId]?.categoryId
        var safety = 64
        while (categoryId != null && safety-- > 0) {
            if (categoryId in selectedCategoryIds) return@filterTo true
            categoryId = categoriesById[categoryId]?.parentId
        }
        false
    }
}

private fun buildLinkFilterTree(availableBookIds: Set<Long>, catalogCache: CatalogCache): List<SearchResultViewModel.SearchTreeCategory> {
    if (availableBookIds.isEmpty()) return emptyList()
    val books = catalogCache.getAllBooks().orEmpty().filter { it.id in availableBookIds }
    val booksByCategory = books.groupBy { it.categoryId }
    val categoriesById = catalogCache.getCategoriesById().orEmpty()
    val children = catalogCache.getCategoryChildren().orEmpty()
    val includedCategoryIds = mutableSetOf<Long>()

    books.forEach { book ->
        var categoryId: Long? = book.categoryId
        var safety = 64
        while (categoryId != null && safety-- > 0) {
            if (!includedCategoryIds.add(categoryId)) break
            categoryId = categoriesById[categoryId]?.parentId
        }
    }

    fun build(categoryId: Long): SearchResultViewModel.SearchTreeCategory? {
        if (categoryId !in includedCategoryIds) return null
        val category = categoriesById[categoryId] ?: return null
        val childNodes = children[categoryId].orEmpty().mapNotNull { build(it.id) }
        val directBooks =
            booksByCategory[categoryId].orEmpty().map {
                SearchResultViewModel.SearchTreeBook(book = it, count = 1)
            }
        val count = directBooks.size + childNodes.sumOf { it.count }
        return SearchResultViewModel.SearchTreeCategory(
            category = category,
            count = count,
            children = childNodes,
            books = directBooks,
        )
    }

    return catalogCache.getRootCategories().orEmpty().mapNotNull { build(it.id) }
}
