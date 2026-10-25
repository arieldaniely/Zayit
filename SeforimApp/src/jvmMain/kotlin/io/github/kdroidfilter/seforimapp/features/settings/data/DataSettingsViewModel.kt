package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.backup.LocalFileDestination
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncState
import io.github.kdroidfilter.seforimapp.backup.drive.GoogleDriveSync
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.databaseFileIn
import io.github.kdroidfilter.seforimapp.framework.database.databaseInstallDirectory
import io.github.kdroidfilter.seforimapp.framework.database.selectDatabaseDirectory
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.portable.PortablePaths
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DataSettingsViewModel(
    private val appSettings: AppSettings,
    private val backupManager: BackupManager,
    private val driveSync: GoogleDriveSync,
) : ViewModel() {
    private val _state =
        MutableStateFlow(DataSettingsState(databaseDirectory = databaseInstallDirectory().absolutePath))
    val state: StateFlow<DataSettingsState> = _state.asStateFlow()

    val isDriveAvailable: Boolean = driveSync.isAvailable
    val driveState: StateFlow<DriveSyncState> = driveSync.state

    fun connectDrive() = driveSync.connect()

    fun cancelDriveConnect() = driveSync.cancelConnect()

    fun disconnectDrive() = driveSync.disconnect()

    fun backupToDrive() = driveSync.backupNow()

    fun restoreFromDrive() = driveSync.restore()

    private fun restartAfterRestore() {
        _state.update { it.copy(importSucceeded = true) }
        restartApplication()
    }

    fun inspectBooksDatabaseDirectory(directory: File) {
        val status =
            when {
                !directory.isDirectory -> SelectedDatabaseStatus.INVALID_DIRECTORY
                !databaseFileIn(directory).isFile -> SelectedDatabaseStatus.NOT_FOUND
                DatabaseVersionManager.isDatabaseVersionCompatible(databaseFileIn(directory)) ->
                    SelectedDatabaseStatus.READY
                else -> SelectedDatabaseStatus.UPDATE_REQUIRED
            }
        _state.update {
            it.copy(
                selectedDatabaseDirectory = directory.absolutePath,
                selectedDatabaseStatus = status,
            )
        }
    }

    fun useSelectedBooksDatabaseDirectory() {
        val directory = state.value.selectedDatabaseDirectory?.let(::File) ?: return
        if (!directory.isDirectory) {
            _state.update { it.copy(selectedDatabaseStatus = SelectedDatabaseStatus.INVALID_DIRECTORY) }
            return
        }
        selectDatabaseDirectory(directory)
        restartApplication()
    }

    fun clearSelectedBooksDatabaseDirectory() {
        _state.update { it.copy(selectedDatabaseDirectory = null, selectedDatabaseStatus = null) }
    }

    fun exportToFile(exportDir: File) {
        if (!exportDir.isDirectory) {
            _state.update { it.copy(exportFailed = true, exportedFileName = null) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportFailed = false, exportedFileName = null) }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val exportFile = File(exportDir, "zayita_backup_$timestamp.$BACKUP_EXTENSION")
            try {
                backupManager.backup(LocalFileDestination(exportFile))
                _state.update { it.copy(isExporting = false, exportedFileName = exportFile.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, exportFailed = true) }
            }
        }
    }

    fun importFromFile(importFile: File) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importFailed = false, importSucceeded = false) }
            val staged =
                try {
                    backupManager.restore(LocalFileDestination(importFile))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            _state.update { it.copy(isImporting = false, importFailed = !staged) }
            if (staged) restartAfterRestore()
        }
    }

    /** Wipes this app's managed data and settings, but never deletes an externally selected books database. */
    fun resetApp() {
        viewModelScope.launch(Dispatchers.IO) {
            val dbDir = File(portableDatabasesDirPath())

            appSettings.clearAll()

            // Delete every file/directory in the managed databases directory (this also holds the
            // user settings DB with notes and highlights).
            if (dbDir.exists()) {
                dbDir.listFiles()?.forEach { file ->
                    runCatching {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
            }

            _state.update { it.copy(resetDone = true) }
            restartApplication()
        }
    }

    companion object {
        const val BACKUP_EXTENSION = "zip"

        // The exports made before the archive format: a bare copy of the user database
        const val LEGACY_BACKUP_EXTENSION = "db"
    }
}

private fun portableDatabasesDirPath(): String =
    if (PortablePaths.isPortable) PortablePaths.databasesDir.absolutePath else FileKit.databasesDir.path
