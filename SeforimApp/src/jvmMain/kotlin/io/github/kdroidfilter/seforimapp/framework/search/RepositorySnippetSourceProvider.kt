package io.github.kdroidfilter.seforimapp.framework.search

import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.LineSnippetInfo
import io.github.kdroidfilter.seforimlibrary.search.SnippetProvider
import io.github.kdroidfilter.seforimlibrary.search.SnippetSource
import io.github.kdroidfilter.seforimlibrary.search.SnippetSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [SnippetProvider] over the app's database: the lines come from [repository]. */
class RepositorySnippetSourceProvider(
    private val repository: SeforimRepository,
) : SnippetProvider {
    override suspend fun getSnippetSources(lines: List<LineSnippetInfo>): Map<Long, SnippetSource> {
        if (lines.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            SnippetSources.build(lines) { bookId, from, to ->
                repository.getLines(bookId, from, to).associate { it.lineIndex to it.content }
            }
        }
    }
}

private const val SNIPPET_NEIGHBOR_WINDOW = 4

internal fun snippetContextRanges(indices: List<Int>): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    for (index in indices.distinct().sorted()) {
        val next = (index - SNIPPET_NEIGHBOR_WINDOW).coerceAtLeast(0)..index + SNIPPET_NEIGHBOR_WINDOW
        val previous = ranges.lastOrNull()
        if (previous != null && next.first.toLong() <= previous.last.toLong() + 1) {
            ranges[ranges.lastIndex] = previous.first..maxOf(previous.last, next.last)
        } else {
            ranges += next
        }
    }
    return ranges
}
