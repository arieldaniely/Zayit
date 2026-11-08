package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.presentation.components.HorizontalDivider
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.*
import io.github.kdroidfilter.seforimapp.features.bookcontent.usecases.categoryAncestry
import io.github.kdroidfilter.seforimapp.features.search.domain.TocTree
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.*

@Composable
internal fun SearchScopeBreadcrumbBar(
    state: SearchUiState,
    navigation: NavigationState,
    tocTree: TocTree?,
    onCategorySelect: (Category) -> Unit,
    onBookSelect: (Book) -> Unit,
    onTocSelect: (TocEntry) -> Unit,
    isIslands: Boolean,
    onResetScopeFilters: () -> Unit = {},
    onOriginScopeChange: (SearchScope) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val origin = state.scopeOrigin
    val scoped = origin is SearchScope.Author || origin is SearchScope.Favorites
    val visibility by graph.libraryVisibility.selection.collectAsState()
    val favoritesRevision by graph.favoritesStore.revision.collectAsState()
    val scopeBooks by produceState<Set<Long>?>(null, origin, visibility, favoritesRevision) {
        value =
            withContext(Dispatchers.IO) {
                when (origin) {
                    is SearchScope.Author ->
                        graph.repository
                            .getAuthorDetails(origin.authorId)
                            ?.books
                            .orEmpty()
                            .map { it.id }
                            .toSet()
                    is SearchScope.Favorites ->
                        graph.favoritesStore
                            .query()
                            .filter { origin.folderId == null || origin.folderId in it.folderIds }
                            .map { it.bookId }
                            .toSet()
                    else -> null
                }
            }
    }
    val booksByCategory =
        remember(navigation.booksInCategory, scopeBooks, scoped) {
            val books = navigation.booksInCategory
            val inScope = if (scoped) books.filterTo(mutableSetOf()) { it.id in scopeBooks.orEmpty() } else books
            CatalogBreadcrumbChildrenProvider.groupBooks(inScope)
        }
    val scopeCategories =
        remember(booksByCategory, navigation.categoriesById, scoped) {
            if (!scoped) {
                null
            } else {
                booksByCategory.keys
                    .flatMap { categoryAncestry(it, navigation.categoriesById) }
                    .map { it.id }
                    .toSet()
            }
        }
    val roots = navigation.rootCategories.filter { scopeCategories == null || it.id in scopeCategories }
    val scopeLabel =
        stringResource(
            when {
                scoped -> Res.string.search_scope_kind_books
                state.globalExtended -> Res.string.history_search_all
                else -> Res.string.history_search_base
            },
        )
    val root = BreadcrumbNode.ChoiceNode("catalog:$scopeLabel", scopeLabel)
    val path =
        remember(root, state.scopeCategoryPath, state.scopeBook, state.scopeTocId, navigation.categoriesById, tocTree) {
            val book = state.scopeBook
            buildList {
                add(root)
                val categories =
                    book
                        ?.let { categoryAncestry(it.categoryId, navigation.categoriesById) }
                        ?.ifEmpty { state.scopeCategoryPath } ?: state.scopeCategoryPath
                addAll(categories.map(BreadcrumbNode::CategoryNode))
                if (book != null) {
                    add(BreadcrumbNode.BookNode(book))
                    val toc = buildTocPath(tocTree, state.scopeTocId)
                    val visibleToc = if (toc.size > 1 && toc.first().text == book.title) toc.drop(1) else toc
                    addAll(visibleToc.map(BreadcrumbNode::TocNode))
                }
            }
        }
    val repository = graph.repository
    val provider =
        remember(navigation.categoryChildren, booksByCategory, scopeCategories, repository) {
            CatalogBreadcrumbChildrenProvider(
                navigation.categoryChildren.mapValues { (_, categories) ->
                    categories.filter { scopeCategories == null || it.id in scopeCategories }
                },
                booksByCategory,
                repository::getBookRootToc,
                repository::getTocChildren,
            )
        }
    val wholeBook = stringResource(Res.string.search_scope_whole_book)
    val children: suspend (BreadcrumbNode) -> List<BreadcrumbNode> = { node ->
        when (node) {
            is BreadcrumbNode.ChoiceNode -> roots.map(BreadcrumbNode::CategoryNode)
            else -> provider.children(node)
        }
    }
    val currentChildren by rememberUpdatedState(children)
    val bookCatalog by rememberUpdatedState<(Book) -> List<BreadcrumbNode>> { book ->
        buildList {
            add(BreadcrumbNode.BookNode(book, wholeBook))
            booksByCategory[book.categoryId].orEmpty().filter { it.id != book.id }.mapTo(this, BreadcrumbNode::BookNode)
            navigation.categoryChildren[book.categoryId]
                .orEmpty()
                .filter { scopeCategories == null || it.id in scopeCategories }
                .mapTo(this, BreadcrumbNode::CategoryNode)
        }
    }
    val resetScope by rememberUpdatedState(onResetScopeFilters)
    val categorySelect by rememberUpdatedState(onCategorySelect)
    val bookSelect by rememberUpdatedState(onBookSelect)
    val tocSelect by rememberUpdatedState(onTocSelect)
    val scope = rememberCoroutineScope()
    val navigator =
        remember(scope) {
            BreadcrumbNavigator(
                scope = scope,
                childrenProvider = { currentChildren(it) },
                selectBranches = true,
                bookCatalog = { bookCatalog(it) },
                initialPath = path,
                onReturnFocus = {},
                onNavigate = {
                    when (it) {
                        is BreadcrumbNode.ChoiceNode -> resetScope()
                        is BreadcrumbNode.CategoryNode -> categorySelect(it.category)
                        is BreadcrumbNode.BookNode -> bookSelect(it.book)
                        is BreadcrumbNode.TocNode -> tocSelect(it.entry)
                    }
                },
            )
        }
    LaunchedEffect(navigator, path) { navigator.updatePath(path) }
    val card =
        if (isIslands) {
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 4.dp, end = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JewelTheme.globalColors.panelBackground)
        } else {
            Modifier.fillMaxWidth().background(JewelTheme.globalColors.panelBackground)
        }
    Column(card) {
        if (!isIslands) HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchScopeOriginControls(state.scopeOrigin, state.scopeOriginName, onOriginScopeChange)
            if (scoped) BreadcrumbSeparator()
            BreadcrumbBar(navigator, Modifier.weight(1f))
        }
    }
}

private fun buildTocPath(
    tocTree: TocTree?,
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
