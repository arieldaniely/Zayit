package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Author
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorDetails
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BookInformationUiTest {
    @Test
    fun metadataLoadsOnlyAfterOpeningAndAuthorSelection() =
        runComposeUiTest {
            val repository = mockk<SeforimRepository>()
            val graph = mockk<AppGraph>()
            every { graph.repository } returns repository
            coEvery { repository.getBook(7) } returns
                Book(
                    id = 7,
                    categoryId = 1,
                    sourceId = 1,
                    title = "ספר לבדיקה",
                    authors = listOf(Author(2, "מחבר לבדיקה")),
                    heShortDesc = "תיאור הספר",
                )
            coEvery { repository.getBookEditions(7) } returns emptyList()
            coEvery { repository.getAuthorDetails(2) } returns
                AuthorDetails(
                    2,
                    "מחבר לבדיקה",
                    1200,
                    false,
                    1280,
                    true,
                    "RI",
                    listOf("כינוי המחבר"),
                    null,
                    emptyList(),
                )
            setContent {
                CompositionLocalProvider(
                    LocalAppGraph provides graph,
                    LocalOpenWindow provides mockk(),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    IntUiTheme(isDark = false) { BookInformationButton(7) }
                }
            }
            coVerify(exactly = 0) { repository.getBook(any()) }
            onNodeWithContentDescription("מידע על הספר והמחברים").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("תיאור הספר").fetchSemanticsNodes().isNotEmpty() }
            coVerify(exactly = 0) { repository.getAuthorDetails(any()) }
            onNodeWithText("מחבר לבדיקה").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("תקופת הראשונים").fetchSemanticsNodes().isNotEmpty() }
            coVerify(exactly = 1) { repository.getAuthorDetails(2) }
        }
}
