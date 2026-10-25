package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookTabUi
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.NavigationState
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.Providers
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookContentPanel
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.LocalBookTextReady
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.mockk.mockk
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BookTextReadyUiTest {
    @Test
    fun aDisplayedBookStaysComposedWhileItsWindowPanesFollowAnotherTab() =
        runComposeUiTest {
            val graph = testAppGraph()
            val tabUi = BookTabUi()
            var ready by mutableStateOf(false)
            var bookId by mutableLongStateOf(7L)
            var created = 0
            var disposed = 0
            val providers = mockk<Providers>(relaxed = true)
            setContent {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalBookTextReady provides { ready }) {
                    IntUiTheme(isDark = false) {
                        BookContentPanel(
                            uiState =
                                BookContentState(
                                    navigation = NavigationState(selectedBook = Book(bookId, 1, 1, "ספר לבדיקה")),
                                    providers = providers,
                                ),
                            onEvent = {},
                            showDiacritics = true,
                            tabUi = tabUi,
                            mainContentOverride = {
                                remember { created++ }
                                DisposableEffect(Unit) { onDispose { disposed++ } }
                                Text("טקסט", modifier = Modifier.testTag("book-text"))
                            },
                        )
                    }
                }
            }
            onNodeWithTag("book-text").assertDoesNotExist()
            runOnIdle { ready = true }
            onNodeWithTag("book-text").assertExists()
            runOnIdle { ready = false }
            onNodeWithTag("book-text").assertExists()
            runOnIdle {
                assertEquals(1, created)
                assertEquals(0, disposed)
            }
            runOnIdle { bookId = 8L }
            onNodeWithTag("book-text").assertDoesNotExist()
            runOnIdle { ready = true }
            onNodeWithTag("book-text").assertExists()
            runOnIdle {
                assertEquals(2, created)
                assertEquals(1, disposed)
            }
        }
}
