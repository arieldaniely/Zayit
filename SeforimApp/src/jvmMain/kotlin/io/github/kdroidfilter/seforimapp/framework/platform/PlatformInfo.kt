package io.github.kdroidfilter.seforimapp.framework.platform

import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.systeminfo.SystemInfo
import dev.nucleusframework.systeminfo.model.RenderTier

/**
 * Cached platform information.
 * Values are computed once at class loading time and reused throughout the application lifecycle.
 */
object PlatformInfo {
    /**
     * The current operating system, cached at startup.
     */
    val currentOS: Platform = Platform.Current

    /**
     * True if running on macOS.
     */
    val isMacOS: Boolean = currentOS == Platform.MacOS

    /**
     * True if running on Windows.
     */
    val isWindows: Boolean = currentOS == Platform.Windows

    /**
     * True if running on Linux.
     */
    val isLinux: Boolean = currentOS == Platform.Linux

    /** Heavy 3D widgets require enough RAM and a hardware renderer supporting the required graphics APIs. */
    val isLowEnd: Boolean by lazy {
        val memory =
            java.lang.management.ManagementFactory
                .getOperatingSystemMXBean()
                as? com.sun.management.OperatingSystemMXBean
        val tier = runCatching { SystemInfo.renderTier() }.getOrDefault(RenderTier.UNKNOWN)
        (memory != null && memory.totalMemorySize < 6L * 1024 * 1024 * 1024) ||
            tier == RenderTier.SOFTWARE ||
            tier == RenderTier.LEGACY
    }

    fun warmUp() {
        Thread({ isLowEnd }, "platform-info-warmup").apply { isDaemon = true }.start()
    }
}
