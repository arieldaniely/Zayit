package io.github.kdroidfilter.seforimapp.core.presentation.components

import dev.nucleusframework.launcher.windows.StockIcon
import dev.nucleusframework.launcher.windows.TaskbarIconSource
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import java.io.File
import java.io.InputStream

object JumpListIcons {
    private const val ICONS_DIR_NAME = "jumplist_icons"

    private const val ICON_NEW_TAB = "new_tab.ico"
    private const val ICON_NEW_DESKTOP = "new_desktop.ico"
    private const val ICON_DESKTOP = "desktop.ico"
    private const val ICON_HOME = "home.ico"
    private const val ICON_SEARCH = "search.ico"
    private const val ICON_BOOK = "book.ico"
    private const val ICON_FAVORITE = "favorite.ico"
    private const val ICON_HISTORY = "history.ico"

    private val baseIconsDir: File by lazy {
        val baseDir =
            runCatching {
                File(getUserSettingsDatabasePath()).parentFile
            }.getOrNull() ?: File(System.getProperty("java.io.tmpdir"), "zayita")
        File(baseDir, ICONS_DIR_NAME).apply { mkdirs() }
    }

    private val cachedIcons = mutableMapOf<String, TaskbarIconSource>()

    private fun getIconSource(
        fileName: String,
        isDark: Boolean,
        fallbackStock: StockIcon,
    ): TaskbarIconSource {
        val themeSubdir = if (isDark) "dark" else "light"
        val cacheKey = "$themeSubdir/$fileName"
        cachedIcons[cacheKey]?.let { return it }

        val targetDir = File(baseIconsDir, themeSubdir).apply { mkdirs() }
        val targetFile = File(targetDir, fileName)
        extractIconResourceIfDifferent(themeSubdir, fileName, targetFile)

        val source =
            if (targetFile.exists() && targetFile.length() > 0L) {
                TaskbarIconSource.FromFile(targetFile.absolutePath)
            } else {
                TaskbarIconSource.FromStock(fallbackStock)
            }
        cachedIcons[cacheKey] = source
        return source
    }

    private fun extractIconResourceIfDifferent(
        themeSubdir: String,
        fileName: String,
        targetFile: File,
    ) {
        runCatching {
            val resourcePath = "jumplist_icons/$themeSubdir/$fileName"
            val stream: InputStream? =
                JumpListIcons::class.java.classLoader?.getResourceAsStream(resourcePath)
                    ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
            if (stream != null) {
                val bytes = stream.use { it.readBytes() }
                if (!targetFile.exists() || targetFile.length() != bytes.size.toLong()) {
                    targetFile.writeBytes(bytes)
                }
            }
        }
    }

    fun iconForNewTab(isDark: Boolean = false): TaskbarIconSource =
        getIconSource(
            fileName = ICON_NEW_TAB,
            isDark = isDark,
            fallbackStock = StockIcon.DOCUMENT_NO_ASSOCIATION,
        )

    fun iconForNewDesktop(isDark: Boolean = false): TaskbarIconSource =
        getIconSource(
            fileName = ICON_NEW_DESKTOP,
            isDark = isDark,
            fallbackStock = StockIcon.DESKTOP_PC,
        )

    fun iconForDesktop(isDark: Boolean = false): TaskbarIconSource =
        getIconSource(
            fileName = ICON_DESKTOP,
            isDark = isDark,
            fallbackStock = StockIcon.DESKTOP_PC,
        )

    fun iconForFavorite(isDark: Boolean = false): TaskbarIconSource =
        getIconSource(
            fileName = ICON_FAVORITE,
            isDark = isDark,
            fallbackStock = StockIcon.LINK,
        )

    fun iconForRecentlyClosed(isDark: Boolean = false): TaskbarIconSource =
        getIconSource(
            fileName = ICON_HISTORY,
            isDark = isDark,
            fallbackStock = StockIcon.SLOW_FILE,
        )

    fun iconForTab(
        tabType: TabType,
        rawTitle: String,
        isDark: Boolean = false,
    ): TaskbarIconSource =
        when {
            rawTitle.isEmpty() -> getIconSource(ICON_HOME, isDark, StockIcon.APPLICATION)
            tabType == TabType.SEARCH -> getIconSource(ICON_SEARCH, isDark, StockIcon.FIND)
            tabType == TabType.FAVORITES -> iconForFavorite(isDark)
            tabType == TabType.HISTORY -> iconForRecentlyClosed(isDark)
            else -> getIconSource(ICON_BOOK, isDark, StockIcon.DOCUMENT_NO_ASSOCIATION)
        }
}
