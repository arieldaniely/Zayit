package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.features.search.TocSuggestionDto

/**
 * Helper for parsing and matching continuous Torah book+location reference queries.
 * Examples: "ברכות ב:", "שו\"ע או\"ח רסג", "משנ\"ב רסג", "רמב\"ם שבת א ב", "בראשית יח א".
 */
object TorahReferenceSearchHelper {
    private val gematriaLetterValues =
        mapOf(
            'א' to 1,
            'ב' to 2,
            'ג' to 3,
            'ד' to 4,
            'ה' to 5,
            'ו' to 6,
            'ז' to 7,
            'ח' to 8,
            'ט' to 9,
            'י' to 10,
            'כ' to 20,
            'ך' to 20,
            'ל' to 30,
            'מ' to 40,
            'ם' to 40,
            'נ' to 50,
            'ן' to 50,
            'ס' to 60,
            'ע' to 70,
            'פ' to 80,
            'ף' to 80,
            'צ' to 90,
            'ץ' to 90,
            'ק' to 100,
            'ר' to 200,
            'ש' to 300,
            'ת' to 400,
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
        while (n >= 400) {
            sb.append('ת')
            n -= 400
        }
        if (n >= 300) {
            sb.append('ש')
            n -= 300
        }
        if (n >= 200) {
            sb.append('ר')
            n -= 200
        }
        if (n >= 100) {
            sb.append('ק')
            n -= 100
        }
        if (n >= 90) {
            sb.append('צ')
            n -= 90
        }
        if (n >= 80) {
            sb.append('פ')
            n -= 80
        }
        if (n >= 70) {
            sb.append('ע')
            n -= 70
        }
        if (n >= 60) {
            sb.append('ס')
            n -= 60
        }
        if (n >= 50) {
            sb.append('נ')
            n -= 50
        }
        if (n >= 40) {
            sb.append('מ')
            n -= 40
        }
        if (n >= 30) {
            sb.append('ל')
            n -= 30
        }
        if (n >= 20) {
            sb.append('כ')
            n -= 20
        }
        if (n == 15) {
            sb.append("טו")
            n = 0
        } else if (n == 16) {
            sb.append("טז")
            n = 0
        } else if (n >= 10) {
            sb.append('י')
            n -= 10
        }
        if (n == 9) {
            sb.append('ט')
        } else if (n == 8) {
            sb.append('ח')
        } else if (n == 7) {
            sb.append('ז')
        } else if (n == 6) {
            sb.append('ו')
        } else if (n == 5) {
            sb.append('ה')
        } else if (n == 4) {
            sb.append('ד')
        } else if (n == 3) {
            sb.append('ג')
        } else if (n == 2) {
            sb.append('ב')
        } else if (n == 1) {
            sb.append('א')
        }
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

    private val quotes = Regex("[\"'״׳]")
    private val separators = Regex("[\\s,:./\\-–—]+")
    private val aliases =
        mapOf(
            "אוח" to "אורח חיים",
            "יוד" to "יורה דעה",
            "חומ" to "חושן משפט",
            "אהעז" to "אבן העזר",
            "אהע" to "אבן העזר",
            "סי" to "סימן",
            "סע" to "סעיף",
            "פ" to "פרק",
            "הל" to "הלכות",
        )

    private fun tokens(text: String): List<String> = text.replace(quotes, "").split(separators).filter(String::isNotBlank)

    private fun number(token: String): Int? {
        token.toIntOrNull()?.let { return it.takeIf { value -> value > 0 } }
        val value = gematriaToNumber(token) ?: return null
        // Ordinary Hebrew words must not become numbers just because their letters have values.
        return value.takeIf { numberToGematria(it) == token }
    }

    /** Do not let prefix book search consume the chapter number as part of a title word. */
    fun hasUnmatchedLocationSuffix(
        bookTitle: String,
        bookQuery: String,
    ): Boolean {
        val queryTokens = tokens(bookQuery)
        if (queryTokens.size < 2) return false
        val suffixNumber = number(queryTokens.last()) ?: return false
        return tokens(bookTitle).none { number(it) == suffixNumber }
    }

    private data class Daf(
        val number: Int,
        val amud: String?,
    )

    private val dafPattern =
        Regex("^(?:דף\\s+)?([א-ת]+|[0-9]+)(?:\\s*([:.])|\\s*/\\s*([אב])|\\s+(?:עמוד\\s+|עמ\\s+|ע)?([אב]))?$")

    private fun parseDaf(text: String): Daf? {
        val clean = text.replace(quotes, "").trim()
        val match = dafPattern.matchEntire(clean) ?: return null
        val dafNumber = number(match.groupValues[1]) ?: return null
        val amud =
            when (match.groupValues[2]) {
                ":" -> "ב"
                "." -> "א"
                else -> match.groupValues[3].ifEmpty { match.groupValues[4] }.ifEmpty { null }
            }
        return Daf(dafNumber, amud)
    }

    /** Matches whole tokens in path order, with the final token belonging to this entry. */
    fun matchesTocLocation(
        dto: TocSuggestionDto,
        locQuery: String,
        allowTextPrefix: Boolean = false,
    ): Boolean {
        val loc = locQuery.trim()
        if (loc.isEmpty()) return false
        val tocDaf = parseDaf(dto.toc.text)
        val queryDaf = parseDaf(loc)
        val isDafEntry = tocDaf?.amud != null || tokens(dto.toc.text).firstOrNull() == "דף"
        if (tocDaf != null && queryDaf != null && (isDafEntry || queryDaf.amud == null)) {
            return tocDaf.number == queryDaf.number && (queryDaf.amud == null || tocDaf.amud == queryDaf.amud)
        }
        // An explicit daf side must never fall through to a chapter/verse token match.
        if (queryDaf?.amud != null && (loc.any { it in ":./" } || tokens(loc).any { it in setOf("עא", "עב", "עמוד", "עמ", "דף") })) {
            return false
        }

        val queryTokens =
            loc.split(separators).filter(String::isNotBlank).flatMap { rawToken ->
                val token = rawToken.replace(quotes, "")
                val expand = token !in setOf("סי", "סע", "פ", "הל") || quotes.containsMatchIn(rawToken)
                if (expand) aliases[token]?.let(::tokens) ?: listOf(token) else listOf(token)
            }
        if (queryTokens.isEmpty()) return false
        val path = dto.path.toMutableList()
        if (path.lastOrNull() == dto.toc.text) path.removeAt(path.lastIndex)
        val parentTokens = path.flatMap(::tokens)
        val allTokens = parentTokens + tokens(dto.toc.text)
        var nextIndex = 0
        for ((index, token) in queryTokens.withIndex()) {
            val tokenNumber = number(token)
            val start = if (index == queryTokens.lastIndex) maxOf(nextIndex, parentTokens.size) else nextIndex
            val found =
                (start until allTokens.size).firstOrNull { position ->
                    val candidate = allTokens[position]
                    candidate.equals(token, ignoreCase = true) ||
                        (tokenNumber != null && tokenNumber == number(candidate)) ||
                        (
                            allowTextPrefix &&
                                index == queryTokens.lastIndex &&
                                tokenNumber == null &&
                                candidate.startsWith(token, ignoreCase = true)
                        )
                } ?: return false
            nextIndex = found + 1
        }
        return true
    }
}
