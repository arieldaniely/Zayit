package io.github.kdroidfilter.seforimapp.integration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforimapp.core.history.HistoryStore
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.SearchFeedbackService
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.features.search.SearchResultViewModel
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.HybridSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in corpus benchmark: run with -Dzayit.search.benchmark.database=/path/to/seforim.db. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchLatencyIntegrationTest {
    @Test
    fun firstPageLatency() =
        runBlocking {
            val database = System.getProperty("zayit.search.benchmark.database") ?: return@runBlocking
            val semantic = System.getProperty("zayit.search.benchmark.semantic") ?: "$database.semantic"
            val started = System.nanoTime()
            PersistentSqliteDriver("jdbc:sqlite:$database").use { driver ->
                val repository = SeforimRepository(database, driver)
                val lexical =
                    LuceneSearchEngine(
                        Path.of("$database.lucene"),
                        RepositorySnippetSourceProvider(repository),
                        dictionaryPath = Path.of(database).resolveSibling("lexical.db"),
                    )
                HybridSearchEngine(
                    lexical,
                    Path.of("$semantic/model"),
                    Path.of("$semantic/index"),
                    Path.of(database),
                    resolveLine = { lineId, bookId, query ->
                        val line = repository.getLine(lineId)
                        val book = repository.getBookCore(bookId)
                        if (line == null || book == null || line.bookId != book.id) {
                            null
                        } else {
                            LineHit(
                                book.id,
                                book.title,
                                line.id,
                                line.lineIndex,
                                lexical.buildSnippet(line.content, query, 5),
                                0f,
                                line.content,
                                book.isBaseBook,
                            )
                        }
                    },
                ).use { engine ->
                    if (System.getProperty("zayit.search.benchmark.ui") == "true") {
                        benchmarkUi(database, repository, engine)
                        return@use
                    }
                    println("ENGINE CONSTRUCTION ms=${(System.nanoTime() - started) / 1_000_000}")
                    for (mode in listOf(SearchMode.SMART, SearchMode.FLEXIBLE)) {
                        for (query in listOf(
                            "מצוות התלויות בארץ",
                            "שמירת שבת",
                            "דיני תפילה",
                            "ואהבת לרעך כמוך",
                            "השבת אבדה",
                            "כיבוד אב ואם",
                            "ברכת המזון",
                            "דיני מוקצה",
                            "שמיטת כספים",
                            "הלכות צדקה",
                            "לימוד תורה",
                        )) {
                            val before = System.nanoTime()
                            val page = engine.openSession(query, baseBookOnly = true, mode = mode)!!.use { it.nextPage(25) }
                            val elapsed = (System.nanoTime() - before) / 1_000_000
                            assertTrue(page?.hits?.isNotEmpty() == true, "No results: $query")
                            println("FIRST PAGE mode=$mode query=$query ms=$elapsed hits=${page!!.hits.size}")
                            System.getProperty("zayit.search.benchmark.maxMillis")?.toLong()?.let {
                                assertTrue(elapsed < it, "$mode $query exceeded ${it}ms: ${elapsed}ms")
                            }
                        }
                    }
                }
            }
        }

    private suspend fun benchmarkUi(
        database: String,
        repository: SeforimRepository,
        engine: HybridSearchEngine,
    ) = kotlinx.coroutines.coroutineScope {
        val settings = PropertiesSettings(Properties())
        AppSettings.initialize(settings)
        AppSettings.setDatabasePath(database)
        Dispatchers.setMain(Dispatchers.Default)
        val settingsDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        UserSettingsDb.Schema.create(settingsDriver)
        val store = TabPersistedStateStore()
        val titles = TabTitleUpdateManager()
        val home = SearchHomeViewModel(store, repository, LuceneLookupSearchService(Path.of("$database.lookup.lucene")), settings)
        val model =
            SearchResultViewModel(
                SavedStateHandle(),
                store,
                repository,
                engine,
                titles,
                DesktopManager(store, titles, { home }, "benchmark"),
                HistoryStore(UserSettingsDb(settingsDriver)),
                SearchFeedbackService(repository),
                LuceneLookupSearchService(Path.of("$database.lookup.lucene")),
            )
        val visible = launch { model.visibleResultsFlow.collect() }
        try {
            model.onEvent(SearchResultViewModel.SearchResultEvents.SetUiVisible(true))
            for (mode in listOf(SearchMode.SMART, SearchMode.FLEXIBLE)) {
                model.setQuery("")
                model.onEvent(SearchResultViewModel.SearchResultEvents.SetMode(mode))
                for (query in listOf(
                    "מצוות התלויות בארץ",
                    "שמירת שבת",
                    "דיני תפילה",
                    "ואהבת לרעך כמוך",
                    "השבת אבדה",
                    "כיבוד אב ואם",
                    "ברכת המזון",
                    "דיני מוקצה",
                    "שמיטת כספים",
                    "הלכות צדקה",
                    "לימוד תורה",
                )) {
                    model.setQuery(query)
                    val started = System.nanoTime()
                    val completed =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            model.uiState.first { it.isLoading }
                            model.uiState.first { !it.isLoading }
                            model.visibleResultsFlow.first { it.isNotEmpty() && it == model.uiState.value.results }
                        }
                    model.executeSearch()
                    val hits = withTimeout(120_000) { completed.await() }
                    if (mode == SearchMode.SMART) {
                        assertTrue(!model.uiState.value.semanticFallback, "Smart search unexpectedly fell back")
                    }
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    println("VISIBLE UI mode=$mode query=$query ms=$elapsed hits=${hits.size}")
                    System.getProperty("zayit.search.benchmark.maxMillis")?.toLong()?.let {
                        assertTrue(elapsed < it, "UI $mode $query exceeded ${it}ms: ${elapsed}ms")
                    }
                }
            }
        } finally {
            visible.cancel()
            model.viewModelScope.cancel()
            home.viewModelScope.cancel()
            settingsDriver.close()
            Dispatchers.resetMain()
        }
    }
}
