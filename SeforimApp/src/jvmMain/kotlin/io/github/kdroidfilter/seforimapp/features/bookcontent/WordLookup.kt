package io.github.kdroidfilter.seforimapp.features.bookcontent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.word_lookup_acronym_section
import seforimapp.seforimapp.generated.resources.word_lookup_close
import seforimapp.seforimapp.generated.resources.word_lookup_dictionary_section
import seforimapp.seforimapp.generated.resources.word_lookup_title
import java.text.Normalizer

internal data class WordLookupResult(
    val term: String,
    val dictionarySenses: List<String>,
    val acronymExpansions: List<String>,
)

internal data class WordLookupSnapshot(
    val dictionary: Map<String, List<String>> = emptyMap(),
    val acronyms: Map<String, List<String>> = emptyMap(),
)

/**
 * An immutable, in-memory index for the bundled Aramaic dictionary and acronym database.
 *
 * The JSON resources are parsed once, off the UI thread. Opening a context menu therefore does
 * normally performs one dictionary HashMap lookup and, only when the selection contains
 * gershayim, one acronym HashMap lookup. A bounded prefix fallback runs only after an exact miss.
 */
internal object WordLookupIndex {
    private val loadMutex = Mutex()

    @Volatile
    private var snapshot = WordLookupSnapshot()

    @Volatile
    private var loaded = false

    suspend fun preload() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            val (dictionaryJson, acronymsJson) =
                withContext(Dispatchers.IO) {
                    Res.readBytes("files/dictionary.json").decodeToString() to
                        Res.readBytes("files/acronyms.json").decodeToString()
                }
            snapshot =
                withContext(Dispatchers.Default) {
                    buildWordLookupSnapshot(dictionaryJson, acronymsJson)
                }
            loaded = true
        }
    }

    fun lookup(selection: String): WordLookupResult? = lookupWord(snapshot, selection)
}

internal fun buildWordLookupSnapshot(
    dictionaryJson: String,
    acronymsJson: String,
): WordLookupSnapshot {
    val dictionary = LinkedHashMap<String, MutableList<String>>()
    val dictionaryEntries =
        Json.parseToJsonElement(dictionaryJson)
            .jsonObject["מילון פשיטא"]
            ?.jsonArray
            .orEmpty()
    for (entry in dictionaryEntries) {
        for ((term, definition) in entry.jsonObject) {
            val key = normalizeLookupKey(term)
            if (key.isNotEmpty()) {
                dictionary
                    .getOrPut(key, ::mutableListOf)
                    .addAll(formatDictionarySenses(definition.jsonPrimitive.content))
            }
        }
    }

    val acronyms = LinkedHashMap<String, List<String>>()
    for ((term, expansions) in Json.parseToJsonElement(acronymsJson).jsonObject) {
        val key = normalizeLookupKey(term)
        if (key.isNotEmpty()) {
            acronyms[key] =
                expansions.jsonArray
                    .map { formatDisplayText(it.jsonPrimitive.content) }
                    .filter(String::isNotBlank)
                    .distinct()
        }
    }

    return WordLookupSnapshot(
        dictionary = dictionary.mapValues { (_, values) -> values.distinct() },
        acronyms = acronyms,
    )
}

internal fun lookupWord(snapshot: WordLookupSnapshot, selection: String): WordLookupResult? {
    // A lookup is meaningful only for a short word/phrase; this also prevents large selections
    // from creating normalization work while the menu is being assembled.
    if (
        selection.isBlank() ||
        selection.length > MAX_LOOKUP_LENGTH ||
        '\n' in selection ||
        '\r' in selection
    ) {
        return null
    }

    // Every key in Acronyms.json contains gershayim. This cheap character scan deliberately
    // precedes touching the much larger acronym map.
    val canBeAcronym = selection.any(::isDoubleQuote)
    for (key in lookupCandidates(normalizeLookupKey(selection))) {
        val dictionarySenses = snapshot.dictionary[key].orEmpty()
        val acronymExpansions =
            if (canBeAcronym) {
                snapshot.acronyms[key].orEmpty()
            } else {
                emptyList()
            }

        if (dictionarySenses.isNotEmpty() || acronymExpansions.isNotEmpty()) {
            return WordLookupResult(
                term = formatDisplayText(key),
                dictionarySenses = dictionarySenses,
                acronymExpansions = acronymExpansions,
            )
        }
    }
    return null
}

private fun lookupCandidates(key: String): Sequence<String> =
    sequence {
        if (key.isEmpty()) return@sequence
        yield(key)

        var stripped = key
        repeat(MAX_PREFIXES_TO_STRIP) {
            if (stripped.length <= MIN_BASE_WORD_LENGTH || stripped.first() !in HEBREW_PREFIXES) {
                return@sequence
            }
            stripped = stripped.drop(1)
            yield(stripped)
        }
    }

