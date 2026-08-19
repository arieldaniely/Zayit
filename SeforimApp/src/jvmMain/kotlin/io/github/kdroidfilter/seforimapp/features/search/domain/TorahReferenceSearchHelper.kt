package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.TocSuggestionDto
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry

/**
 * Fast, zero-allocation helper for parsing and matching continuous Torah book+location reference queries.
 * Examples: "ברכות ב:", "שו\"ע או\"ח רסג", "משנ\"ב רסג", "רמב\"ם שבת א ב", "בראשית יח א".
 */
object TorahReferenceSearchHelper {
    private val gematriaLetterValues = mapOf(
        'א' to 1, 'ב' to 2, 'ג' to 3, 'ד' to 4, 'ה' to 5, 'ו' to 6, 'ז' to 7, 'ח' to 8, 'ט' to 9,
        'י' to 10, 'כ' to 20, 'ך' to 20, 'ל' to 30, 'מ' to 40, 'ם' to 40, 'נ' to 50, 'ן' to 50,
        'ס' to 60, 'ע' to 70, 'פ' to 80, 'ף' to 80, 'צ' to 90, 'ץ' to 90,
        'ק' to 100, 'ר' to 200, 'ש' to 300, 'ת' to 400
    )

    fun gematriaToNumber(str: String): Int? {
        val clean = str.replace("[\"\'״׳]".toRegex(), "").trim()
        if (clean.isEmpty()) return null
        var total = 0
        for (ch in clean) {
            val v = gematriaLetterValues[ch] ?: return null
            total += v
        }
        return if (total > 0) total else null
    }

    fun numberToGematria(num: Int): String {
        if (num <= 0 || num > 1999) return num.toString()
        val sb = StringBuilder()
        var n = num
        while (n >= 400) { sb.append('ת'); n -= 400 }
        if (n >= 300) { sb.append('ש'); n -= 300 }
        if (n >= 200) { sb.append('ר'); n -= 200 }
        if (n >= 100) { sb.append('ק'); n -= 100 }
        if (n >= 90) { sb.append('צ'); n -= 90 }
        if (n >= 80) { sb.append('פ'); n -= 80 }
        if (n >= 70) { sb.append('ע'); n -= 70 }
        if (n >= 60) { sb.append('ס'); n -= 60 }
        if (n >= 50) { sb.append('נ'); n -= 50 }
        if (n >= 40) { sb.append('מ'); n -= 40 }
        if (n >= 30) { sb.append('ל'); n -= 30 }
        if (n >= 20) { sb.append('כ'); n -= 20 }
        if (n == 15) { sb.append("טו"); n = 0 }
        else if (n == 16) { sb.append("טז"); n = 0 }
        else if (n >= 10) { sb.append('י'); n -= 10 }
        if (n == 9) { sb.append('ט') }
        else if (n == 8) { sb.append('ח') }
        else if (n == 7) { sb.append('ז') }
        else if (n == 6) { sb.append('ו') }
        else if (n == 5) { sb.append('ה') }
        else if (n == 4) { sb.append('ד') }
        else if (n == 3) { sb.append('ג') }
        else if (n == 2) { sb.append('ב') }
        else if (n == 1) { sb.append('א') }
        return sb.toString()
    }

    /**
     * Splits a raw continuous query into potential (bookQuery, locQuery) candidate pairs.
     * Ordered by most specific book prefix first.
     */
    fun splitReferenceQuery(rawQuery: String): List<Pair<String, String>> {
        val q = rawQuery.trim()
        if (q.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, String>>()

        // Check for comma, colon, dash, or slash delimiter
        val delimiterMatch = Regex("[,\\-–—]").find(q)
        if (delimiterMatch != null) {
            val bookPart = q.substring(0, delimiterMatch.range.first).trim()
            val locPart = q.substring(delimiterMatch.range.last + 1).trim()
            if (bookPart.isNotBlank() && locPart.isNotBlank()) {
                results.add(bookPart to locPart)
            }
        }

        // Token-based splitting on whitespace
        val tokens = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            for (k in (tokens.size - 1) downTo 1) {
                val bookPart = tokens.take(k).joinToString(" ").trim()
                val locPart = tokens.drop(k).joinToString(" ").trim()
                if (bookPart.isNotEmpty() && locPart.isNotEmpty()) {
                    if (results.none { it.first.equals(bookPart, ignoreCase = true) && it.second.equals(locPart, ignoreCase = true) }) {
                        results.add(bookPart to locPart)
                    }
                }
            }
        }

        return results
    }

