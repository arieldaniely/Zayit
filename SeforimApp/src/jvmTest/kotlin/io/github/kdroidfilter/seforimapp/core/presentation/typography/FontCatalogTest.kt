package io.github.kdroidfilter.seforimapp.core.presentation.typography

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FontCatalogTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a discovered system font can be selected for rendering`() =
        runComposeUiTest {
            val installed = runBlocking { FontCatalog.installedOptions() }.firstOrNull { it.systemFamilyName != null }
            assumeTrue("No system fonts installed on this machine", installed != null)
            setContent {
                BasicText("שלום 123", style = TextStyle(fontFamily = FontCatalog.familyFor(installed!!.code)))
            }
            onNodeWithText("שלום 123").assertIsDisplayed()
        }

    @Test
    fun `installed fonts extend bundled choices without changing persisted codes`() {
        val combined = FontCatalog.withSystemFonts(listOf("Arial", "גופן עברי", "Noto Serif Hebrew"))
        assertEquals(FontCatalog.options, combined.take(FontCatalog.options.size))
        assertEquals(
            setOf("system:Arial", "system:גופן עברי", "system:Noto Serif Hebrew"),
            combined.drop(FontCatalog.options.size).map { it.code }.toSet(),
        )
    }

    @Test
    fun `installed font names are deduplicated and sorted for selection`() {
        val combined = FontCatalog.withSystemFonts(listOf("Zulu", " Arial ", "arial", "", "  ", "Calibri"))
        assertEquals(listOf("Arial", "Calibri", "Zulu"), combined.drop(FontCatalog.options.size).map { it.systemFamilyName })
    }

    @Test
    fun `FontOption stores code and label`() {
        val option = FontCatalog.options.first()
        assertNotNull(option.code)
        assertNotNull(option.label)
    }

    @Test
    fun `FontCatalog has expected number of options`() {
        assertEquals(28, FontCatalog.options.size)
    }

    @Test
    fun `FontCatalog contains notoserifhebrew`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("notoserifhebrew" in codes)
    }

    @Test
    fun `FontCatalog contains frankruhllibre`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("frankruhllibre" in codes)
    }

    @Test
    fun `FontCatalog contains Mekorot Rashi replacement`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("mekorotrashi" in codes)
    }

    @Test
    fun `FontCatalog contains taameyashkenaz`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("taameyashkenaz" in codes)
    }

    @Test
    fun `FontCatalog contains tinos`() {
        val codes = FontCatalog.options.map { it.code }
        assertTrue("tinos" in codes)
    }

    @Test
    fun `all font codes are unique`() {
        val codes = FontCatalog.options.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `all font codes are non-empty`() {
        FontCatalog.options.forEach { option ->
            assertTrue(option.code.isNotEmpty(), "Font code should not be empty")
        }
    }

    @Test
    fun `FontOption equals works correctly`() {
        val option1 = FontCatalog.options[0]
        val option2 = FontCatalog.options[0]
        val option3 = FontCatalog.options[1]

        assertEquals(option1, option2)
        assertTrue(option1 != option3)
    }

    @Test
    fun `FontCatalog options list is not empty`() {
        assertTrue(FontCatalog.options.isNotEmpty())
    }
}
