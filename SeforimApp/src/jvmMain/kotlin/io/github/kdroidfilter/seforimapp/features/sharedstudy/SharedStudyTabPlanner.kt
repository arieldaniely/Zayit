package io.github.kdroidfilter.seforimapp.features.sharedstudy

/** Pure decision layer for remote-presence tab behavior. */
object SharedStudyTabPlanner {
    enum class Action { MARK_VISIBLE, INDICATE_ABOVE, INDICATE_BELOW, MARK_BACKGROUND_TAB, OPEN_BACKGROUND_TAB, IGNORE_DISMISSED }

    data class OpenTab(
        val tabId: String,
        val bookId: Long,
        val visibleLineIds: List<Long>,
        val active: Boolean,
    )

    data class Plan(
        val action: Action,
        val tabId: String? = null,
        val targetLineId: Long,
    )

    fun plan(
        remote: StudyLocation,
        tabs: List<OpenTab>,
        currentLineId: Long?,
        dismissedBookIds: Set<Long> = emptySet(),
    ): Plan {
        val sameBook = tabs.filter { it.bookId == remote.bookId }
        if (sameBook.isEmpty() && remote.bookId in dismissedBookIds) {
            return Plan(Action.IGNORE_DISMISSED, targetLineId = remote.lineId)
        }
        val containing = sameBook.firstOrNull { remote.lineId in it.visibleLineIds }
        if (containing != null) {
            return if (containing.active) {
                Plan(Action.MARK_VISIBLE, containing.tabId, remote.lineId)
            } else {
                Plan(Action.MARK_BACKGROUND_TAB, containing.tabId, remote.lineId)
            }
        }
        val activeSameBook = sameBook.firstOrNull { it.active }
        if (activeSameBook != null && currentLineId != null) {
            return Plan(
                if (remote.lineId < currentLineId) Action.INDICATE_ABOVE else Action.INDICATE_BELOW,
                activeSameBook.tabId,
                remote.lineId,
            )
        }
        val backgroundSameBook = sameBook.firstOrNull()
        return if (backgroundSameBook != null) {
            Plan(Action.MARK_BACKGROUND_TAB, backgroundSameBook.tabId, remote.lineId)
        } else {
            Plan(Action.OPEN_BACKGROUND_TAB, targetLineId = remote.lineId)
        }
    }
}
