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
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.TocTree
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
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
) {
    val path =
        remember(state.scopeCategoryPath, state.scopeBook, state.scopeTocId, navigation.categoriesById, tocTree) {
            val book = state.scopeBook
            buildList {
                val categories =
                    book
                        ?.let { categoryAncestry(it.categoryId, navigation.categoriesById) }
                        ?.ifEmpty { state.scopeCategoryPath } ?: state.scopeCategoryPath
                addAll(categories.map(BreadcrumbNode::CategoryNode))
                if (book != null) {
                    add(BreadcrumbNode.BookNode(book))
                    val toc = buildTocPath(tocTree, state.scopeTocId)
                    addAll((if (toc.firstOrNull()?.text == book.title) toc.drop(1) else toc).map(BreadcrumbNode::TocNode))
                }
            }
        }
    val originLabel =
        when (state.scopeOrigin) {
            is SearchScope.Author ->
                state.scopeOriginName?.let { stringResource(Res.string.search_scope_author, AuthorNames.display(it)) }
                    ?: stringResource(Res.string.search_scope_author_loading)
            is SearchScope.Favorites ->
                listOfNotNull(stringResource(Res.string.favorites_title), state.scopeOriginName)
                    .joinToString(stringResource(Res.string.breadcrumb_separator))
            else -> null
        }
    if (path.isEmpty() && originLabel == null) return
    val repository = LocalAppGraph.current.repository
    val booksByCategory = remember(navigation.booksInCategory) { CatalogBreadcrumbChildrenProvider.groupBooks(navigation.booksInCategory) }
    val provider =
        remember(navigation.categoryChildren, booksByCategory, repository) {
            CatalogBreadcrumbChildrenProvider(
                navigation.categoryChildren,
                booksByCategory,
                repository::getBookRootToc,
                repository::getTocChildren,
            )
        }
    val currentProvider by rememberUpdatedState(provider)
    val categorySelect by rememberUpdatedState(onCategorySelect)
    val bookSelect by rememberUpdatedState(onBookSelect)
    val tocSelect by rememberUpdatedState(onTocSelect)
    val scope = rememberCoroutineScope()
    val navigator =
        remember(scope) {
            BreadcrumbNavigator(
                scope = scope,
                childrenProvider = { currentProvider.children(it) },
                initialPath = path,
                onReturnFocus = {},
                onNavigate = {
                    when (it) {
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
        if (originLabel != null) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Link(originLabel, onClick = onResetScopeFilters)
                if (path.isNotEmpty()) {
                    Text(stringResource(Res.string.breadcrumb_separator))
                    BreadcrumbBar(navigator, Modifier.weight(1f))
                }
            }
        } else {
            BreadcrumbBar(navigator, Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp))
        }
    }
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
