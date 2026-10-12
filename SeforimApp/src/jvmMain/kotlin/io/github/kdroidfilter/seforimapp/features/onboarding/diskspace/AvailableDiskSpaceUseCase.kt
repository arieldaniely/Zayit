package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AvailableDiskSpaceUseCase(
    private val appSettings: io.github.kdroidfilter.seforimapp.core.settings.AppSettings,
) {
    /**
     * Reads available and total disk space via Nucleus SystemInfo.
     * Must be called from a coroutine — dispatched to IO internally.
     */
    suspend fun getDiskSpaceInfo(): DiskSpaceInfo =
        withContext(Dispatchers.IO) {
            var directory =
                io.github.kdroidfilter.seforimapp.framework.database
                    .databaseInstallDirectory(appSettings)
            while (!directory.exists() && directory.parentFile != null) directory = directory.parentFile
            DiskSpaceInfo(directory.usableSpace, directory.totalSpace)
        }

    data class DiskSpaceInfo(
        val availableBytes: Long,
        val totalBytes: Long,
    ) {
        val hasEnoughSpace: Boolean get() = availableBytes >= REQUIRED_SPACE_BYTES
        val remainingAfterInstall: Long get() = availableBytes - REQUIRED_SPACE_BYTES
    }

    companion object {
        /** Total space required during installation (includes temporary files). */
        const val REQUIRED_SPACE_GB = 15L

        /** Temporary space needed only during installation (will be freed after). */
        const val TEMPORARY_SPACE_GB = 2.5

        /** Final space after installation completes. */
        const val FINAL_SPACE_GB = 12.5

        val REQUIRED_SPACE_BYTES = REQUIRED_SPACE_GB * 1024 * 1024 * 1024
    }
}
