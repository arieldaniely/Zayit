package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.TefilaRules
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/** The local siddur uses the installed corpus; it has no dependency on upstream's private templates. */
internal enum class SiddurPrayer(
    val headings: Set<String>,
) {
    SHACHARIT(setOf("שחרית")),
    MINCHA(setOf("מנחה")),
    MAARIV(setOf("ערבית", "מעריב")),
    MUSAF(setOf("מוסף")),
    NEILA(setOf("נעילה")),
    KABBALAT_SHABBAT(setOf("קבלת שבת")),
    BIRKAT_HAMAZON(setOf("ברכת המזון")),
    MEEIN_SHALOSH(setOf("מעין שלוש", "מעין שלש")),
    TEFILAT_HADERECH(setOf("תפילת הדרך", "תפלת הדרך")),
    KRIAT_SHEMA_AL_HAMITA(setOf("קריאת שמע שעל המיטה", "קריאת שמע על המיטה", "קריאת שמע שעל המטה")),
    SFIRAT_HAOMER(setOf("ספירת העומר")),
    NEROT_CHANUKA(setOf("נרות חנוכה", "נר חנוכה", "הדלקת נר חנוכה")),
    KIDDUSH_LEVANA(setOf("קידוש לבנה", "קדוש לבנה", "ברכת הלבנה")),
    HAVDALA(setOf("הבדלה")),
}

internal enum class SiddurNusach(
    val titleParts: Set<String>,
) {
    ASHKENAZ(setOf("אשכנז")),
    SEFARD(setOf("ספרד")),
    EDOT(setOf("עדות המזרח", "ספרדי")),
}

internal enum class Hallel { NONE, HALF, FULL }

internal data class SiddurDay(
    val date: LocalDate,
    val prayer: SiddurPrayer,
    val inIsrael: Boolean,
    val minyan: Boolean,
    val beitAvel: Boolean,
    val shabbat: Boolean,
    val festival: Boolean,
    val roshChodesh: Boolean,
    val musaf: Boolean,
    val tachanun: Boolean,
    val hallel: Hallel,
    val yaalehVeyavo: Boolean,
    val alHanissim: Boolean,
    val mashivHaruach: Boolean,
    val talUmatar: Boolean,
    val mizmorLetoda: Boolean,
    val lamenatzeach: Boolean,
    val omer: Int?,
    val chanuka: Int?,
    val holiday: Int,
)

/** Keep calendrical decisions in the maintained calendar library, rather than reproducing private siddur rules. */
internal fun siddurDay(
    date: LocalDate,
    prayer: SiddurPrayer,
    inIsrael: Boolean,
    minyan: Boolean = true,
    beitAvel: Boolean = false,
): SiddurDay {
    val calendar = JewishCalendar(date.toKotlinLocalDate(), inIsrael)
    val rules = TefilaRules()
    val shabbat = date.dayOfWeek == java.time.DayOfWeek.SATURDAY
    val festival = calendar.isYomTovAssurBemelacha
    return SiddurDay(
        date = date,
        prayer = prayer,
        inIsrael = inIsrael,
        minyan = minyan,
        beitAvel = beitAvel,
        shabbat = shabbat,
        festival = festival,
        roshChodesh = calendar.isRoshChodesh,
        musaf = shabbat || festival || calendar.isRoshChodesh || calendar.isCholHamoed,
        tachanun =
            !beitAvel &&
                when (prayer) {
                    SiddurPrayer.SHACHARIT -> rules.isTachanunRecitedShacharis(calendar)
                    SiddurPrayer.MINCHA -> rules.isTachanunRecitedMincha(calendar)
                    else -> false
                },
        hallel =
            when {
                rules.isHallelShalemRecited(calendar) -> Hallel.FULL
                rules.isHallelRecited(calendar) -> Hallel.HALF
                else -> Hallel.NONE
            },
        yaalehVeyavo = rules.isYaalehVeyavoRecited(calendar),
        alHanissim = rules.isAlHanissimRecited(calendar),
        mashivHaruach =
            when {
                rules.isMashivHaruachStartDate(calendar) -> prayer != SiddurPrayer.SHACHARIT
                rules.isMashivHaruachEndDate(calendar) -> prayer == SiddurPrayer.SHACHARIT
                else -> rules.isMashivHaruachRecited(calendar)
            },
        talUmatar = rules.isVeseinTalUmatarRecited(calendar),
        mizmorLetoda = !shabbat && !festival,
        lamenatzeach = !shabbat && !festival,
        omer = calendar.dayOfOmer.takeIf { it > 0 },
        chanuka = calendar.dayOfChanukah.takeIf { it > 0 },
        holiday = calendar.yomTovIndex,
    )
}

