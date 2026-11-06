package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.features.author.AUTHOR_ERAS
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Feather
import io.github.kdroidfilter.seforimapp.icons.JournalBookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*
import java.nio.file.Path
import java.sql.DriverManager

private data class ScopeAuthor(
    val id: Long,
    val name: String,
    val era: String?,
)

@Composable
internal fun SearchScopeOriginControls(
    origin: SearchScope,
    originName: String?,
    onChange: (SearchScope) -> Unit,
) {
    val graph = LocalAppGraph.current
    var stage by remember { mutableIntStateOf(-1) }
    var era by remember { mutableStateOf<String?>(null) }
    var authors by remember { mutableStateOf<List<ScopeAuthor>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val query = rememberTextFieldState()
    LaunchedEffect(stage, origin) {
        query.clearText()
        if (stage < 0 && origin !is SearchScope.Author) return@LaunchedEffect
        loading = true
        try {
            withContext(Dispatchers.IO) {
                val eras =
                    runCatching {
                        DriverManager.getConnection("jdbc:sqlite:${Path.of(getDatabasePath()).toUri()}?mode=ro").use { connection ->
                            connection.createStatement().use { statement ->
                                statement.executeQuery("SELECT id,era FROM author").use { rows ->
                                    buildMap { while (rows.next()) put(rows.getLong(1), rows.getString(2)) }
                                }
                            }
                        }
                    }.getOrDefault(emptyMap())
                authors = graph.repository.getAuthorsForLookup().map { ScopeAuthor(it.id, AuthorNames.display(it.name), eras[it.id]) }
                folders = graph.favoritesStore.folders()
            }
        } finally {
            loading = false
        }
    }
    val selectedAuthor = (origin as? SearchScope.Author)?.let { selected -> authors.firstOrNull { it.id == selected.authorId } }
    val kind =
        when (origin) {
            is SearchScope.Author -> Res.string.search_scope_kind_authors
            is SearchScope.Favorites -> Res.string.search_scope_kind_favorites
            else -> Res.string.search_scope_kind_books
        }

    fun choose(scope: SearchScope) {
        stage = -1
        onChange(scope)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            val iconModifier = Modifier.size(20.dp).clickable { stage = 0 }
            when (origin) {
                is SearchScope.Author -> Icon(Feather, stringResource(kind), iconModifier)
                is SearchScope.Favorites -> Icon(AllIconsKeys.Nodes.Favorite, stringResource(kind), iconModifier)
                else -> Icon(JournalBookmark, stringResource(kind), iconModifier)
            }
            if (stage >= 0) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, 28),
                    onDismissRequest = { stage = -1 },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .width(280.dp)
                            .heightIn(max = 360.dp)
                            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (stage != 0) Link(stringResource(Res.string.search_scope_back), onClick = { stage = 0 })
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp))
                        if (stage == 2) TextField(query, placeholder = { Text(stringResource(Res.string.search_scope_author_query)) })
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            when (stage) {
                                0 -> {
                                    ScopeChoice(stringResource(Res.string.search_scope_kind_books)) { choose(SearchScope.Global) }
                                    ScopeChoice(stringResource(Res.string.search_scope_kind_authors)) { stage = 1 }
                                    ScopeChoice(stringResource(Res.string.search_scope_kind_favorites)) { stage = 3 }
                                }
                                1 -> {
                                    val groups = AUTHOR_ERAS.keys.filter { code -> authors.any { it.era == code } } + listOf<String?>(null)
                                    groups.forEach { code ->
                                        ScopeChoice(AUTHOR_ERAS[code] ?: stringResource(Res.string.search_scope_era_unknown)) {
                                            era = code
                                            stage = 2
                                        }
                                    }
                                }
                                2 ->
                                    authors
                                        .filter {
                                            (if (era == null) it.era !in AUTHOR_ERAS else it.era == era) &&
                                                it.name.contains(query.text, ignoreCase = true)
                                        }.sortedBy { it.name }
                                        .forEach { author ->
                                            ScopeChoice(author.name) { choose(SearchScope.Author(author.id)) }
                                        }
                                3 -> {
                                    ScopeChoice(stringResource(Res.string.favorites_title)) { choose(SearchScope.Favorites(null)) }
                                    folders.forEach { folder -> ScopeChoice(folder.name) { choose(SearchScope.Favorites(folder.id)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (origin is SearchScope.Author) {
            Text(
                AUTHOR_ERAS[selectedAuthor?.era] ?: stringResource(Res.string.search_scope_era_unknown),
                Modifier.clickable { stage = 1 },
            )
            Text(stringResource(Res.string.breadcrumb_separator))
            Text(
                selectedAuthor?.name ?: originName?.let(AuthorNames::display) ?: stringResource(Res.string.search_scope_kind_authors),
                Modifier.widthIn(max = 180.dp).clickable {
                    era = selectedAuthor?.era
                    stage = 2
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (origin is SearchScope.Favorites) {
            Text(
                originName ?: stringResource(Res.string.favorites_title),
                Modifier.widthIn(max = 180.dp).clickable { stage = 3 },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScopeChoice(
    label: String,
    onClick: () -> Unit,
) {
    Text(label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp))
}
