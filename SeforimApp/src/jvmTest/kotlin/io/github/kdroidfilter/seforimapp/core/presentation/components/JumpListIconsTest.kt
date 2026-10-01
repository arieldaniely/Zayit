package io.github.kdroidfilter.seforimapp.core.presentation.components

import io.github.kdroidfilter.seforim.tabs.TabType
import kotlin.test.Test
import kotlin.test.assertNotNull

class JumpListIconsTest {
    @Test
    fun testIconForNewTabNotNull() {
        assertNotNull(JumpListIcons.iconForNewTab(isDark = false))
        assertNotNull(JumpListIcons.iconForNewTab(isDark = true))
    }

    @Test
    fun testIconForNewDesktopNotNull() {
        assertNotNull(JumpListIcons.iconForNewDesktop(isDark = false))
        assertNotNull(JumpListIcons.iconForNewDesktop(isDark = true))
    }

    @Test
    fun testIconForDesktopNotNull() {
        assertNotNull(JumpListIcons.iconForDesktop(isDark = false))
        assertNotNull(JumpListIcons.iconForDesktop(isDark = true))
    }

    @Test
    fun testIconForFavoriteNotNull() {
        assertNotNull(JumpListIcons.iconForFavorite(isDark = false))
        assertNotNull(JumpListIcons.iconForFavorite(isDark = true))
    }

    @Test
    fun testIconForRecentlyClosedNotNull() {
        assertNotNull(JumpListIcons.iconForRecentlyClosed(isDark = false))
        assertNotNull(JumpListIcons.iconForRecentlyClosed(isDark = true))
    }

    @Test
    fun testIconForTabVariants() {
        for (isDark in listOf(false, true)) {
            val homeIcon = JumpListIcons.iconForTab(TabType.BOOK, "", isDark = isDark)
            assertNotNull(homeIcon)

            val searchIcon = JumpListIcons.iconForTab(TabType.SEARCH, "חיפוש", isDark = isDark)
            assertNotNull(searchIcon)

            val bookIcon = JumpListIcons.iconForTab(TabType.BOOK, "בראשית", isDark = isDark)
            assertNotNull(bookIcon)

            val favIcon = JumpListIcons.iconForTab(TabType.FAVORITES, "מועדפים", isDark = isDark)
            assertNotNull(favIcon)

            val histIcon = JumpListIcons.iconForTab(TabType.HISTORY, "היסטוריה", isDark = isDark)
            assertNotNull(histIcon)
        }
    }
}
