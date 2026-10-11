package io.github.kdroidfilter.seforimapp.framework.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SnippetContextRangesTest {
    @Test
    fun distantHitsReadOnlyTheirContext() {
        assertEquals(listOf(96..104, 99996..100004), snippetContextRanges(listOf(100, 100000)))
    }

    @Test
    fun overlappingAndAdjacentWindowsMergeWithoutDuplicates() {
        assertEquals(listOf(0..22), snippetContextRanges(listOf(18, 0, 9, 9)))
        assertEquals(emptyList(), snippetContextRanges(emptyList()))
    }
}
