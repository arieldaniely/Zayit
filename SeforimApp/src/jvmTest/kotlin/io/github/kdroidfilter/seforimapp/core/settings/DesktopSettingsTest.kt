package io.github.kdroidfilter.seforimapp.core.settings

import com.russhwolf.settings.Settings
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopSettingsTest {
    @Test
    fun `fork preferences persist independently of the original application`() {
        val original = Settings()
        val fork = createDesktopSettings()
        val key = "isolation_test_${UUID.randomUUID()}"
        try {
            original.putString(key, "original-library")
            assertFalse(fork.hasKey(key))
            fork.putString(key, "fork-library")
            assertEquals("original-library", original.getString(key, ""))
            assertEquals("fork-library", createDesktopSettings().getString(key, ""))
            fork.remove(key)
            assertEquals("original-library", original.getString(key, ""))
        } finally {
            original.remove(key)
            fork.remove(key)
        }
    }
}
