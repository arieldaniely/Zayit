package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorLookupEntry
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchScopeOriginControlsTest {
    @Test
    fun switchesBetweenAuthorAndFavoritesFromThePath() =
        runComposeUiTest {
            testAppSettings()
            val graph = testAppGraph()
            coEvery { graph.repository.getAuthorsForLookup() } returns
                listOf(AuthorLookupEntry(-7, "מחבר בדיקה", emptyList(), 2))
            val folderId = runBlocking { graph.favoritesStore.createFolder("תיקיית בדיקה", 1L) }
            var origin by mutableStateOf<SearchScope>(SearchScope.Global)
            setContent {
                IntUiTheme(isDark = false) {
                    CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            Modifier
                                .width(700.dp)
                                .height(120.dp)
                                .background(JewelTheme.globalColors.panelBackground)
                                .padding(16.dp),
                        ) {
                            SearchScopeOriginControls(
                                origin,
                                if (origin is SearchScope.Favorites) "תיקיית בדיקה" else null,
                                onChange = { origin = it },
                            )
                        }
                    }
                }
            }
            onNodeWithContentDescription("ספרים").performClick()
            onNodeWithText("מחברים").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("מחברים נוספים").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("מחברים נוספים").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("הרב מחבר בדיקה").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("הרב מחבר בדיקה").performClick()
            runOnIdle { assertEquals(SearchScope.Author(-7), origin) }
            waitForIdle()
            val authorShot = File("build/screenshots/search-path-author.png")
            authorShot.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", authorShot)
            onNodeWithContentDescription("מחברים").performClick()
            onNodeWithText("מועדפים").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("תיקיית בדיקה").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("תיקיית בדיקה").performClick()
            runOnIdle { assertEquals(SearchScope.Favorites(folderId), origin) }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/screenshots/search-path-favorites.png"))
        }
}
