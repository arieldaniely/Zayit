package io.github.kdroidfilter.seforimapp.framework.platform

import dev.nucleusframework.core.runtime.Platform

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

    /** Heavy 3D widgets are disabled on machines with less than 6 GB of physical RAM. */
    val isLowEnd: Boolean by lazy {
        val memory =
            java.lang.management.ManagementFactory
                .getOperatingSystemMXBean()
                as? com.sun.management.OperatingSystemMXBean
        memory != null && memory.totalMemorySize < 6L * 1024 * 1024 * 1024
    }
}
