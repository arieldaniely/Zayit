package io.github.kdroidfilter.seforimapp.features.sharedstudy

import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object BundledNativeLibrary {
    fun load(baseName: String): Boolean =
        runCatching {
            System.loadLibrary(baseName)
            true
        }.getOrElse {
            runCatching { extractAndLoad(baseName) }.getOrDefault(false)
        }

    private fun extractAndLoad(baseName: String): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val architecture = System.getProperty("os.arch").lowercase()
        val platform = platformDirectory(os, architecture) ?: return false
        val fileName = System.mapLibraryName(baseName)
        val resourcePath = "/native/$platform/$fileName"
        val source = BundledNativeLibrary::class.java.getResourceAsStream(resourcePath) ?: return false
        val directory = Files.createTempDirectory("zayit-shared-study-native-")
        val target = directory.resolve(fileName)
        source.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        target.toFile().deleteOnExit()
        directory.toFile().deleteOnExit()
        System.load(target.toAbsolutePath().toString())
        return true
    }

    internal fun platformDirectory(
        os: String,
        architecture: String,
    ): String? =
        when {
            os.contains("win", ignoreCase = true) &&
                (architecture.contains("aarch64", ignoreCase = true) ||
                    architecture.contains("arm64", ignoreCase = true)) -> "windows-arm64"
            os.contains("win", ignoreCase = true) && architecture.contains("64") -> "windows-x64"
            os.contains("mac", ignoreCase = true) &&
                (architecture.contains("aarch64", ignoreCase = true) ||
                    architecture.contains("arm64", ignoreCase = true)) -> "macos-arm64"
            os.contains("mac", ignoreCase = true) -> "macos-x64"
            else -> null
        }
}
