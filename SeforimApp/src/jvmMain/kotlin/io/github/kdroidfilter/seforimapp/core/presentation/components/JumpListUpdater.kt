package io.github.kdroidfilter.seforimapp.core.presentation.components

import dev.nucleusframework.launcher.windows.JumpListCategory
import dev.nucleusframework.launcher.windows.JumpListItem
import dev.nucleusframework.launcher.windows.WindowsJumpListManager
import java.util.logging.Logger

internal class JumpListUpdater(
    private val setJumpList: (List<JumpListCategory>, List<JumpListItem>) -> Boolean = { categories, tasks ->
        WindowsJumpListManager.setJumpList(categories = categories, tasks = tasks)
    },
    private val lastError: () -> String? = { WindowsJumpListManager.lastError },
) {
    private val logger = Logger.getLogger(JumpListUpdater::class.java.name)

    fun update(
        categories: List<JumpListCategory>,
        tasks: List<JumpListItem>,
    ): Boolean {
        if (setJumpList(categories, tasks)) return true

        logger.warning("Could not update Windows jump list: ${lastError()}")
        if (categories.isEmpty() || tasks.isEmpty()) return false

        // Windows privacy settings can reject custom categories (E_ACCESSDENIED), but user
        // tasks are still allowed. Nucleus returns before AddUserTasks/CommitList on failure.
        // Its next BeginList releases the previous COM destination list and starts a new one.
        // Keep dynamic items out of this fallback to respect the user's privacy settings.
        val updated = setJumpList(emptyList(), tasks)
        if (!updated) {
            logger.warning("Could not update Windows jump list tasks: ${lastError()}")
        }
        return updated
    }
}
