package io.github.kdroidfilter.seforimapp.framework.update

import dev.nucleusframework.core.runtime.ExecutableRuntime
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo

/** How this copy of the app was installed, as far as its updates are concerned. */
object InstallInfo {
    /** This fork currently distributes desktop installers outside the Microsoft Store. */
    val selfUpdates: Boolean by lazy { !(PlatformInfo.isWindows && ExecutableRuntime.isAppX()) }

    /** Our installers do not ship upstream's signed passwordless update helper. */
    val hasPasswordlessUpdateHelper: Boolean = false
}
