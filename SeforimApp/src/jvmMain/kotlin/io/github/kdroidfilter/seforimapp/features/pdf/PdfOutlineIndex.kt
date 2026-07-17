package io.github.kdroidfilter.seforimapp.features.pdf

/** A flattened PDF bookmark with its zero-based destination page. */
internal data class PdfOutlineEntry(
    val title: String,
    val level: Int,
    val pageIndex: Int,
)

/** A text-edition heading that can be selected when the matching PDF page becomes visible. */
internal data class PdfTextAnchor(
    val title: String,
    val lineId: Long,
)

/**
 * Matches PDF bookmarks to the references used by the text edition.
 *
 * The Bavli data uses more than one equivalent spelling for a daf (for example `ב.`,
 * `דף ב עמוד א`, and a full line reference such as `ברכות ב., ג`). Exact normalized titles are
 * preferred; a parsed daf/amud key is the fallback. Keeping this logic outside the UI makes
 * bookmark navigation deterministic and cheap.
 */
internal class PdfOutlineIndex(
    entries: List<PdfOutlineEntry>,
) {
    val entries: List<PdfOutlineEntry> = entries.filter { it.title.isNotBlank() }.sortedBy { it.pageIndex }

    private val entriesByTitle: Map<String, PdfOutlineEntry> =
        this.entries
            .flatMap { entry -> titleCandidates(entry.title).map { it to entry } }
            .distinctBy { it.first }
            .toMap()

    private val entriesByDaf: Map<DafReference, PdfOutlineEntry> =
        this.entries.mapNotNull { entry -> parseDafReference(entry.title)?.let { it to entry } }.distinctBy { it.first }.toMap()

    fun pageFor(references: Iterable<String>): Int? {
        val values = references.filter { it.isNotBlank() }
        values.forEach { reference ->
            titleCandidates(reference).firstNotNullOfOrNull(entriesByTitle::get)?.let { return it.pageIndex }
        }
        values.forEach { reference ->
            parseDafReference(reference)?.let(entriesByDaf::get)?.let { return it.pageIndex }
        }
        return null
    }

    fun entryForPage(pageIndex: Int): PdfOutlineEntry? = entries.lastOrNull { it.pageIndex <= pageIndex } ?: entries.firstOrNull()

    fun textAnchorForPage(
        pageIndex: Int,
        anchors: List<PdfTextAnchor>,
    ): PdfTextAnchor? {
        val outline = entryForPage(pageIndex) ?: return null
        val anchorsByTitle =
            anchors
                .flatMap { anchor -> titleCandidates(anchor.title).map { it to anchor } }
                .distinctBy { it.first }
                .toMap()
        titleCandidates(outline.title).firstNotNullOfOrNull(anchorsByTitle::get)?.let { return it }

        val daf = parseDafReference(outline.title) ?: return null
        return anchors.firstOrNull { parseDafReference(it.title) == daf }
    }
}

private data class DafReference(
    val daf: String,
    val amud: Char,
)

private fun titleCandidates(value: String): List<String> =
    buildList {
        add(normalizeTitle(value))
        if (',' in value) add(normalizeTitle(value.substringBeforeLast(',')))
        val withoutDafPrefix = value.replace(Regex("^\\s*דף\\s+"), "")
        add(normalizeTitle(withoutDafPrefix))
    }.filter { it.isNotEmpty() }.distinct()

private fun normalizeTitle(value: String): String =
    value
        .trim()
        .replace('־', ' ')
        .replace(Regex("[\\s\\-–—_׳״'\"]+"), "")
        .lowercase()

private fun parseDafReference(value: String): DafReference? {
    val normalized = value.trim().replace('־', ' ')

    PHRASE_DAF.find(normalized)?.let { match ->
        return DafReference(normalizeDafNumber(match.groupValues[1]), match.groupValues[2].last())
    }
    SHORT_PHRASE_DAF.find(normalized)?.let { match ->
        return DafReference(normalizeDafNumber(match.groupValues[1]), match.groupValues[2].last())
    }
    ABBREVIATED_DAF.find(normalized)?.let { match ->
        return DafReference(normalizeDafNumber(match.groupValues[1]), match.groupValues[2].last())
    }
    PUNCTUATED_DAF.find(normalized)?.let { match ->
        return DafReference(normalizeDafNumber(match.groupValues[1]), if (match.groupValues[2] == ".") 'א' else 'ב')
    }
    LATIN_DAF.find(normalized)?.let { match ->
        val amud = if (match.groupValues[2].equals("a", ignoreCase = true)) 'א' else 'ב'
        return DafReference(match.groupValues[1].trimStart('0').ifEmpty { "0" }, amud)
    }
    return null
}

private fun normalizeDafNumber(value: String): String =
    value.replace(Regex("[\\s׳״'\"]+"), "").trimStart('0').ifEmpty { "0" }

private val PHRASE_DAF = Regex("(?:^|\\s)דף\\s+([א-תךםןףץ0-9׳״'\"]{1,8})\\s+עמוד\\s+([אב])(?:\\s|$)")
private val SHORT_PHRASE_DAF =
    Regex("(?:^|\\s)([א-תךםןףץ0-9׳״'\"]{1,8})\\s+עמוד\\s+([אב])(?:\\s|$)")
private val ABBREVIATED_DAF = Regex("(?:^|\\s)([א-תךםןףץ0-9׳״'\"]{1,8})\\s+ע[׳״'\"]?\\s*([אב])(?:\\s|$)")
private val PUNCTUATED_DAF =
    Regex("(?:^|\\s)([א-תךםןףץ0-9׳״'\"]{1,8})\\s*([.:])(?=\\s*(?:,|$))")
private val LATIN_DAF = Regex("(?:^|\\s)([0-9]+)\\s*([abAB])(?=\\s*(?:,|$))")
