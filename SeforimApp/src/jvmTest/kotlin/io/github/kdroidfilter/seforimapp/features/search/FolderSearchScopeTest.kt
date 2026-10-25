package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderSearchScopeTest {
    private val main = UnconfinedTestDispatcher()
    private lateinit var persisted: TabPersistedStateStore
    private lateinit var viewModel: SearchHomeViewModel
    private val folder = Category(id = 7, title = "תלמוד")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(main)
        persisted = TabPersistedStateStore()
        viewModel = SearchHomeViewModel(persisted, mockk(relaxed = true), mockk(relaxed = true), PropertiesSettings(Properties()))
    }

    @AfterTest
    fun cleanup() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `typing and clearing query retains folder until explicitly removed`() {
        viewModel.onPickCategory(folder)
        viewModel.onReferenceQueryChanged("לימוד")
        viewModel.onReferenceQueryChanged("")
        assertEquals(folder, viewModel.uiState.value.selectedScopeCategory)
        assertEquals(SearchScope.Category(folder.id), viewModel.barScope())
        viewModel.onClearCategory()
        assertNull(viewModel.uiState.value.selectedScopeCategory)
        assertEquals(SearchScope.Global, viewModel.barScope())
    }

    @Test
    fun `folder submission preserves search mode and clears an earlier author fetch`() =
        runTest(main) {
            persisted.set("tab", TabPersistedState(search = SearchPersistedState().withScope(SearchScope.Author(99))))
            viewModel.onModeChange(SearchMode.EXACT)
            viewModel.onPickCategory(folder)
            viewModel.submitSearch("לימוד", "tab")
            val search = persisted.get("tab")!!.search!!
            assertEquals(SearchScope.Category(folder.id), search.scope)
            assertEquals(folder.id, search.fetchCategoryId)
            assertEquals(folder.id, search.filterCategoryId)
            assertEquals(0L, search.fetchAuthorId)
            assertEquals(0L, search.scopeAuthorId)
            assertEquals(SearchMode.EXACT.name, search.mode)
            assertTrue(search.globalExtended)
        }

    @Test
    fun `folder scope survives scope conversion and session serialization`() {
        val state = SearchPersistedState().withScope(SearchScope.Category(folder.id))
        val restored = Json.decodeFromString<SearchPersistedState>(Json.encodeToString(state))
        assertEquals("category", restored.datasetScope)
        assertEquals(SearchScope.Category(folder.id), restored.scope)
        val global = restored.withScope(SearchScope.Global)
        assertEquals(0L, global.fetchCategoryId)
        assertEquals(0L, global.filterCategoryId)
        assertEquals(SearchScope.Global, global.scope)
    }
}