/** Extracts the Hebrew token around a text-layout offset for a direct secondary click. */
internal fun extractLookupToken(text: String, rawOffset: Int): String {
    if (text.isEmpty()) return ""
    var offset = rawOffset.coerceIn(0, text.lastIndex)
    if (!isLookupTokenChar(text[offset]) && offset > 0 && isLookupTokenChar(text[offset - 1])) {
        offset--
    }
    if (!isLookupTokenChar(text[offset])) return ""

    var start = offset
    while (start > 0 && isLookupTokenChar(text[start - 1])) start--
    var end = offset + 1
    while (end < text.length && isLookupTokenChar(text[end])) end++
    return text.substring(start, end)
}

internal fun normalizeLookupKey(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
    val out = StringBuilder(normalized.length)
    var pendingSpace = false
    for (char in normalized.trim(*LOOKUP_EDGE_CHARS)) {
        when {
            char.isWhitespace() -> pendingSpace = out.isNotEmpty()
            isHebrewMark(char) -> Unit
            else -> {
                if (pendingSpace) out.append(' ')
                out.append(
                    when (char) {
                        '״', '“', '”', '„', '‟' -> '"'
                        '׳', '‘', '’', '‛', '`', '´' -> '\''
                        else -> char
                    },
                )
                pendingSpace = false
            }
        }
    }
    return out.toString()
}

private fun formatDictionarySenses(value: String): List<String> =
    value
        .split(Regex("\\s*\\*{3}\\s*"))
        .map { sense ->
            val trimmed = sense.trim()
            val vocalized = VOCALIZED_FORM.matchEntire(trimmed)
            if (vocalized == null) {
                formatDisplayText(trimmed)
            } else {
                val form = formatDisplayText(vocalized.groupValues[1].trim())
                val meaning = formatDisplayText(vocalized.groupValues[2].trim())
                if (meaning.isEmpty()) form else "$form — $meaning"
            }
        }.filter(String::isNotBlank)

private fun formatDisplayText(value: String): String =
    value
        .trim()
        .replace('"', '״')
        .replace('\'', '׳')
        .replace("_", "־")
        .replace(Regex("\\s*/\\s*"), " / ")

private fun isDoubleQuote(char: Char): Boolean = char == '"' || char in TYPOGRAPHIC_DOUBLE_QUOTES

private fun isLookupTokenChar(char: Char): Boolean =
    char.code in 0x05D0..0x05EA ||
        isHebrewMark(char) ||
        isDoubleQuote(char) ||
        char == '\'' ||
        char == '׳'

private fun isHebrewMark(char: Char): Boolean =
    char.code in 0x0591..0x05BD ||
        char.code == 0x05BF ||
        char.code in 0x05C1..0x05C7

private val VOCALIZED_FORM = Regex("^\\{([^}]*)}(?:\\s*)(.*)$")
private val TYPOGRAPHIC_DOUBLE_QUOTES = charArrayOf('״', '“', '”', '„', '‟')
private val LOOKUP_EDGE_CHARS =
    charArrayOf(
        ' ', '\t', '\n', '\r', '.', ',', ';', ':', '!', '?', '…',
        '(', ')', '[', ']', '{', '}', '<', '>', '"', '״', '“', '”',
    )
private const val MAX_LOOKUP_LENGTH = 80
private const val MAX_PREFIXES_TO_STRIP = 2
private const val MIN_BASE_WORD_LENGTH = 2
private const val HEBREW_PREFIXES = "ובכלמשה"

@Composable
internal fun WordLookupDialog(
    result: WordLookupResult,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .width(500.dp)
                    .heightIn(max = 600.dp)
                    .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(16.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.word_lookup_title), color = JewelTheme.globalColors.text.info)
            Text(result.term, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (result.dictionarySenses.isNotEmpty()) {
                        LookupSection(
                            title = stringResource(Res.string.word_lookup_dictionary_section),
                            entries = result.dictionarySenses,
                        )
                    }
                    if (result.acronymExpansions.isNotEmpty()) {
                        LookupSection(
                            title = stringResource(Res.string.word_lookup_acronym_section),
                            entries = result.acronymExpansions,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DefaultButton(onClick = onDismiss) { Text(stringResource(Res.string.word_lookup_close)) }
            }
        }
    }
}

@Composable
private fun LookupSection(
    title: String,
    entries: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = JewelTheme.globalColors.text.info)
        entries.forEach { entry ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("•", color = JewelTheme.globalColors.text.info)
                Text(entry, modifier = Modifier.weight(1f))
            }
        }
    }
}
