package io.github.kdroidfilter.seforimapp.core.presentation.components

import dev.nucleusframework.launcher.windows.JumpListCategory
import dev.nucleusframework.launcher.windows.JumpListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JumpListUpdaterTest {
    private val categories =
        listOf(JumpListCategory("לשוניות פתוחות", listOf(JumpListItem("בראשית", "seforim://tab/0"))))
    private val tasks =
        listOf(
            JumpListItem("לשונית חדשה", "seforim://new-tab"),
            JumpListItem("שולחן עבודה חדש", "seforim://new-desktop"),
        )

    @Test
    fun successfulUpdatePreservesCategoriesAndTasks() {
        val calls = mutableListOf<Pair<List<JumpListCategory>, List<JumpListItem>>>()
        val updater =
            JumpListUpdater(
                setJumpList = { categories, tasks ->
                    calls += categories to tasks
                    true
                },
                lastError = { error("Successful updates must not read the native error") },
            )

        assertTrue(updater.update(categories, tasks))
        assertEquals(listOf(categories to tasks), calls)
    }

    @Test
    fun blockedRecentItemsStillPublishQuickActions() {
        val calls = mutableListOf<Pair<List<JumpListCategory>, List<JumpListItem>>>()
        val updater =
            JumpListUpdater(
                setJumpList = { categories, tasks ->
                    calls += categories to tasks
                    categories.isEmpty()
                },
                lastError = { "AppendCategory failed (HRESULT: 0x80070005)" },
            )

        assertTrue(updater.update(categories, tasks))
        assertEquals(listOf(categories to tasks, emptyList<JumpListCategory>() to tasks), calls)
    }

    @Test
    fun failedFallbackReturnsFailureWithoutRepeatedRetries() {
        var attempts = 0
        val updater =
            JumpListUpdater(
                setJumpList = { _, _ ->
                    attempts++
                    false
                },
                lastError = { "CommitList failed" },
            )

        assertFalse(updater.update(categories, tasks))
        assertEquals(2, attempts)
    }

    @Test
    fun tasksOnlyFailureIsNotRetried() {
        var attempts = 0
        val updater =
            JumpListUpdater(
                setJumpList = { _, _ ->
                    attempts++
                    false
                },
                lastError = { "AddUserTasks failed" },
            )

        assertFalse(updater.update(emptyList(), tasks))
        assertEquals(1, attempts)
    }

    @Test
    fun laterUpdatesRetryCategoriesAfterPrivacySettingsChange() {
        val published = mutableListOf<Pair<List<JumpListCategory>, List<JumpListItem>>>()
        var recentItemsAllowed = false
        val updater =
            JumpListUpdater(
                setJumpList = { categories, tasks ->
                    val accepted = recentItemsAllowed || categories.isEmpty()
                    if (accepted) published += categories to tasks
                    accepted
                },
                lastError = { "AppendCategory failed (HRESULT: 0x80070005)" },
            )

        assertTrue(updater.update(categories, tasks))
        recentItemsAllowed = true
        assertTrue(updater.update(categories, tasks))
        assertEquals(listOf(emptyList<JumpListCategory>() to tasks, categories to tasks), published)
    }
}
