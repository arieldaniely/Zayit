package io.github.kdroidfilter.seforimapp.features.onboarding.data

/** Choose exactly one archive, validating and sorting all of its numbered parts. */
internal fun <T> selectDistributionArchives(
    assets: List<T>,
    archiveName: String,
    name: (T) -> String,
): List<T> {
    val pattern = Regex(Regex.escape(archiveName) + "\\.part(\\d+)")
    val parts =
        assets
            .filter { pattern.matches(name(it)) }
            .sortedBy { pattern.matchEntire(name(it))!!.groupValues[1].toInt() }
    if (parts.isNotEmpty()) {
        require(parts.map { pattern.matchEntire(name(it))!!.groupValues[1].toInt() } == (1..parts.size).toList()) {
            "Missing or duplicate archive parts: $archiveName"
        }
        return parts
    }
    return listOf(assets.firstOrNull { name(it) == archiveName } ?: throw IllegalArgumentException("Missing archive: $archiveName"))
}
