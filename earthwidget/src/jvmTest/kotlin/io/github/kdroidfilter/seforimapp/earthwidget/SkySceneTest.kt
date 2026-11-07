package io.github.kdroidfilter.seforimapp.earthwidget

import io.github.erkko68.filament.compose.scene.Direction
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkySceneTest {
    @Test
    fun moonVisibilityUsesTheSelectedMomentAndLocation() {
        val noon = Instant.parse("2024-04-08T18:42:00Z").toEpochMilli()
        val night = Instant.parse("2024-04-08T06:42:00Z").toEpochMilli()

        // The Moon was near the Sun on the day of the Dallas solar eclipse.
        assertTrue(isMoonAboveHorizon(32.78, -96.80, noon))
        assertFalse(isMoonAboveHorizon(32.78, -96.80, night))
        assertFalse(isMoonAboveHorizon(-32.78, 83.20, noon))
    }

    /** The star field's rotation puts a star where the textbook hour-angle formula does. */
    @Test
    fun equatorialRotationMatchesHorizontalFormula() {
        val latitude = 31.77
        for (lst in listOf(0.3, 2.1, 4.4)) {
            val rotation = equatorialToSky(lst, latitude)
            for ((ra, dec) in listOf(1.7 to 0.4, 5.2 to -0.3, 3.0 to 1.2)) {
                val star = Direction((cos(dec) * cos(ra)).toFloat(), (cos(dec) * sin(ra)).toFloat(), sin(dec).toFloat())
                val got = rotation * star

                val phi = latitude * DEG_TO_RAD
                val h = lst - ra
                val el = asin(sin(dec) * sin(phi) + cos(dec) * cos(phi) * cos(h))
                val az = atan2(-sin(h), tan(dec) * cos(phi) - sin(phi) * cos(h))
                val want = skyDirection(Math.toDegrees(az), Math.toDegrees(el))
                assertEquals(want.x, got.x, 1e-4f)
                assertEquals(want.y, got.y, 1e-4f)
                assertEquals(want.z, got.z, 1e-4f)
            }
        }
    }
}
