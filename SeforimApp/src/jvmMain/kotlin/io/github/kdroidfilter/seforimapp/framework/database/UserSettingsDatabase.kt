package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.framework.portable.PortablePaths
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

private const val SETTINGS_DB_DIR = "settings"
private const val SETTINGS_DB_NAME = "user_settings.db"

fun getUserSettingsDatabasePath(): String {
    val root = File(portableDatabasesDirPath(), SETTINGS_DB_DIR).apply { mkdirs() }
    return File(root, SETTINGS_DB_NAME).absolutePath
}

private fun portableDatabasesDirPath(): String =
    if (PortablePaths.isPortable) PortablePaths.databasesDir.absolutePath else FileKit.databasesDir.path
