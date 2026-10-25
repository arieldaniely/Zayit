package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.BookSuggestion
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.SearchBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.TocSuggestion
import io.github.kdroidfilter.seforimapp.features.search.CategorySuggestionDto
import io.github.kdroidfilter.seforimapp.features.search.domain.reference.ResolvedReference
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReferenceSelectionKeyboardTest {
    private val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "ברכות", order = 1f)
    private val toc = TocEntry(id = 2L, bookId = 1L, text = "דף ב עמוד ב", level = 1)

    private fun checkSelection(
        key: Key?,
        jump: Boolean = false,
        tocMode: Boolean = false,
    ) = runComposeUiTest {
        var picks = 0
        var opens = 0
        var visible by mutableStateOf(true)
        val focus = FocusRequester()
        val state = TextFieldState("ברכות ב:")
        setContent {
            PreviewContainer {
                SearchBar(
                    state = state,
                    focusRequester = focus,
                    suggestionsVisible = visible && !tocMode,
                    bookSuggestions =
                        if (jump) {
                            persistentListOf()
                        } else {
                            persistentListOf(
                                BookSuggestion(book, listOf(book.title, toc.text)),
                            )
                        },
                    jumpSuggestions = if (jump) persistentListOf(ResolvedReference(book, 3, toc.text)) else persistentListOf(),
                    selectedBook = book.takeIf { tocMode },
                    tocSuggestionsVisible = visible && tocMode,
                    tocSuggestions = listOf(TocSuggestion(toc, listOf(book.title, toc.text))),
                    onPickBook = { picks++ },
                    onOpenBook = {
                        picks++
                        opens++
                    },
                    onPickJump = {
                        picks++
                        opens++
                    },
                    onPickToc = { picks++ },
                    onDismissSuggestions = { visible = false },
                    onSubmit = { opens++ },
                    submitOnEnterInReference = tocMode,
                )
            }
        }
        waitForIdle()
        runOnIdle { focus.requestFocus() }
        waitForIdle()
        if (key != null) {
            onNode(hasSetTextAction()).performKeyInput { pressKey(key) }
        } else {
            onNodeWithText(toc.text, substring = true).performClick()
        }
        waitForIdle()
        assertEquals(1, picks)
        assertEquals(if (jump || tocMode || key != Key.Tab) 1 else 0, opens)
    }

    @Test
    fun `Tab selects a book for searching inside`() = checkSelection(Key.Tab)

    @Test
    fun `Enter opens a book exactly once`() = checkSelection(Key.Enter)

    @Test
    fun `click opens a book exactly once`() = checkSelection(null)

    @Test
    fun `Enter opens a resolved reference directly`() = checkSelection(Key.Enter, jump = true)

    @Test
    fun `toc Tab opens the selected location as upstream does`() = checkSelection(Key.Tab, tocMode = true)

    @Test
    fun `toc Enter opens exactly once`() = checkSelection(Key.Enter, tocMode = true)

    private fun checkFolderSelection(key: Key) =
        runComposeUiTest {
            val folder = Category(id = 7, title = "תלמוד")
            val state = TextFieldState(folder.title)
            val focus = FocusRequester()
            var selected by mutableStateOf<Category?>(null)
            var picks = 0
            setContent {
                PreviewContainer {
                    SearchBar(
                        state = state,
                        focusRequester = focus,
                        suggestionsVisible = selected == null,
                        categorySuggestions = persistentListOf(CategorySuggestionDto(folder, listOf(folder.title))),
                        selectedCategory = selected,
                        onPickCategory = {
                            selected = it.category
                            picks++
                            state.edit { replace(0, length, "") }
                        },
                        onSubmit = {},
                    )
                }
            }
            waitForIdle()
            runOnIdle { focus.requestFocus() }
            onNode(hasSetTextAction()).performKeyInput { pressKey(key) }
            waitForIdle()
            assertEquals(1, picks)
            assertEquals(folder, selected)
            onNodeWithText(folder.title).assertExists()
        }

    @Test
    fun `Enter selects a folder as a search scope`() = checkFolderSelection(Key.Enter)

    @Test
    fun `Tab selects a folder as a search scope`() = checkFolderSelection(Key.Tab)
}
