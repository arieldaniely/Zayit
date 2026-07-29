package io.github.kdroidfilter.seforimapp.hebrewcalendar

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DateTimeException
import java.time.LocalDate

/** Parses the date formats people commonly type in the calendar search field. */
fun parseFlexibleDate(
    input: String,
    mode: CalendarMode,
    currentDate: LocalDate = LocalDate.now(),
): LocalDate? =
    when (mode) {
        CalendarMode.GREGORIAN -> parseGregorianDate(input, currentDate)
        CalendarMode.HEBREW -> parseHebrewDate(input, currentDate)
    }

private fun parseGregorianDate(input: String, currentDate: LocalDate): LocalDate? {
    val parts = input.trim().split(Regex("[\\s./_-]+"))
    if (parts.size !in 2..3 || parts.any { part -> part.isBlank() || part.any { !it.isDigit() } }) return null

    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val (day, month, year) =
        when {
            parts.size == 2 -> Triple(numbers[0], numbers[1], currentDate.year)
            parts[0].length == 4 -> Triple(numbers[2], numbers[1], numbers[0])
            else -> Triple(numbers[0], numbers[1], expandShortYear(numbers[2], parts[2].length, currentDate.year))
        }
    return try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }
}

private fun expandShortYear(value: Int, digits: Int, currentYear: Int): Int {
    if (digits > 2) return value
    val century = (currentYear / 100) * 100
    val candidate = century + value
    return when {
        candidate - currentYear > 50 -> candidate - 100
        currentYear - candidate > 50 -> candidate + 100
        else -> candidate
    }
}

private data class HebrewMonthMatch(
    val tokenIndex: Int,
    val tokenCount: Int,
    val monthKind: HebrewMonthKind,
)

private enum class HebrewMonthKind {
    NISSAN, IYAR, SIVAN, TAMMUZ, AV, ELUL, TISHREI, CHESHVAN, KISLEV, TEVET, SHEVAT, ADAR, ADAR_I, ADAR_II,
}

private val singleTokenHebrewMonths =
    mapOf(
        "\u05E0\u05D9\u05E1\u05DF" to HebrewMonthKind.NISSAN,
        "\u05D0\u05D9\u05E8" to HebrewMonthKind.IYAR,
        "\u05D0\u05D9\u05D9\u05E8" to HebrewMonthKind.IYAR,
        "\u05E1\u05D9\u05D5\u05DF" to HebrewMonthKind.SIVAN,
        "\u05E1\u05D9\u05D5\u05D5\u05DF" to HebrewMonthKind.SIVAN,
        "\u05EA\u05DE\u05D5\u05D6" to HebrewMonthKind.TAMMUZ,
        "\u05D0\u05D1" to HebrewMonthKind.AV,
        "\u05D0\u05DC\u05D5\u05DC" to HebrewMonthKind.ELUL,
        "\u05EA\u05E9\u05E8\u05D9" to HebrewMonthKind.TISHREI,
        "\u05D7\u05E9\u05D5\u05DF" to HebrewMonthKind.CHESHVAN,
        "\u05D7\u05E9\u05D5\u05D5\u05DF" to HebrewMonthKind.CHESHVAN,
        "\u05DE\u05E8\u05D7\u05E9\u05D5\u05DF" to HebrewMonthKind.CHESHVAN,
        "\u05DE\u05E8\u05D7\u05E9\u05D5\u05D5\u05DF" to HebrewMonthKind.CHESHVAN,
        "\u05DB\u05E1\u05DC\u05D5" to HebrewMonthKind.KISLEV,
        "\u05DB\u05E1\u05DC\u05D9\u05D5" to HebrewMonthKind.KISLEV,
        "\u05D8\u05D1\u05EA" to HebrewMonthKind.TEVET,
        "\u05E9\u05D1\u05D8" to HebrewMonthKind.SHEVAT,
        "\u05D0\u05D3\u05E8" to HebrewMonthKind.ADAR,
    )