internal fun siddurKey(text: String): String =
    text
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("[֑-ׇ'\"׳״]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Conditions apply to source headings, never to guessed substrings inside prayer text. */
internal fun sectionIsApplicable(
    path: List<String>,
    day: SiddurDay,
): Boolean {
    if (day.prayer == SiddurPrayer.MUSAF && !day.musaf) return false
    if (day.prayer == SiddurPrayer.NEILA && day.holiday != JewishCalendar.YOM_KIPPUR) return false
    for (heading in path.map(::siddurKey)) {
        if (!day.minyan && heading in setOf("קדושה", "קדושת חזרת השץ")) return false
        val ordinaryPrayer = day.prayer in setOf(SiddurPrayer.SHACHARIT, SiddurPrayer.MINCHA, SiddurPrayer.MAARIV, SiddurPrayer.MUSAF)
        if (ordinaryPrayer) {
            if (Regex("(?:^| )(?:יום חול|ימי חול|ימות החול)(?:$| )").containsMatchIn(heading) && (day.shabbat || day.festival)) return false
            if (Regex("(?:^| )(?:לשבת|של שבת|בשבת|שבת)(?:$| )").containsMatchIn(heading) && !day.shabbat) return false
            if (heading.contains("יום טוב") && !day.festival) return false
            val occasions =
                listOf(
                    "ראש השנה" to setOf(JewishCalendar.ROSH_HASHANA),
                    "יום כיפור" to setOf(JewishCalendar.YOM_KIPPUR),
                    "יום הכיפורים" to setOf(JewishCalendar.YOM_KIPPUR),
                    "פסח" to setOf(JewishCalendar.PESACH, JewishCalendar.CHOL_HAMOED_PESACH),
                    "שבועות" to setOf(JewishCalendar.SHAVUOS),
                    "סוכות" to setOf(JewishCalendar.SUCCOS, JewishCalendar.CHOL_HAMOED_SUCCOS, JewishCalendar.HOSHANA_RABBA),
                    "שמיני עצרת" to setOf(JewishCalendar.SHEMINI_ATZERES),
                    "שמחת תורה" to setOf(JewishCalendar.SIMCHAS_TORAH, JewishCalendar.SHEMINI_ATZERES),
                )
            if (occasions.any { (label, holidays) -> heading.contains(label) && day.holiday !in holidays }) return false
        }
        if (!day.minyan &&
            listOf("קדיש", "ברכו", "חזרת השץ", "ברכת כהנים").any { heading == it || heading.startsWith("$it ") }
        ) {
            return false
        }
        if ((heading.contains("תחנון") || heading.contains("נפילת אפיים")) && !day.tachanun) return false
        if (heading.contains("מזמור לתודה") && !day.mizmorLetoda) return false
        if (heading == "למנצח" && !day.lamenatzeach) return false
        if (heading.contains("יעלה ויבוא") && !day.yaalehVeyavo) return false
        if ((heading.contains("על הניסים") || heading.contains("על הנסים")) && !day.alHanissim) return false
        if (heading.contains("הלל שלם") && day.hallel != Hallel.FULL) return false
        if (heading.contains("חצי הלל") && day.hallel != Hallel.HALF) return false
        if (heading == "הלל" && day.hallel == Hallel.NONE) return false
        if (heading.contains("ספירת העומר") && day.omer == null) return false
        if (heading.contains("נר חנוכה") && day.chanuka == null) return false
        if (heading in setOf("יום חול", "ימי חול", "ימות החול") && (day.shabbat || day.festival)) return false
        if (heading in setOf("שבת", "יום שבת") && !day.shabbat) return false
        if (heading in setOf("יום טוב", "ימים טובים", "שלוש רגלים", "שלש רגלים") && !day.festival) return false
        if (heading.contains("ראש חודש") && !day.roshChodesh) return false
        if (heading.contains("משיב הרוח") && !day.mashivHaruach) return false
        if (heading.contains("מוריד הטל") && day.mashivHaruach) return false
        if (heading.contains("ותן טל ומטר") && !day.talUmatar) return false
        if (heading.contains("ותן ברכה") && day.talUmatar) return false
    }
    return true
}

internal data class SiddurSourceSection(
    val entry: TocEntry,
    val path: List<String>,
)

/** Preserve the source tree's order and reject cyclic outlines instead of entering an endless traversal. */
internal fun selectSiddurSections(
    entries: List<TocEntry>,
    day: SiddurDay,
): List<SiddurSourceSection> {
    val byId = entries.associateBy { it.id }
    val children = entries.groupBy { it.parentId }

    fun ancestors(entry: TocEntry): List<TocEntry> {
        val path = mutableListOf<TocEntry>()
        val visited = mutableSetOf<Long>()
        var current: TocEntry? = entry
        while (current != null) {
            check(visited.add(current.id)) { "Cyclic siddur outline" }
            path += current
            current = current.parentId?.let(byId::get)
        }
        return path.asReversed()
    }
    val paths = entries.associate { it.id to ancestors(it) }
    val roots =
        entries.filter { entry ->
            day.prayer.headings.any { siddurKey(entry.text).contains(siddurKey(it)) } &&
                sectionIsApplicable(paths.getValue(entry.id).map { it.text }, day)
        }
    if (roots.isEmpty()) return emptyList()
    val rootIds = roots.map { it.id }.toSet()
    return entries.mapNotNull { entry ->
        if (!children[entry.id].isNullOrEmpty()) return@mapNotNull null
        val path = paths.getValue(entry.id)
        if (path.none { it.id in rootIds } || !sectionIsApplicable(path.map { it.text }, day)) return@mapNotNull null
        SiddurSourceSection(entry, path.map { it.text })
    }
}
