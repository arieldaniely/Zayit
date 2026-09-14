package io.github.kdroidfilter.seforimapp.features.sharedstudy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundledNativeLibraryTest {
    @Test
    fun `selects native directory for both Mac architectures`() {
        assertEquals("macos-arm64", BundledNativeLibrary.platformDirectory("Mac OS X", "aarch64"))
        assertEquals("macos-x64", BundledNativeLibrary.platformDirectory("Mac OS X", "x86_64"))
    }

    @Test
    fun `selects native directory for both Windows architectures`() {
        assertEquals("windows-arm64", BundledNativeLibrary.platformDirectory("Windows 11", "ARM64"))
        assertEquals("windows-x64", BundledNativeLibrary.platformDirectory("Windows 11", "amd64"))
    }

    @Test
    fun `rejects unsupported desktop platform`() {
        assertNull(BundledNativeLibrary.platformDirectory("Linux", "x86_64"))
    }
}
