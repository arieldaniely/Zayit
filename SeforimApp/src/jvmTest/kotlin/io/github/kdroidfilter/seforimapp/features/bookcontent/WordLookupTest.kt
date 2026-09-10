package io.github.kdroidfilter.seforimapp.features.bookcontent

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordLookupTest {
    @Test
    fun `bundled dictionaries load and return real entries`() =
        runTest {
            WordLookupIndex.preload()

            assertTrue(WordLookupIndex.lookup("איכא")?.dictionarySenses?.isNotEmpty() == true)
            assertTrue(WordLookupIndex.lookup("רש״י")?.acronymExpansions?.isNotEmpty() == true)
        }

    @Test
    fun `normalization removes cantillation and accepts typographic gershayim`() {
        assertEquals("רש\"י", normalizeLookupKey("  רַשִּׁ״י, "))
    }

    @Test
    fun `dictionary markup becomes separate readable senses`() {
        val snapshot =
            buildWordLookupSnapshot(
                dictionaryJson = """{"מילון פשיטא":[{"אִיכָּא":"{אִיכָּא} יש כאן *** יש"}]}""",
                acronymsJson = "{}",
            )

        assertEquals(
            listOf("אִיכָּא — יש כאן", "יש"),
            lookupWord(snapshot, "איכא")?.dictionarySenses,
        )
    }

    @Test
    fun `acronym alternatives are formatted and deduplicated`() {
        val snapshot =
            buildWordLookupSnapshot(
                dictionaryJson = """{"מילון פשיטא":[]}""",
                acronymsJson = """{"רש\"י":["רבי שלמה יצחקי","רבי שלמה יצחקי","ר' שלמה יצחקי"]}""",
            )

        val result = lookupWord(snapshot, "רש״י")

        assertEquals("רש״י", result?.term)
        assertEquals(listOf("רבי שלמה יצחקי", "רבי שלמה יצחקי", "ר׳ שלמה יצחקי").distinct(), result?.acronymExpansions)
    }

    @Test
    fun `acronym map is skipped when selection has no gershayim`() {
        val snapshot = WordLookupSnapshot(acronyms = mapOf("איכא" to listOf("לא אמור להופיע")))

        assertNull(lookupWord(snapshot, "איכא"))
    }

    @Test
    fun `large and multiline selections are ignored`() {
        val snapshot = WordLookupSnapshot(dictionary = mapOf("איכא" to listOf("יש")))

        assertNull(lookupWord(snapshot, "איכא\nהתם"))
        assertNull(lookupWord(snapshot, "א".repeat(81)))
    }

    @Test
    fun `token extraction resolves the word directly under the pointer`() {
        val text = "אמר רש״י, ואיכא למימר"

        assertEquals("רש״י", extractLookupToken(text, text.indexOf('ש')))
        assertEquals("רש״י", extractLookupToken(text, text.indexOf(',')))
        assertEquals("ואיכא", extractLookupToken(text, text.indexOf("איכא") + 2))
        assertEquals("", extractLookupToken("   ", 1))
    }

    @Test
    fun `lookup falls back to a base word after Hebrew prefixes`() {
        val snapshot = WordLookupSnapshot(dictionary = mapOf("איכא" to listOf("יש")))

        val result = lookupWord(snapshot, "ואיכא")

        assertEquals("איכא", result?.term)
        assertEquals(listOf("יש"), result?.dictionarySenses)
    }
}
