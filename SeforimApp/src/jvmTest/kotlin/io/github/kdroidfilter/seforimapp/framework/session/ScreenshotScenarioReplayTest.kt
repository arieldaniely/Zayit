@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.session

import io.github.vinceglb.filekit.FileKit
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenshotScenarioReplayTest {
    init {
        FileKit.init("io.github.kdroidfilter.seforimapp.screenshot-test")
    }

    private val proto = ProtoBuf

    private fun selectedState(scenario: String): TabPersistedState {
        val resource = requireNotNull(javaClass.classLoader.getResource("website-screenshots/$scenario.pb"))
        val recorded = proto.decodeFromByteArray(DesktopsState.serializer(), resource.readBytes())
        val normalized = SessionManager.normalizeScreenshotState(recorded, scenario)
        for (snapshot in normalized.snapshots.values) {
            for (window in snapshot.effectiveWindows()) {
                val selected = window.destinations.getOrNull(window.selectedIndex) ?: continue
                return snapshot.tabStates.getValue(selected.tabId)
            }
        }
        error("No selected tab in $scenario")
    }

    @Test
    fun `sidebars are deterministic for every scenario`() {
        for (scenario in listOf("DB-SEARCH-SIMPLE", "DB-SEARCH-ADVANCED")) {
            val state = selectedState(scenario).bookContent
            assertTrue(state.isBookTreeVisible)
            assertEquals(setOf(44L, 61L, 62L), state.expandedCategoryIds)
            assertFalse(state.isTocVisible)
        }

        for (scenario in listOf("HOME", "BOOK-SEARCH", "TOC-BOOK-SEARCH")) {
            val state = selectedState(scenario).bookContent
            assertTrue(state.isBookTreeVisible)
            assertTrue(state.expandedCategoryIds.isEmpty())
            assertFalse(state.isTocVisible)
        }

        for (scenario in listOf("INBOOK-SEARCH", "PIRUSHIM", "PIRUSHIM-TARGUMIM", "MEKOR", "CLIPBOARD-DEMO")) {
            val state = selectedState(scenario).bookContent
            assertFalse(state.isBookTreeVisible)
            assertTrue(state.isTocVisible)
            assertTrue(state.expandedTocEntryIds.isEmpty())
        }
    }

    @Test
    fun `book panes are deterministic for every scenario`() {
        val commentaries = selectedState("PIRUSHIM").bookContent
        assertTrue(commentaries.showCommentaries)
        assertFalse(commentaries.showTargum)
        assertFalse(commentaries.showSources)

        val targum = selectedState("PIRUSHIM-TARGUMIM").bookContent
        assertTrue(targum.showCommentaries)
        assertTrue(targum.showTargum)
        assertFalse(targum.showSources)

        val sources = selectedState("MEKOR").bookContent
        assertFalse(sources.showCommentaries)
        assertFalse(sources.showTargum)
        assertTrue(sources.showSources)
    }

    @Test
    fun `database searches always use the recorded global scope`() {
        val simple = assertNotNull(selectedState("DB-SEARCH-SIMPLE").search)
        val advanced = assertNotNull(selectedState("DB-SEARCH-ADVANCED").search)

        val expectedQuery =
            "\u05DC\u05D7\u05EA\u05D5\u05DA " +
                "\u05E6\u05E0\u05D5\u05DF " +
                "\u05D1\u05E1\u05DB\u05D9\u05DF " +
                "\u05D1\u05E9\u05E8\u05D9"
        for (state in listOf(simple, advanced)) {
            assertEquals(expectedQuery, state.query)
            assertEquals("global", state.datasetScope)
            assertEquals(0L, state.filterCategoryId)
            assertEquals(0L, state.filterBookId)
            assertEquals(0L, state.filterTocId)
            assertEquals(0L, state.fetchCategoryId)
            assertEquals(0L, state.fetchBookId)
            assertEquals(0L, state.fetchTocId)
        }
        assertFalse(simple.globalExtended)
        assertTrue(advanced.globalExtended)
    }
}
