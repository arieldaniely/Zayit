package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimapp.core.presentation.components.AccentMarkdownView
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.BookEdition
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jsoup.Jsoup
import seforimapp.seforimapp.generated.resources.*
import java.net.URI
import java.util.UUID

internal data class BookInformation(
    val book: Book,
    val editions: List<BookEdition>,
)

internal suspend fun loadBookInformation(
    repository: SeforimRepository,
    bookId: Long,
): BookInformation {
    val book = checkNotNull(repository.getBook(bookId)) { "Book not found" }
    // Editions are optional for older databases and personal books.
    val editions = runSuspendCatching { repository.getBookEditions(bookId) }.getOrDefault(emptyList())
    return BookInformation(book, editions)
}

internal fun informationSourceUrl(value: String?): String? =
    value?.takeIf { runCatching { URI(it).scheme in setOf("https", "http") }.getOrDefault(false) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookInformationButton(bookId: Long) {
    var visible by remember(bookId) { mutableStateOf(false) }
    val label = stringResource(Res.string.book_information_title)
    Tooltip({ Text(label) }) {
        IconButton(onClick = { visible = true }, modifier = Modifier.size(28.dp)) {
            Icon(AllIconsKeys.General.Information, contentDescription = label, modifier = Modifier.size(16.dp))
        }
    }
    if (visible) BookInformationDialog(bookId) { visible = false }
}

@Composable
private fun BookInformationDialog(
    bookId: Long,
    onDismiss: () -> Unit,
) {
    val repository = LocalAppGraph.current.repository
    val window = LocalOpenWindow.current
    val information by produceState<Result<BookInformation>?>(null, bookId) {
        value = runSuspendCatching { withContext(Dispatchers.IO) { loadBookInformation(repository, bookId) } }
    }
    var authorId by remember(bookId) { mutableStateOf<Long?>(null) }
    val author by produceState<Result<AuthorDetails?>?>(null, authorId) {
        value = null
        authorId?.let { id ->
            value = runSuspendCatching { withContext(Dispatchers.IO) { repository.getAuthorDetails(id) } }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 720.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.book_information_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (authorId == null) {
                when {
                    information == null -> CircularProgressIndicator()
                    information?.isFailure == true -> Text(stringResource(Res.string.book_information_missing))
                    else -> {
                        val details = information?.getOrNull()
                        if (details != null) {
                            Column(
                                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(details.book.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                details.book.heShortDesc?.takeIf { it.isNotBlank() }?.let { description ->
                                    SelectionContainer { Text(remember(description) { Jsoup.parse(description).text() }) }
                                }
                                if (details.book.pubDates.isNotEmpty()) {
                                    Text(
                                        stringResource(
                                            Res.string.book_information_dates,
                                            details.book.pubDates.joinToString(" · ") { it.date },
                                        ),
                                    )
                                }
                                Text(stringResource(Res.string.book_information_authors), fontWeight = FontWeight.SemiBold)
                                if (details.book.authors.isEmpty()) Text(stringResource(Res.string.book_information_missing))
                                details.book.authors.forEach { entry ->
                                    OutlinedButton(onClick = { authorId = entry.id }) { Text(entry.name) }
                                }
                                if (details.editions.isNotEmpty()) {
                                    Text(stringResource(Res.string.book_information_editions), fontWeight = FontWeight.SemiBold)
                                    details.editions.distinct().forEach { edition ->
                                        Text(edition.heTitle ?: edition.title)
                                        Text(stringResource(Res.string.book_information_license, edition.licenseCode))
                                        informationSourceUrl(edition.sourceUrl)?.let { url ->
                                            OutlinedButton(onClick = { UrlOpener.open(url) }) {
                                                Text(stringResource(Res.string.book_information_source))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val details = author?.getOrNull()
                when {
                    author == null -> CircularProgressIndicator()
                    details == null -> Text(stringResource(Res.string.book_information_missing))
                    else -> {
                        Column(
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(details.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            details.birthYear?.let { year ->
                                val display =
                                    if (details.birthYearApprox) {
                                        stringResource(
                                            Res.string.author_information_approx,
                                            year,
                                        )
                                    } else {
                                        year.toString()
                                    }
                                Text(stringResource(Res.string.author_information_birth, display))
                            }
                            details.deathYear?.let { year ->
                                val display =
                                    if (details.deathYearApprox) {
                                        stringResource(
                                            Res.string.author_information_approx,
                                            year,
                                        )
                                    } else {
                                        year.toString()
                                    }
                                Text(stringResource(Res.string.author_information_death, display))
                            }
                            val era =
                                when (details.era) {
                                    "Z" -> Res.string.author_era_zugot
                                    "T" -> Res.string.author_era_tannaim
                                    "A" -> Res.string.author_era_amoraim
                                    "GN" -> Res.string.author_era_geonim
                                    "RI" -> Res.string.author_era_rishonim
                                    "AH" -> Res.string.author_era_acharonim
                                    "CO" -> Res.string.author_era_contemporary
                                    else -> null
                                }
                            if (era != null) Text(stringResource(era))
                            if (details.aliases.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.author_information_aliases, details.aliases.joinToString(" · ")),
                                )
                            }
                            details.bio?.let { biography ->
                                Text(stringResource(Res.string.author_information_bio_notice), color = JewelTheme.globalColors.text.info)
                                Box(Modifier.fillMaxWidth().height(360.dp)) {
                                    AccentMarkdownView(resourcePath = "", markdownText = biography.bodyMd)
                                }
                                Text(stringResource(Res.string.book_information_license, biography.licenseCode))
                            }
                            Text(stringResource(Res.string.author_information_books), fontWeight = FontWeight.SemiBold)
                            details.books.forEach { book ->
                                OutlinedButton(onClick = {
                                    onDismiss()
                                    window.tabsViewModel.openTab(
                                        TabsDestination.BookContent(bookId = book.id, tabId = UUID.randomUUID().toString()),
                                    )
                                }) { Text(book.title) }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { authorId = null }) { Text(stringResource(Res.string.book_information_back)) }
            }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(Res.string.word_lookup_close)) }
        }
    }
}
