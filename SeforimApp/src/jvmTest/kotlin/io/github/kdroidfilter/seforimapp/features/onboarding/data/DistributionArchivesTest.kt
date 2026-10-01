package io.github.kdroidfilter.seforimapp.features.onboarding.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DistributionArchivesTest {
    @Test
    fun `selects all parts numerically without mixing variants`() {
        val archive = "seforim_bundle.tar.zst"
        val parts = (1..12).map { "$archive.part%02d".format(it) }
        val assets = parts.reversed() + archive + "seforim_bundle-no-pdf.tar.zst.part01"
        assertEquals(parts, selectDistributionArchives(assets, archive) { it })
    }

    @Test
    fun `PDF supplement supports more than two parts and a single archive`() {
        val archive = "talmud_bavli_latest.tar.zst"
        val parts = (1..3).map { "$archive.part%02d".format(it) }
        assertEquals(parts, selectDistributionArchives(parts.reversed(), archive) { it })
        assertEquals(listOf(archive), selectDistributionArchives(listOf(archive, "other.tar.zst"), archive) { it })
    }

    @Test
    fun `rejects gaps duplicates and missing archives`() {
        val archive = "seforim_bundle.tar.zst"
        for (assets in listOf(
            listOf("$archive.part01", "$archive.part03"),
            listOf("$archive.part01", "$archive.part1"),
            emptyList(),
        )) {
            assertFailsWith<IllegalArgumentException> {
                selectDistributionArchives(assets, archive) { it }
            }
        }
    }
}
