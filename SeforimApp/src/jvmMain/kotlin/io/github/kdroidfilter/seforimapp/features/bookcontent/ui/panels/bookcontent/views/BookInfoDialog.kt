package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.book_info_aliases
import seforimapp.seforimapp.generated.resources.book_info_approximately
import seforimapp.seforimapp.generated.resources.book_info_authors
import seforimapp.seforimapp.generated.resources.book_info_biography
import seforimapp.seforimapp.generated.resources.book_info_birth_place
import seforimapp.seforimapp.generated.resources.book_info_close
import seforimapp.seforimapp.generated.resources.book_info_death_place
import seforimapp.seforimapp.generated.resources.book_info_description
import seforimapp.seforimapp.generated.resources.book_info_life_years
import seforimapp.seforimapp.generated.resources.book_info_no_data
import seforimapp.seforimapp.generated.resources.book_info_period
import seforimapp.seforimapp.generated.resources.book_info_relations

private data class BookInfoData(val book: Book, val authors: List<AuthorDetails>)

@Composable
internal fun BookInfoDialog(bookId: Long, onDismiss: () -> Unit) {
    val repository = LocalAppGraph.current.repository
    val data by produceState<BookInfoData?>(null, bookId, repository) {
        val book = repository.getBook(bookId) ?: return@produceState
        value = BookInfoData(book, book.authors.mapNotNull { repository.getAuthorDetails(it.id) })
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(680.dp)
                .heightIn(max = 720.dp)
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            data?.let { loaded ->
                Text(loaded.book.title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        BookDescriptionSection(loaded.book)
                        if (loaded.authors.isNotEmpty()) {
                            InfoHeading(stringResource(Res.string.book_info_authors))
                            loaded.authors.forEach { AuthorSection(it) }
                        }
                        if (loaded.book.heDesc.isNullOrBlank() && loaded.book.heShortDesc.isNullOrBlank() &&
                            loaded.authors.isEmpty()
                        ) {
                            Text(stringResource(Res.string.book_info_no_data))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DefaultButton(onClick = onDismiss) { Text(stringResource(Res.string.book_info_close)) }
            }
        }
    }
}

@Composable
private fun BookDescriptionSection(book: Book) {
    val description = book.heDesc?.takeIf { it.isNotBlank() }
        ?: book.heShortDesc?.takeIf { it.isNotBlank() }
        ?: return
    InfoHeading(stringResource(Res.string.book_info_description))
    Text(buildAnnotatedFromHtml(description, baseTextSize = 15f, boldScale = 1f))
}

@Composable
private fun AuthorSection(details: AuthorDetails) {
    val author = details.author
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(author.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        author.eraName?.let { InfoLine(stringResource(Res.string.book_info_period), it) }
        lifeYears(author.birthYear, author.birthYearIsApprox, author.deathYear, author.deathYearIsApprox)?.let {
            InfoLine(stringResource(Res.string.book_info_life_years), it)
        }
        author.birthPlace?.let { InfoLine(stringResource(Res.string.book_info_birth_place), it) }
        author.deathPlace?.let { InfoLine(stringResource(Res.string.book_info_death_place), it) }
        author.heBio?.takeIf { it.isNotBlank() }?.let {
            InfoHeading(stringResource(Res.string.book_info_biography))
            Text(buildAnnotatedFromHtml(it, baseTextSize = 15f, boldScale = 1f))
        }
        val aliases = details.aliases.map { it.name }.filter { it != author.name }
        if (aliases.isNotEmpty()) InfoLine(stringResource(Res.string.book_info_aliases), aliases.joinToString(" · "))
        val relations = details.relations.mapNotNull { relation ->
            val target = relation.targetName ?: return@mapNotNull null
            relation.relationTypeHe?.let { "$it: $target" } ?: target
        }
        if (relations.isNotEmpty()) {
            InfoHeading(stringResource(Res.string.book_info_relations))
            relations.forEach { Text("• $it") }
        }
    }
}

@Composable
private fun InfoHeading(value: String) {
    Text(value, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.info)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("$label:", fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}

@Composable
private fun lifeYears(birth: Int?, birthApprox: Boolean, death: Int?, deathApprox: Boolean): String? {
    if (birth == null && death == null) return null
    val approximate = stringResource(Res.string.book_info_approximately)
    fun format(year: Int?, isApprox: Boolean): String = when {
        year == null -> "?"
        isApprox -> "$year ($approximate)"
        else -> year.toString()
    }
    return "${format(birth, birthApprox)}–${format(death, deathApprox)}"
}
