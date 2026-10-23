package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopedSearchTest {
    @Test
    fun restoredBookCategoryAndTocScopesAutomaticallyExpandCoverage() {
        val scopes =
            listOf(
                SearchPersistedState(datasetScope = "book", fetchBookId = 12),
                SearchPersistedState(datasetScope = "category", fetchCategoryId = 4),
                SearchPersistedState(datasetScope = "toc", fetchTocId = 9),
                SearchPersistedState(selectedBookIds = setOf(-12)),
                SearchPersistedState(filterCategoryId = 8),
            )
        scopes.forEach { assertTrue(it.withScopeCoverage().globalExtended) }
        assertFalse(SearchPersistedState().withScopeCoverage().globalExtended)
    }

    @Test
    fun engineCoverageIsConsistentForEveryExplicitFilter() {
        assertFalse(scopedBaseBooksOnly(true, 1, null, null, null))
        assertFalse(scopedBaseBooksOnly(true, null, 1, null, null))
        assertFalse(scopedBaseBooksOnly(true, null, null, listOf(-1), null))
        assertFalse(scopedBaseBooksOnly(true, null, null, null, listOf(1)))
        assertTrue(scopedBaseBooksOnly(true, null, null, null, null))
    }
}
