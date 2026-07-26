package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWindowViewModelTest {
    private val focusedWindowId = MutableStateFlow("window-1")
    private val desktopManager =
        mockk<DesktopManager> {
            every { focusedWindowId } returns this@SettingsWindowViewModelTest.focusedWindowId
        }

    private fun createViewModel() = SettingsWindowViewModel(desktopManager)

    @Test
    fun `initial state has isVisible false`() =
        runTest {
            val viewModel = createViewModel()
            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `OnOpen event sets isVisible to true`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
            assertEquals("window-1", viewModel.state.value.ownerWindowId)
        }

    @Test
    fun `OnClose event sets isVisible to false`() =
        runTest {
            val viewModel = createViewModel()

            // First open
            viewModel.onEvent(SettingsWindowEvents.OnOpen)
            assertTrue(viewModel.state.value.isVisible)

            // Then close
            viewModel.onEvent(SettingsWindowEvents.OnClose)
            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `multiple OnOpen events keep isVisible true`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnOpen)
            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            assertTrue(viewModel.state.value.isVisible)
        }

    @Test
    fun `multiple OnClose events keep isVisible false`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onEvent(SettingsWindowEvents.OnClose)
            viewModel.onEvent(SettingsWindowEvents.OnClose)

            assertFalse(viewModel.state.value.isVisible)
        }

    @Test
    fun `state flow emits updates`() =
        runTest {
            val viewModel = createViewModel()

            // Collect initial state
            val initialState = viewModel.state.value
            assertFalse(initialState.isVisible)

            // Trigger event
            viewModel.onEvent(SettingsWindowEvents.OnOpen)

            // Verify state changed
            val newState = viewModel.state.value
            assertTrue(newState.isVisible)
        }

    @Test
    fun `state is a StateFlow`() {
        val viewModel = createViewModel()
        assertEquals(SettingsWindowState::class, viewModel.state.value::class)
    }
}