private fun parseHebrewDate(input: String, currentDate: LocalDate): LocalDate? {
    val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size < 2) return null
    val monthMatch = findHebrewMonth(tokens) ?: return null
    if (monthMatch.tokenIndex == 0) return null

    val dayText = tokens.take(monthMatch.tokenIndex).joinToString("")
    val yearText = tokens.drop(monthMatch.tokenIndex + monthMatch.tokenCount).joinToString("")
    val day = parseHebrewNumber(dayText, isYear = false) ?: return null
    val currentHebrewYear = JewishDate(currentDate).jewishYear
    val year = if (yearText.isBlank()) currentHebrewYear else parseHebrewNumber(yearText, isYear = true) ?: return null
    val month = resolveJewishMonth(monthMatch.monthKind, year) ?: return null
    val daysInMonth = JewishDate().apply { setJewishDate(year, month, 1) }.daysInJewishMonth
    if (day !in 1..daysInMonth) return null

    return try {
        JewishDate().apply { setJewishDate(year, month, day) }.localDate
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun findHebrewMonth(tokens: List<String>): HebrewMonthMatch? {
    for (index in tokens.indices) {
        val first = normalizeHebrewWord(tokens[index], allowPrefix = true)
        if (first == "\u05D0\u05D3\u05E8" && index + 1 < tokens.size) {
            when (normalizeHebrewWord(tokens[index + 1])) {
                "\u05D0", "\u05E8\u05D0\u05E9\u05D5\u05DF", "1" -> return HebrewMonthMatch(index, 2, HebrewMonthKind.ADAR_I)
                "\u05D1", "\u05E9\u05E0\u05D9", "2" -> return HebrewMonthMatch(index, 2, HebrewMonthKind.ADAR_II)
            }
        }
        singleTokenHebrewMonths[first]?.let { return HebrewMonthMatch(index, 1, it) }
    }
    return null
}

private fun normalizeHebrewWord(value: String, allowPrefix: Boolean = false): String {
    val normalized = value.filter { it.isLetterOrDigit() }
    if (allowPrefix && normalized.length > 2 && normalized.first() in setOf('\u05D1', '\u05DC')) {
        val withoutPrefix = normalized.drop(1)
        if (withoutPrefix in singleTokenHebrewMonths) return withoutPrefix
    }
    return normalized
}

private fun resolveJewishMonth(kind: HebrewMonthKind, year: Int): Int? {
    val isLeap = ((7 * year) + 1) % 19 < 7
    return when (kind) {
        HebrewMonthKind.NISSAN -> 1
        HebrewMonthKind.IYAR -> 2
        HebrewMonthKind.SIVAN -> 3
        HebrewMonthKind.TAMMUZ -> 4
        HebrewMonthKind.AV -> 5
        HebrewMonthKind.ELUL -> 6
        HebrewMonthKind.TISHREI -> 7
        HebrewMonthKind.CHESHVAN -> 8
        HebrewMonthKind.KISLEV -> 9
        HebrewMonthKind.TEVET -> 10
        HebrewMonthKind.SHEVAT -> 11
        HebrewMonthKind.ADAR, HebrewMonthKind.ADAR_I -> 12
        HebrewMonthKind.ADAR_II -> if (isLeap) 13 else null
    }
}

private val hebrewDigitValues =
    mapOf(
        '\u05D0' to 1, '\u05D1' to 2, '\u05D2' to 3, '\u05D3' to 4, '\u05D4' to 5, '\u05D5' to 6, '\u05D6' to 7, '\u05D7' to 8, '\u05D8' to 9,
        '\u05D9' to 10, '\u05DB' to 20, '\u05DA' to 20, '\u05DC' to 30, '\u05DE' to 40, '\u05DD' to 40, '\u05E0' to 50, '\u05DF' to 50,
        '\u05E1' to 60, '\u05E2' to 70, '\u05E4' to 80, '\u05E3' to 80, '\u05E6' to 90, '\u05E5' to 90, '\u05E7' to 100, '\u05E8' to 200,
        '\u05E9' to 300, '\u05EA' to 400,
    )

private fun parseHebrewNumber(raw: String, isYear: Boolean): Int? {
    val trimmed = raw.trim()
    trimmed.toIntOrNull()?.let { return it }
    val thousandsSeparator = trimmed.indexOfFirst { it == '\'' || it == '\u05F3' }
    if (isYear && thousandsSeparator > 0) {
        val thousands = sumHebrewDigits(trimmed.substring(0, thousandsSeparator)) ?: return null
        val remainder = sumHebrewDigits(trimmed.substring(thousandsSeparator + 1)) ?: 0
        return thousands * 1000 + remainder
    }
    val letters = trimmed.filterNot { it == '"' || it == '\u05F4' || it == '\'' || it == '\u05F3' }
    if (letters.isBlank()) return null
    if (isYear && letters.length > 1 && letters.first() == '\u05D4') {
        val remainder = sumHebrewDigits(letters.drop(1))
        if (remainder != null && remainder >= 100) return 5000 + remainder
    }
    val value = sumHebrewDigits(letters) ?: return null
    return if (isYear && value < 1000) value + 5000 else value
}

private fun sumHebrewDigits(value: String): Int? {
    val letters = value.filterNot { it == '"' || it == '\u05F4' || it == '\'' || it == '\u05F3' }
    if (letters.isBlank()) return null
    var total = 0
    for (letter in letters) total += hebrewDigitValues[letter] ?: return null
    return total
}
