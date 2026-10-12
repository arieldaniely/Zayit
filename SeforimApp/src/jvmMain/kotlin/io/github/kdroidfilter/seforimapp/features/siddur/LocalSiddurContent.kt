package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.earthwidget.EarthWidgetLocation
import io.github.kdroidfilter.seforimapp.earthwidget.ZmanimOpinion
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocationViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class SiddurReadingSection(
    val heading: String,
    val lines: List<Line>,
)

internal suspend fun loadSiddurReading(
    repository: SeforimRepository,
    book: Book,
    day: SiddurDay,
): List<SiddurReadingSection> {
    val sections = selectSiddurSections(repository.getTocEntriesForBook(book.id), day)
    val seen = mutableSetOf<Long>()
    return sections.mapNotNull { section ->
        val ids = repository.getLineIdsForTocEntry(section.entry.id).filter { seen.add(it) }
        val lines = ids.chunked(400).flatMap { repository.getLinesByIds(it) }.sortedBy { it.lineIndex }
        if (lines.isEmpty()) null else SiddurReadingSection(section.entry.text, lines)
    }
}

private val prayerLabels =
    listOf(
        Res.string.siddur_part_shacharit,
        Res.string.siddur_part_mincha,
        Res.string.siddur_part_maariv,
        Res.string.siddur_hl_musaf,
        Res.string.siddur_part_neila,
        Res.string.siddur_part_kabbalat_shabbat,
        Res.string.siddur_part_birkat_hamazon,
        Res.string.siddur_part_meein_shalosh,
        Res.string.siddur_part_tefilat_haderech,
        Res.string.siddur_part_kriat_shema_al_hamita,
        Res.string.siddur_part_sfirat_haomer,
        Res.string.siddur_part_nerot_chanuka,
        Res.string.siddur_part_kiddush_levana,
        Res.string.siddur_part_havdala,
    )

private data class SiddurReadingState(
    val loading: Boolean = true,
    val sections: List<SiddurReadingSection> = emptyList(),
    val failed: Boolean = false,
)

