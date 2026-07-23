package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.Settings
import java.nio.file.Files
import java.nio.file.Path

class PersonalLibraryManager(
    settings: Settings,
    baseDatabase: Path,
    storageDirectory: Path,
) {
    val store = PersonalLibraryConfigurationStore(settings)
    private val generationsDirectory = storageDirectory.resolve("generations")
    private val importer = PersonalLibraryImporter(baseDatabase, generationsDirectory)

    @Synchronized
    fun synchronize(
        requested: PersonalLibraryConfiguration = store.load(),
        force: Boolean = false,
        onProgress: ((Float) -> Unit)? = null,
    ): Pair<PersonalLibraryConfiguration, PersonalLibraryArtifacts?> {
        val enabled = requested.folders.filter { it.enabled }
        if (enabled.isEmpty()) {
            val empty = requested.copy(activeGeneration = null, synchronizedFingerprint = null)
            store.save(empty)
            PersonalLibraryRuntime.activeDatabase = null
            return empty to null
        }
        val fingerprint = importer.fingerprint(enabled)
        val current = requested.activeGeneration?.let { artifacts(it) }
        if (!force && requested.synchronizedFingerprint == fingerprint && current?.isComplete() == true) {
            PersonalLibraryRuntime.activeDatabase = current.databasePath
            return requested to current
        }
        val generation = if (force) "$fingerprint-${System.currentTimeMillis()}" else fingerprint
        val (artifacts, summaries) = importer.build(enabled, generation, onProgress)
        val now = System.currentTimeMillis()
        val folders = requested.folders.map { folder ->
            if (!folder.enabled) folder else summaries[folder.id]?.let { summary ->
                folder.copy(
                    lastBookCount = summary.books,
                    lastLinkCount = summary.links,
                    lastImportedAt = now,
                    lastError = null,
                )
            } ?: folder.copy(lastImportedAt = folder.lastImportedAt ?: now, lastError = null)
        }
        val synchronized = requested.copy(
            folders = folders,
            activeGeneration = artifacts.generation,
            synchronizedFingerprint = fingerprint,
        )
        store.save(synchronized)
        PersonalLibraryRuntime.activeDatabase = artifacts.databasePath
        return synchronized to artifacts
    }

    fun activeArtifacts(configuration: PersonalLibraryConfiguration = store.load()): PersonalLibraryArtifacts? =
        configuration.activeGeneration?.let(::artifacts)?.takeIf { it.isComplete() }

    private fun artifacts(generation: String): PersonalLibraryArtifacts {
        val root = generationsDirectory.resolve(generation)
        return PersonalLibraryArtifacts(generation, root.resolve("personal.db"), root.resolve("personal.lucene"))
    }

    private fun PersonalLibraryArtifacts.isComplete(): Boolean =
        Files.isRegularFile(databasePath) && Files.isDirectory(indexPath)
}

object PersonalLibraryRuntime {
    @Volatile var overlay: PersonalLibraryOverlay? = null
    @Volatile var startupError: String? = null
    @Volatile var activeDatabase: Path? = null
}
