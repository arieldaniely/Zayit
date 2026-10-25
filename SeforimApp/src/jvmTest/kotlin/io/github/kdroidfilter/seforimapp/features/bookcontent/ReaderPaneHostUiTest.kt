@file:OptIn(dev.nucleusframework.window.ExperimentalNucleusApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.window.tao.TaoWindow
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.JoinReaderPanes
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReaderPaneHostUiTest {
    @Test
    fun bothDocksAcquireTheirHostAndReleaseItWithTheComposition() =
        runComposeUiTest {
            val window = window("reader")
            val host = mockk<TaoWindow>(relaxed = true)
            var attached by mutableStateOf(false)
            setContent {
                if (attached) JoinReaderPanes(window, host)
            }
            runOnIdle {
                assertNull(window.panesOf(true).owner)
                assertNull(window.panesOf(false).owner)
                attached = true
            }
            runOnIdle {
                assertSame(host, window.panesOf(true).owner)
                assertSame(host, window.panesOf(false).owner)
                attached = false
            }
            runOnIdle {
                assertNull(window.panesOf(true).owner)
                assertNull(window.panesOf(false).owner)
                assertEquals(emptyList(), window.panesOf(true).members)
                assertEquals(emptyList(), window.panesOf(false).members)
            }
        }

    @Test
    fun replacingOneNativeHostLeavesTheOtherWindowDocksAttached() =
        runComposeUiTest {
            val first = window("first")
            val second = window("second")
            val original = mockk<TaoWindow>(relaxed = true)
            val replacement = mockk<TaoWindow>(relaxed = true)
            val other = mockk<TaoWindow>(relaxed = true)
            var host by mutableStateOf(original)
            setContent {
                JoinReaderPanes(first, host)
                JoinReaderPanes(second, other)
            }
            runOnIdle { host = replacement }
            runOnIdle {
                for (navigation in listOf(true, false)) {
                    assertSame(replacement, first.panesOf(navigation).owner)
                    assertEquals(listOf(replacement), first.panesOf(navigation).members)
                    assertSame(other, second.panesOf(navigation).owner)
                    assertEquals(listOf(other), second.panesOf(navigation).members)
                }
            }
        }

    private fun window(id: String) = OpenWindow(id, "desktop", mockk(relaxed = true), mockk(relaxed = true), WindowState())
}