@Composable
internal fun LocalSiddurContent(
    tabId: String,
    destination: TabsDestination.Siddur,
) {
    val graph = LocalAppGraph.current
    val settings = graph.appSettings
    val locationViewModel: HomeUserLocationViewModel =
        metroViewModel(
            viewModelStoreOwner = LocalWindowViewModelStoreOwner.current,
        )
    val location by locationViewModel.state.collectAsState()
    val initialMoment =
        remember(location) {
            val now = Instant.now()
            val date = now.atZone(ZoneId.of(location.userPlace.zoneId)).toLocalDate()
            val place = location.userPlace
            val times =
                computeZmanimTimes(
                    date,
                    EarthWidgetLocation(place.lat, place.lng, place.elevation, place.timeZone),
                    ZmanimOpinion.ITIM_LABINA,
                    location.inIsrael,
                )
            val afterSunset = times.sunset?.toInstant()?.let { now >= it } ?: false
            val afterMidday = times.chatzosHayom?.toInstant()?.let { now >= it } ?: false
            val currentPrayer =
                when {
                    afterSunset -> SiddurPrayer.MAARIV
                    afterMidday -> SiddurPrayer.MINCHA
                    else -> SiddurPrayer.SHACHARIT
                }
            (if (afterSunset) date.plusDays(1) else date) to currentPrayer
        }
    var epochDay by rememberSaveable(tabId) {
        mutableStateOf(destination.epochDay ?: initialMoment.first.toEpochDay())
    }
    var prayer by rememberSaveable(tabId) {
        mutableStateOf(SiddurPrayer.entries.firstOrNull { it.name == destination.part } ?: initialMoment.second)
    }
    val nusachCode by settings.siddurNusachFlow.collectAsState()
    val minyan by settings.siddurMinyanFlow.collectAsState()
    val beitAvel by settings.siddurBeitAvelFlow.collectAsState()
    val nusach =
        SiddurNusach.entries.firstOrNull { it.name == nusachCode }
            ?: if (nusachCode == "EDOT_HAMIZRACH") SiddurNusach.EDOT else SiddurNusach.ASHKENAZ
    val day =
        remember(epochDay, prayer, location.inIsrael, minyan, beitAvel) {
            siddurDay(LocalDate.ofEpochDay(epochDay), prayer, location.inIsrael, minyan, beitAvel)
        }
    val books by produceState<List<Book>>(emptyList(), graph.catalogCache) {
        value =
            withContext(Dispatchers.IO) {
                graph.catalogCache
                    .getAllBooks()
                    .orEmpty()
                    .filter { siddurKey(it.title).contains("סידור") }
                    .sortedWith(compareByDescending<Book> { it.isBaseBook }.thenBy { it.order }.thenBy { it.id })
            }
    }
    val candidates =
        remember(books, nusach) {
            books.filter { book -> nusach.titleParts.any { siddurKey(book.title).contains(it) } }
        }
    var selectedBookId by rememberSaveable(tabId, nusach) { mutableStateOf<Long?>(null) }
    val book = candidates.firstOrNull { it.id == selectedBookId } ?: candidates.firstOrNull()
    val reading by produceState(SiddurReadingState(), book?.id, day) {
        value = SiddurReadingState()
        value =
            try {
                SiddurReadingState(loading = false, sections = book?.let { loadSiddurReading(graph.repository, it, day) }.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SiddurReadingState(loading = false, failed = true)
            }
    }
    val fontCode by settings.bookFontCodeFlow.collectAsState()
    val textSize by settings.textSizeFlow.collectAsState()
    val font = FontCatalog.familyFor(fontCode)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListComboBox(
                items = prayerLabels.map { stringResource(it) },
                selectedIndex = prayer.ordinal,
                onSelectedItemChange = { prayer = SiddurPrayer.entries[it] },
            )
            ListComboBox(
                items =
                    listOf(
                        stringResource(Res.string.siddur_nusach_ashkenaz),
                        stringResource(Res.string.siddur_nusach_sefard),
                        stringResource(Res.string.siddur_nusach_edot),
                    ),
                selectedIndex = nusach.ordinal,
                onSelectedItemChange = { settings.setSiddurNusachCode(SiddurNusach.entries[it].name) },
            )
            CheckboxRow(stringResource(Res.string.siddur_minyan), minyan, settings::setSiddurMinyan)
            CheckboxRow(stringResource(Res.string.siddur_option_beit_avel), beitAvel, settings::setSiddurBeitAvel)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { epochDay-- }) { Text(stringResource(Res.string.siddur_previous_day)) }
            Text(day.date.toString())
            OutlinedButton(onClick = { epochDay++ }) { Text(stringResource(Res.string.siddur_next_day)) }
            if (candidates.isNotEmpty()) {
                ListComboBox(
                    items = candidates.map { it.title },
                    selectedIndex = candidates.indexOf(book).coerceAtLeast(0),
                    onSelectedItemChange = { selectedBookId = candidates[it].id },
                )
            }
        }
        // Inline variants in the source remain visible; heading-level filtering never rewrites sacred text.
        Text(stringResource(Res.string.local_siddur_source_variants))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (day.tachanun) Res.string.siddur_hl_tachanun else Res.string.siddur_hl_no_tachanun))
            Text(stringResource(if (day.mashivHaruach) Res.string.siddur_hl_mashiv else Res.string.local_siddur_summer))
            Text(stringResource(if (day.talUmatar) Res.string.siddur_hl_tal_umatar else Res.string.siddur_hl_vten_bracha))
            if (day.yaalehVeyavo) Text(stringResource(Res.string.siddur_hl_yaale))
            if (day.alHanissim) Text(stringResource(Res.string.siddur_hl_al_hanisim))
        }
        when {
            book == null -> Text(stringResource(Res.string.local_siddur_book_missing))
            reading.loading -> Text(stringResource(Res.string.local_siddur_loading))
            reading.failed -> Text(stringResource(Res.string.local_siddur_failed))
            reading.sections.isEmpty() -> Text(stringResource(Res.string.local_siddur_section_missing))
            else ->
                SelectionContainer(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        reading.sections.forEach { section ->
                            item(key = "heading-${section.lines.first().id}") {
                                Text(section.heading, fontFamily = font, fontSize = (textSize * 1.2f).sp)
                            }
                            items(section.lines, key = { it.id }) { line ->
                                val annotated = remember(line.content, textSize) { buildAnnotatedFromHtml(line.content, textSize) }
                                Text(annotated, fontFamily = font, fontSize = textSize.sp)
                            }
                        }
                    }
                }
        }
    }
}
