package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal data class SmartFindHighlights(
    val query: String = "",
    val terms: List<String> = emptyList(),
    val semanticLineIds: Set<Long> = emptySet(),
    val passages: Map<Long, String> = emptyMap(),
)

/** Dictionary matches arrive first; semantic localization is limited to visible candidate paragraphs. */
internal class SmartFindUseCase(
    private val engine: SearchEngine,
) {
    fun search(
        bookId: Long,
        query: String,
        visibleLines: Flow<List<Line>>,
    ): Flow<SmartFindHighlights> =
        channelFlow {
            if (query.trim().length < 2) {
                send(SmartFindHighlights(query))
                return@channelFlow
            }
            var state = SmartFindHighlights(query, listOf(query))
            send(state)
            val terms =
                runSuspendCatching {
                    withContext(Dispatchers.IO) { engine.buildHighlightTerms(query) }
                }.getOrDefault(listOf(query))
            state = state.copy(terms = terms.ifEmpty { listOf(query) })
            send(state)
            val candidates =
                runSuspendCatching { engine.semanticFind(query, bookId, 150).toSet() }.getOrDefault(emptySet())
            if (candidates.isEmpty()) return@channelFlow
            state = state.copy(semanticLineIds = candidates)
            send(state)
            val localized = mutableMapOf<Long, String>()
            val attempted = mutableSetOf<Long>()
            visibleLines.distinctUntilChanged().collectLatest { visible ->
                val pending = visible.filter { it.bookId == bookId && it.id in candidates && it.id !in attempted }
                for (batch in pending.chunked(8)) {
                    val texts = withContext(Dispatchers.Default) { batch.map { Jsoup.parse(it.content).text() } }
                    val passages =
                        runSuspendCatching { engine.passagesByMeaning(query, texts) }.getOrNull() ?: return@collectLatest
                    batch.forEachIndexed { index, line ->
                        val text = texts[index]
                        val passage = passages.getOrNull(index)?.takeIf { it.isNotBlank() } ?: text
                        // The model only selects verbatim text; never paint a hallucinated passage.
                        if (passage.isNotBlank() && text.contains(passage)) localized[line.id] = passage
                        attempted += line.id
                    }
                    state = state.copy(passages = localized.toMap())
                    send(state)
                }
            }
        }
}
