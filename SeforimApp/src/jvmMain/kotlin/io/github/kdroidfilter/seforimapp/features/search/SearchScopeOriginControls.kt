package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.features.author.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbNode.ChoiceNode
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbPopup
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbPopupModel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbSegment
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BreadcrumbSeparator
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Bookmark
import io.github.kdroidfilter.seforimapp.icons.Feather
import io.github.kdroidfilter.seforimapp.icons.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Icon
import seforimapp.seforimapp.generated.resources.*

private data class ScopeAuthor(
    val id: Long,
    val name: String,
    val era: String?,
)

private enum class ScopePopup { Kind, Era, Author, Favorites }

@Composable
internal fun SearchScopeOriginControls(
    origin: SearchScope,
    originName: String?,
    onChange: (SearchScope) -> Unit,
) {
    val graph = LocalAppGraph.current
    val visibility by graph.libraryVisibility.selection.collectAsState()
    val favoritesRevision by graph.favoritesStore.revision.collectAsState()
    var anchor by remember { mutableStateOf<ScopePopup?>(null) }
    var popup by remember { mutableStateOf(ScopePopup.Kind) }
    var era by remember { mutableStateOf<String?>(null) }
    val showAuthors = origin is SearchScope.Author || popup == ScopePopup.Era || popup == ScopePopup.Author
    val authors by produceState(emptyList<ScopeAuthor>(), showAuthors, visibility) {
        if (!showAuthors) return@produceState
        value =
            withContext(Dispatchers.IO) {
                val snapshot = graph.libraryVisibility.snapshot()
                val eras = graph.libraryVisibility.authors().associate { it.id to it.era }
                graph.repository
                    .getAuthorsForLookup()
                    .filter { snapshot.isAuthorVisible(it.id) }
                    .map { ScopeAuthor(it.id, AuthorNames.display(it.name), eras[it.id]) }
            }
    }
    val showFolders = anchor != null && popup == ScopePopup.Favorites
    val folders by produceState(emptyList<FavoriteFolder>(), showFolders, favoritesRevision) {
        if (showFolders) value = graph.favoritesStore.folders()
    }
    val selectedAuthor = (origin as? SearchScope.Author)?.let { selected -> authors.firstOrNull { it.id == selected.authorId } }
    val currentFolder = (origin as? SearchScope.Favorites)?.folderId
    val booksLabel = stringResource(Res.string.search_scope_kind_books)
    val authorsLabel = stringResource(Res.string.search_scope_kind_authors)
    val favoritesLabel = stringResource(Res.string.search_scope_kind_favorites)
    val unknownEra = stringResource(Res.string.search_scope_era_unknown)
    val allFavorites = stringResource(Res.string.favorites_title)
    val (kind, icon) =
        when (origin) {
            is SearchScope.Author -> authorsLabel to Feather
            is SearchScope.Favorites -> favoritesLabel to Bookmark
            else -> booksLabel to Library
        }

    fun open(part: ScopePopup) {
        anchor = part
        popup = part
    }

    fun choose(scope: SearchScope) {
        anchor = null
        onChange(scope)
    }

    val choices =
        when (popup) {
            ScopePopup.Kind ->
                listOf(
                    ChoiceNode("books", booksLabel, Library),
                    ChoiceNode("authors", authorsLabel, Feather, hasChildren = true),
                    ChoiceNode("favorites", favoritesLabel, Bookmark, hasChildren = true),
                )
            ScopePopup.Era ->
                authors
                    .map { it.era?.takeIf(AUTHOR_ERAS::containsKey) }
                    .distinct()
                    .sortedBy { code -> AUTHOR_ERAS.keys.indexOf(code).takeIf { it >= 0 } ?: Int.MAX_VALUE }
                    .map { ChoiceNode(it.orEmpty(), AUTHOR_ERAS[it] ?: unknownEra, hasChildren = true) }
            ScopePopup.Author ->
                authors
                    .filter { it.era?.takeIf(AUTHOR_ERAS::containsKey) == era }
                    .sortedBy { it.name }
                    .map { ChoiceNode(it.id.toString(), it.name, Feather) }
            ScopePopup.Favorites ->
                listOf(ChoiceNode("", allFavorites, Bookmark)) + folders.map { ChoiceNode(it.id.toString(), it.name, Bookmark) }
        }
    val selectedKey =
        when (popup) {
            ScopePopup.Kind ->
                when (origin) {
                    is SearchScope.Author -> "authors"
                    is SearchScope.Favorites -> "favorites"
                    else -> "books"
                }
            ScopePopup.Era -> selectedAuthor?.era.orEmpty()
            ScopePopup.Author -> selectedAuthor?.id?.toString()
            ScopePopup.Favorites -> currentFolder?.toString().orEmpty()
        }
    val onChoose: (ChoiceNode) -> Unit = { node ->
        when (popup) {
            ScopePopup.Kind ->
                when (node.key) {
                    "books" -> choose(SearchScope.Global)
                    "authors" -> popup = ScopePopup.Era
                    "favorites" -> popup = ScopePopup.Favorites
                }
            ScopePopup.Era -> {
                era = node.key.ifEmpty { null }
                popup = ScopePopup.Author
            }
            ScopePopup.Author -> choose(SearchScope.Author(node.key.toLong()))
            ScopePopup.Favorites -> choose(SearchScope.Favorites(node.key.toLongOrNull()))
        }
    }
    val popupContent: @Composable (ScopePopup) -> Unit = { part ->
        if (anchor == part) {
            BreadcrumbPopup(
                model = BreadcrumbPopupModel(0, choices, choices.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)),
                onChoose = { onChoose(it as ChoiceNode) },
                onShift = { shift ->
                    val parts =
                        when (origin) {
                            is SearchScope.Author -> listOf(ScopePopup.Kind, ScopePopup.Era, ScopePopup.Author)
                            is SearchScope.Favorites -> listOf(ScopePopup.Kind, ScopePopup.Favorites)
                            else -> listOf(ScopePopup.Kind)
                        }
                    parts.getOrNull(parts.indexOf(part) + shift)?.let(::open)
                },
                onDismiss = { anchor = null },
                onCancel = { anchor = null },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = kind, modifier = Modifier.size(16.dp).clickable { open(ScopePopup.Kind) })
            popupContent(ScopePopup.Kind)
        }
        BreadcrumbSeparator()
        if (origin is SearchScope.Author) {
            ScopeSegment(AUTHOR_ERAS[selectedAuthor?.era] ?: unknownEra, onClick = { open(ScopePopup.Era) }) {
                popupContent(ScopePopup.Era)
            }
            BreadcrumbSeparator()
            ScopeSegment(
                selectedAuthor?.name ?: originName?.let(AuthorNames::display) ?: authorsLabel,
                onClick = {
                    era = selectedAuthor?.era?.takeIf(AUTHOR_ERAS::containsKey)
                    open(ScopePopup.Author)
                },
            ) { popupContent(ScopePopup.Author) }
        } else if (origin is SearchScope.Favorites) {
            ScopeSegment(originName ?: allFavorites, onClick = { open(ScopePopup.Favorites) }) { popupContent(ScopePopup.Favorites) }
        }
    }
}

@Composable
private fun ScopeSegment(
    title: String,
    onClick: () -> Unit,
    popup: @Composable () -> Unit,
) {
    Box(Modifier.widthIn(max = 180.dp)) {
        BreadcrumbSegment(title, isLast = false, isSelected = false, isInactive = false, onClick = onClick, onDoubleClick = onClick)
        popup()
    }
}