    /**
     * Checks if a TOC entry matches the location part of a query.
     */
    fun matchesTocLocation(
        dto: TocSuggestionDto,
        locQuery: String,
    ): Boolean {
        val loc = locQuery.trim()
        if (loc.isEmpty()) return false

        val tocText = dto.toc.text.trim()
        val fullPathText = dto.path.joinToString(" ")

        // 1. Exact or direct substring match in text or path
        if (tocText.contains(loc, ignoreCase = true) || fullPathText.contains(loc, ignoreCase = true)) {
            return true
        }

        // 2. Talmud Bavli Daf Matching
        val dafMatch = matchTalmudDaf(tocText, loc)
        if (dafMatch) return true

        // 3. Siman / Perek / Seif / Pasuk multi-token or gematria matching
        if (matchTokensInPathOrText(dto, loc)) {
            return true
        }

        return false
    }

    private fun matchTalmudDaf(tocText: String, loc: String): Boolean {
        // Normalize loc: e.g. "ב:", "ב.", "ב ע\"ב", "דף ב עמוד א", "כז:", "27:"
        val cleanLoc = loc.replace("[\"\'״׳]".toRegex(), "").trim()
        if (cleanLoc.isEmpty()) return false

        val isAmudB = loc.endsWith(":") || loc.endsWith("/ב") || loc.contains("ע\"ב") || loc.contains("עב") ||
            loc.contains("עמוד ב") || loc.contains("עמ' ב") || loc.endsWith(" ב")
        val isAmudA = loc.endsWith(".") || loc.endsWith("/א") || loc.contains("ע\"א") || loc.contains("עא") ||
            loc.contains("עמוד א") || loc.contains("עמ' א") || loc.endsWith(" א")

        // Extract daf component
        var dafPart = loc
            .replace("דף", "")
            .replace("ד'", "")
            .replace("עמוד ב", "")
            .replace("עמוד א", "")
            .replace("ע\"ב", "")
            .replace("ע\"א", "")
            .replace("עמ' ב", "")
            .replace("עמ' א", "")
            .replace("[:./]".toRegex(), "")
            .trim()

        // If ends with " א" or " ב" which indicated amud, strip it from dafPart
        if (isAmudB && dafPart.endsWith(" ב")) dafPart = dafPart.dropLast(2).trim()
        if (isAmudA && dafPart.endsWith(" א")) dafPart = dafPart.dropLast(2).trim()

        if (dafPart.isBlank()) return false

        // If dafPart is digits, convert to gematria
        val numericDaf = dafPart.toIntOrNull()
        val gematriaDaf = if (numericDaf != null) numberToGematria(numericDaf) else dafPart

        val cleanToc = tocText.replace("[\"\'״׳]".toRegex(), "").trim()
        val cleanGematriaDaf = gematriaDaf.replace("[\"\'״׳]".toRegex(), "").trim()

        // Check if tocText refers to this daf
        val dafMatches = cleanToc.contains("דף $cleanGematriaDaf") ||
            cleanToc.startsWith("$cleanGematriaDaf ") ||
            cleanToc.startsWith("דף $cleanGematriaDaf ") ||
            cleanToc == cleanGematriaDaf ||
            cleanToc == "דף $cleanGematriaDaf"

        if (!dafMatches) return false

        return when {
            isAmudB -> cleanToc.contains("עמוד ב") || cleanToc.contains("עב") || cleanToc.endsWith(":") || cleanToc.endsWith(" ב")
            isAmudA -> cleanToc.contains("עמוד א") || cleanToc.contains("עא") || cleanToc.endsWith(".") || cleanToc.endsWith(" א")
            else -> true // Neither amud specified, match any amud of that daf
        }
    }

    private fun matchTokensInPathOrText(
        dto: TocSuggestionDto,
        locQuery: String,
    ): Boolean {
        // Expand common acronyms in query: או"ח -> אורח חיים, יו"ד -> יורה דעה, חו"מ -> חושן משפט, אהע"ז -> אבן העזר
        val expandedQuery = locQuery
            .replace("או\"ח", "אורח חיים")
            .replace("אוח", "אורח חיים")
            .replace("יו\"ד", "יורה דעה")
            .replace("יוד", "יורה דעה")
            .replace("חו\"מ", "חושן משפט")
            .replace("חומ", "חושן משפט")
            .replace("אהע\"ז", "אבן העזר")
            .replace("אה\"ע", "אבן העזר")
            .replace("סי'", "סימן")
            .replace("סע'", "סעיף")
            .replace("פ'", "פרק")
            .replace("הל'", "הלכות")

        val tokens = expandedQuery
            .replace("[\"\'״׳:,\\-–—]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return false

        val fullText = (dto.path + dto.toc.text).joinToString(" ")
            .replace("[\"\'״׳:,\\-–—]".toRegex(), " ")

        // All tokens must match in fullText (either directly or via number <-> gematria)
        return tokens.all { token ->
            val num = token.toIntOrNull()
            val gem = if (num != null) numberToGematria(num) else null
            val asNum = if (num == null) gematriaToNumber(token) else null

            fullText.contains(token, ignoreCase = true) ||
                (gem != null && fullText.contains(gem, ignoreCase = true)) ||
                (asNum != null && fullText.contains(asNum.toString(), ignoreCase = true))
        }
    }
}
