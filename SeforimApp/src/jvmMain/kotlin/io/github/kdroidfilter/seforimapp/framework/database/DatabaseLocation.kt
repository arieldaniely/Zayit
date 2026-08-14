package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.portable.PortablePaths
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

const val BOOKS_DATABASE_FILE_NAME = "seforim.db"

/** Returns the database file that an install or the next app launch should use. */
fun requestedDatabaseFile(): File {
    val environmentPath = System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }
    val configuredPath = AppSettings.getDatabasePath()?.takeIf { it.isNotBlank() }
    return File(environmentPath ?: configuredPath ?: File(defaultDatabasesDirectory(), BOOKS_DATABASE_FILE_NAME).path)
}

/** The folder used for downloads, extraction and disk-space checks. */
fun databaseInstallDirectory(): File = requestedDatabaseFile().absoluteFile.parentFile ?: defaultDatabasesDirectory()

fun databaseFileIn(directory: File): File = File(directory, BOOKS_DATABASE_FILE_NAME)

/** Records a folder choice without moving or copying any existing database. */
fun selectDatabaseDirectory(directory: File): File {
    require(directory.isDirectory) { "Database location must be an existing directory" }
    val databaseFile = databaseFileIn(directory).absoluteFile
    AppSettings.setDatabasePath(databaseFile.path)
    resetDatabasePathCache()
    return databaseFile
}

private fun defaultDatabasesDirectory(): File =
    if (PortablePaths.isPortable) PortablePaths.databasesDir else File(FileKit.databasesDir.path)
