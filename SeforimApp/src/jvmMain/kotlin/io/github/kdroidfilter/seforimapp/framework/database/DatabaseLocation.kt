package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.portable.PortablePaths
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import java.io.File

const val BOOKS_DATABASE_FILE_NAME = "seforim.db"

/** Returns the database file that an install or the next app launch should use. */
fun requestedDatabaseFile(appSettings: AppSettings): File {
    val environmentPath = System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }
    val configuredPath = appSettings.getDatabasePath()?.takeIf { it.isNotBlank() }
    return File(environmentPath ?: configuredPath ?: File(defaultDatabasesDirectory(), BOOKS_DATABASE_FILE_NAME).path)
}

/** The folder used for downloads, extraction and disk-space checks. */
fun databaseInstallDirectory(appSettings: AppSettings): File =
    requestedDatabaseFile(appSettings).absoluteFile.parentFile ?: defaultDatabasesDirectory()

fun databaseFileIn(directory: File): File = File(directory, BOOKS_DATABASE_FILE_NAME)

/** Records a folder choice without moving or copying any existing database. */
fun selectDatabaseDirectory(
    appSettings: AppSettings,
    directory: File,
): File {
    require(directory.isDirectory) { "Database location must be an existing directory" }
    val databaseFile = databaseFileIn(directory).absoluteFile
    appSettings.setDatabasePath(databaseFile.path)
    return databaseFile
}

private fun defaultDatabasesDirectory(): File =
    if (PortablePaths.isPortable) PortablePaths.databasesDir else File(FileKit.databasesDir.path)

/** Root selected during setup; all library data lives below its databases directory. */
fun installationRootDirectory(appSettings: AppSettings): File = databaseInstallDirectory(appSettings).parentFile

fun databaseDirectoryInRoot(root: File): File = File(root, "databases")

fun selectInstallationRoot(
    appSettings: AppSettings,
    root: File,
): File {
    require(root.isDirectory) { "Installation root must be an existing directory" }
    val directory = databaseDirectoryInRoot(root)
    check(directory.isDirectory || directory.mkdirs()) { "Could not create database directory" }
    return selectDatabaseDirectory(appSettings, directory)
}
