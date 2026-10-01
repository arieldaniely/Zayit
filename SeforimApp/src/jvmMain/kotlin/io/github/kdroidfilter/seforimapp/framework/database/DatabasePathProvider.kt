package io.github.kdroidfilter.seforimapp.framework.database

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.infoln
import java.io.File

private const val DEFAULT_DB_NAME = "seforim.db"

/**
 * Resolves the database path, preferring an environment variable if present,
 * falling back to AppSettings, and finally checking the default location.
 *
 * The path is resolved once and cached (thread-safe); call [reset] to force
 * re-resolution after the database is reinstalled or relocated (e.g. following
 * [io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase]).
 */
@Inject
@SingleIn(AppScope::class)
class DatabasePathProvider(
    private val appSettings: AppSettings,
) {
    @Volatile
    private var cached: String? = null

    /** The database path; throws [IllegalStateException] when the file does not exist. */
    fun get(): String {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: resolve().also { cached = it }
        }
    }

    /** Clears the cached path so the next [get] re-resolves it. */
    fun reset() {
        synchronized(this) { cached = null }
    }

    private fun resolve(): String {
        // Repair the legacy setting before resolving the selected/portable install location.
        if (appSettings.getDatabasePath()?.endsWith("lexical.db", ignoreCase = true) == true) {
            appSettings.setDatabasePath(null)
        }
        val dbPath = requestedDatabaseFile(appSettings).absolutePath

        infoln { "[DatabaseUtils] Database path resolved: $dbPath (exists: ${File(dbPath).exists()})" }

        // Check if the database file exists
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("Database file not found at $dbPath")
        }

        return dbPath
    }
}
