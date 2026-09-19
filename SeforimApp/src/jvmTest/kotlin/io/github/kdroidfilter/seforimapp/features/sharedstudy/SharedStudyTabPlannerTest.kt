package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedStudyTabPlannerTest {
    @Test
    fun `does not reopen a remotely created book dismissed during the session`() {
        val plan =
            SharedStudyTabPlanner.plan(
                remote = StudyLocation(bookId = 12, lineId = 900),
                tabs = emptyList(),
                currentLineId = null,
                dismissedBookIds = setOf(12),
            )

        assertEquals(SharedStudyTabPlanner.Action.IGNORE_DISMISSED, plan.action)
    }

    @Test
    fun `marks a remote line in the active visible tab without moving local reader`() {
        val plan =
            SharedStudyTabPlanner.plan(
                remote = StudyLocation(bookId = 7, lineId = 103),
                tabs = listOf(SharedStudyTabPlanner.OpenTab("active", 7, listOf(101, 102, 103), active = true)),
                currentLineId = 102,
            )

        assertEquals(SharedStudyTabPlanner.Action.MARK_VISIBLE, plan.action)
        assertEquals("active", plan.tabId)
    }

    @Test
    fun `indicates direction when peer is outside the active viewport in same book`() {
        val tabs = listOf(SharedStudyTabPlanner.OpenTab("active", 7, listOf(100, 101), active = true))

        assertEquals(
            SharedStudyTabPlanner.Action.INDICATE_ABOVE,
            SharedStudyTabPlanner.plan(StudyLocation(7, 50), tabs, currentLineId = 100).action,
        )
        assertEquals(
            SharedStudyTabPlanner.Action.INDICATE_BELOW,
            SharedStudyTabPlanner.plan(StudyLocation(7, 150), tabs, currentLineId = 100).action,
        )
    }

    @Test
    fun `uses an existing background tab before opening another`() {
        val plan =
            SharedStudyTabPlanner.plan(
                StudyLocation(7, 900),
                listOf(SharedStudyTabPlanner.OpenTab("background", 7, emptyList(), active = false)),
                currentLineId = null,
            )

        assertEquals(SharedStudyTabPlanner.Action.MARK_BACKGROUND_TAB, plan.action)
        assertEquals("background", plan.tabId)
    }

    @Test
    fun `opens a background tab when the book is not open`() {
        val plan = SharedStudyTabPlanner.plan(StudyLocation(7, 900), emptyList(), currentLineId = null)

        assertEquals(SharedStudyTabPlanner.Action.OPEN_BACKGROUND_TAB, plan.action)
        assertEquals(null, plan.tabId)
    }
}
