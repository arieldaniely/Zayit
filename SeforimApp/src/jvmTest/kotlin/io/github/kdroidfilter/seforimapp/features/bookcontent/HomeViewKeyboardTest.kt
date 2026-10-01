package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeViewKeyboardTest {
    @Test
    fun `standalone Alt tap toggles search filter`() =
        runComposeUiTest {
            var filter by mutableStateOf(SearchFilter.REFERENCE)
            val focusRequester = FocusRequester()

            setContent {
                var altAlone by remember { mutableStateOf(false) }
                var isFieldFocused by remember { mutableStateOf(false) }

                Box(
                    modifier =
                        Modifier
                            .testTag("searchField")
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                isFieldFocused = it.isFocused
                                if (!it.isFocused) {
                                    altAlone = false
                                }
                            }.onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    if (ev.key == Key.AltLeft || ev.key == Key.AltRight) {
                                        altAlone = !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    } else {
                                        altAlone = false
                                    }
                                } else if (ev.type == KeyEventType.KeyUp && ev.key != Key.AltLeft && ev.key != Key.AltRight) {
                                    altAlone = false
                                }

                                if ((ev.key == Key.AltLeft || ev.key == Key.AltRight) && ev.type == KeyEventType.KeyUp) {
                                    val shouldToggle = altAlone && !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    altAlone = false
                                    if (shouldToggle) {
                                        filter = if (filter == SearchFilter.REFERENCE) SearchFilter.TEXT else SearchFilter.REFERENCE
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            // Press and release AltLeft alone
            onNodeWithTag("searchField").performKeyInput {
                pressKey(Key.AltLeft)
            }
            waitForIdle()

            assertEquals(SearchFilter.TEXT, filter, "Filter should toggle to TEXT on single Alt press")

            // Press and release AltLeft alone again
            onNodeWithTag("searchField").performKeyInput {
                pressKey(Key.AltLeft)
            }
            waitForIdle()

            assertEquals(SearchFilter.REFERENCE, filter, "Filter should toggle back to REFERENCE on second Alt press")
        }

    @Test
    fun `Alt plus Shift combination does not toggle filter`() =
        runComposeUiTest {
            var filter by mutableStateOf(SearchFilter.REFERENCE)
            val focusRequester = FocusRequester()

            setContent {
                var altAlone by remember { mutableStateOf(false) }
                var isFieldFocused by remember { mutableStateOf(false) }

                Box(
                    modifier =
                        Modifier
                            .testTag("searchField")
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                isFieldFocused = it.isFocused
                                if (!it.isFocused) {
                                    altAlone = false
                                }
                            }.onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    if (ev.key == Key.AltLeft || ev.key == Key.AltRight) {
                                        altAlone = !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    } else {
                                        altAlone = false
                                    }
                                } else if (ev.type == KeyEventType.KeyUp && ev.key != Key.AltLeft && ev.key != Key.AltRight) {
                                    altAlone = false
                                }

                                if ((ev.key == Key.AltLeft || ev.key == Key.AltRight) && ev.type == KeyEventType.KeyUp) {
                                    val shouldToggle = altAlone && !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    altAlone = false
                                    if (shouldToggle) {
                                        filter = if (filter == SearchFilter.REFERENCE) SearchFilter.TEXT else SearchFilter.REFERENCE
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            // Simulate Alt + Shift (language switch)
            onNodeWithTag("searchField").performKeyInput {
                withKeyDown(Key.AltLeft) {
                    pressKey(Key.ShiftLeft)
                }
            }
            waitForIdle()

            assertEquals(
                SearchFilter.REFERENCE,
                filter,
                "Filter should remain REFERENCE and not toggle when Alt+Shift combo is pressed",
            )
        }

    @Test
    fun `Alt plus Tab combination does not toggle filter`() =
        runComposeUiTest {
            var filter by mutableStateOf(SearchFilter.REFERENCE)
            val focusRequester = FocusRequester()

            setContent {
                var altAlone by remember { mutableStateOf(false) }
                var isFieldFocused by remember { mutableStateOf(false) }

                Box(
                    modifier =
                        Modifier
                            .testTag("searchField")
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                isFieldFocused = it.isFocused
                                if (!it.isFocused) {
                                    altAlone = false
                                }
                            }.onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    if (ev.key == Key.AltLeft || ev.key == Key.AltRight) {
                                        altAlone = !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    } else {
                                        altAlone = false
                                    }
                                } else if (ev.type == KeyEventType.KeyUp && ev.key != Key.AltLeft && ev.key != Key.AltRight) {
                                    altAlone = false
                                }

                                if ((ev.key == Key.AltLeft || ev.key == Key.AltRight) && ev.type == KeyEventType.KeyUp) {
                                    val shouldToggle = altAlone && !ev.isShiftPressed && !ev.isCtrlPressed && !ev.isMetaPressed
                                    altAlone = false
                                    if (shouldToggle) {
                                        filter = if (filter == SearchFilter.REFERENCE) SearchFilter.TEXT else SearchFilter.REFERENCE
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }.focusable(),
                )
            }

            waitForIdle()
            focusRequester.requestFocus()
            waitForIdle()

            // Simulate Alt + Tab (task switch)
            onNodeWithTag("searchField").performKeyInput {
                withKeyDown(Key.AltLeft) {
                    pressKey(Key.Tab)
                }
            }
            waitForIdle()

            assertEquals(
                SearchFilter.REFERENCE,
                filter,
                "Filter should remain REFERENCE and not toggle when Alt+Tab combo is pressed",
            )
        }
}
