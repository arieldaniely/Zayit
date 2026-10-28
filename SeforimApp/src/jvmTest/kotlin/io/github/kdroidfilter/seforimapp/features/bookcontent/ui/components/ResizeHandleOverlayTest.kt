package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ResizeHandleOverlayTest {
    @Test
    fun `overlay reserves no space and can be dragged on both sides of its centre`() {
        for (axis in ResizeAxis.entries) {
            for (direction in LayoutDirection.entries) {
                for (edge in listOf(-2f, 2f)) {
                    runComposeUiTest {
                        var dragged = 0f
                        setContent {
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                val handle = @androidx.compose.runtime.Composable {
                                    ResizeHandleOverlay(axis) {
                                        val size =
                                            if (axis == ResizeAxis.Horizontal) {
                                                Modifier.requiredWidth(7.dp).fillMaxHeight()
                                            } else {
                                                Modifier.requiredHeight(7.dp).fillMaxWidth()
                                            }
                                        Box(
                                            size.draggable(
                                                state = rememberDraggableState { dragged += it },
                                                orientation =
                                                    if (axis ==
                                                        ResizeAxis.Horizontal
                                                    ) {
                                                        Orientation.Horizontal
                                                    } else {
                                                        Orientation.Vertical
                                                    },
                                            ),
                                        )
                                    }
                                }
                                if (axis == ResizeAxis.Horizontal) {
                                    Row(Modifier.size(200.dp)) {
                                        Box(Modifier.size(100.dp).testTag("first").clickable {})
                                        handle()
                                        Box(Modifier.size(100.dp).testTag("second").clickable {})
                                    }
                                } else {
                                    Column(Modifier.size(200.dp)) {
                                        Box(Modifier.size(100.dp).testTag("first").clickable {})
                                        handle()
                                        Box(Modifier.size(100.dp).testTag("second").clickable {})
                                    }
                                }
                            }
                        }
                        val first = onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
                        val second = onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
                        if (axis == ResizeAxis.Horizontal) {
                            assertEquals(first.width, abs(second.left - first.left))
                        } else {
                            assertEquals(first.bottom, second.top)
                        }
                        onRoot().performMouseInput {
                            val start =
                                if (axis == ResizeAxis.Horizontal) {
                                    Offset((if (direction == LayoutDirection.Ltr) first.right else first.left) + edge, first.center.y)
                                } else {
                                    Offset(first.center.x, first.bottom + edge)
                                }
                            moveTo(start)
                            press()
                            moveTo(start + if (axis == ResizeAxis.Horizontal) Offset(30f, 0f) else Offset(0f, 30f), 200)
                            release()
                        }
                        runOnIdle { assertTrue(abs(dragged) > 0f, "No drag at $axis / $direction / $edge") }
                    }
                }
            }
        }
    }
}
