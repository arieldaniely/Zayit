package io.github.kdroidfilter.seforimapp.framework.portable

import java.io.File

object PortablePaths {
    private const val PORTABLE_MARKER = "zayit.portable"
    private const val DATA_DIR_NAME = "data"
    private const val DATABASES_DIR_NAME = "databases"
    private const val PREFERENCES_DIR_NAME = "preferences"

    val appDir: File by lazy { resolveAppDir() }

    val isPortable: Boolean by lazy {
        val env = System.getenv("SEFORIMAPP_PORTABLE")?.isEnabledFlag()
        val prop = System.getProperty("seforimapp.portable")?.isEnabledFlag()
        env == true || prop == true || File(appDir, PORTABLE_MARKER).exists()
    }

    val dataDir: File by lazy {
        val explicit =
            System
                .getenv("SEFORIMAPP_PORTABLE_DIR")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: System
                    .getProperty("seforimapp.portable.dir")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)

        (explicit ?: File(appDir, DATA_DIR_NAME)).absoluteFile.apply { mkdirs() }
    }

    val databasesDir: File by lazy {
        if (isPortable) {
            File(dataDir, DATABASES_DIR_NAME).apply { mkdirs() }
        } else {
            error("Portable database directory requested while portable mode is disabled")
        }
    }

    fun configureSystemProperties() {
        if (!isPortable) return
        val prefsDir = File(dataDir, PREFERENCES_DIR_NAME).apply { mkdirs() }
        System.setProperty("java.util.prefs.userRoot", prefsDir.absolutePath)
        System.setProperty("java.util.prefs.systemRoot", prefsDir.absolutePath)
    }

    private fun resolveAppDir(): File {
        val codeSource =
            PortablePaths::class.java.protectionDomain.codeSource
                ?.location
        val location = codeSource?.toURI()?.let(::File)
        val base =
            when {
                location == null -> File(System.getProperty("user.dir"))
                location.isFile -> location.parentFile
                else -> location
            }
        return normalizePackagedAppDir(base).absoluteFile
    }

    private fun normalizePackagedAppDir(base: File): File {
        val parent = base.parentFile
        return if (base.name == "lib" && parent != null && File(parent, "bin").isDirectory) {
            parent
        } else {
            base
        }
    }

    private fun String.isEnabledFlag(): Boolean = lowercase() in setOf("1", "true", "yes", "y", "on")
}
