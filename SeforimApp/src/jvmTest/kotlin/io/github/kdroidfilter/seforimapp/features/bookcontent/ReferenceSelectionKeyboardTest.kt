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
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.kdroidfilter.seforimlibrary.core.models.Book
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
        referenceMode: Boolean,
        combined: Boolean = true,
        tocMode: Boolean = false,
    ) = runComposeUiTest {
        var picks = 0
        var submits = 0
        var visible by mutableStateOf(true)
        val focus = FocusRequester()
        val state = TextFieldState("ברכות ב:")
        setContent {
            PreviewContainer {
                SearchBar(
                    state = state,
                    selectedFilter = SearchFilter.REFERENCE,
                    onFilterChange = {},
                    showToggle = false,
                    showIcon = false,
                    focusRequester = focus,
                    autoFocus = false,
                    placeholderText = "",
                    suggestionsVisible = visible,
                    bookSuggestions =
                        persistentListOf(
                            BookSuggestion(book, listOf(book.title, toc.text), targetToc = toc.takeIf { combined }),
                        ),
                    selectedBook = book.takeIf { tocMode },
                    tocSuggestionsVisible = visible && tocMode,
                    tocSuggestions = listOf(TocSuggestion(toc, listOf(book.title, toc.text))),
                    onPickBook = { picks++ },
                    onPickToc = { picks++ },
                    onDismissSuggestions = { visible = false },
                    onSubmit = { submits++ },
                    submitCombinedReference = referenceMode,
                    submitOnEnterInReference = referenceMode && tocMode,
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
        assertEquals(if (referenceMode && key != Key.Tab && (combined || tocMode)) 1 else 0, submits)
    }

    @Test
    fun `Tab selects combined reference without opening`() = checkSelection(Key.Tab, referenceMode = true)

    @Test
    fun `Enter opens combined reference exactly once`() = checkSelection(Key.Enter, referenceMode = true)

    @Test
    fun `scope Enter only selects combined reference`() = checkSelection(Key.Enter, referenceMode = false)

    @Test
    fun `scope click only selects combined reference`() = checkSelection(null, referenceMode = false)

    @Test
    fun `book Enter preserves staged selection`() = checkSelection(Key.Enter, referenceMode = true, combined = false)

    @Test
    fun `toc Tab only selects location`() = checkSelection(Key.Tab, referenceMode = true, tocMode = true)

    @Test
    fun `toc Enter opens exactly once`() = checkSelection(Key.Enter, referenceMode = true, tocMode = true)
}
