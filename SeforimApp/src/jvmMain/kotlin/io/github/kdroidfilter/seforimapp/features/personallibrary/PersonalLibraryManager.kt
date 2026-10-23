package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.Settings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class PersonalLibraryManager(
    settings: Settings,
    private val baseDatabase: Path,
    storageDirectory: Path,
) {
    val store = PersonalLibraryConfigurationStore(settings)
    private val generationsDirectory = storageDirectory.resolve("generations")
    private val importer = PersonalLibraryImporter(baseDatabase, generationsDirectory)
    private var checkAfterStartup = false

    /** Opens the last compatible generation without walking source folders or rebuilding indexes. */
    @Synchronized
    fun prepareForStartup(): PersonalLibraryArtifacts? {
        checkAfterStartup = false
        val configuration = store.load()
        if (configuration.folders.none { it.enabled }) {
            PersonalLibraryRuntime.activeDatabase = null
            return null
        }
        val cached = compatibleStartupArtifacts(configuration)
        if (cached != null) {
            PersonalLibraryRuntime.activeDatabase = cached.databasePath
            checkAfterStartup = true
            return cached
        }
        return synchronize(configuration).second
    }

    @Synchronized
    fun takeStartupSynchronization(): PersonalLibraryConfiguration? {
        if (!checkAfterStartup) return null
        checkAfterStartup = false
        return store.load()
    }

    fun compatibleStartupArtifacts(configuration: PersonalLibraryConfiguration = store.load()): PersonalLibraryArtifacts? =
        activeArtifacts(configuration)?.takeIf { artifact ->
            runCatching { Files.readString(baseStampFile(artifact)) == baseStamp() }.getOrDefault(false)
        }

    private fun baseStamp(): String {
        val attributes = Files.readAttributes(baseDatabase, BasicFileAttributes::class.java)
        return "${baseDatabase.toRealPath()}:${attributes.size()}:${attributes.lastModifiedTime()}:" +
            "${attributes.creationTime()}:${attributes.fileKey()}"
    }

    private fun baseStampFile(artifact: PersonalLibraryArtifacts): Path = artifact.databasePath.resolveSibling("base-database.stamp")

    private fun rememberBase(artifact: PersonalLibraryArtifacts) {
        runCatching { Files.writeString(baseStampFile(artifact), baseStamp()) }
    }

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
            rememberBase(current)
            PersonalLibraryRuntime.activeDatabase = current.databasePath
            return requested to current
        }
        val generation = if (force) "$fingerprint-${System.currentTimeMillis()}" else fingerprint
        val (artifacts, summaries) = importer.build(enabled, generation, onProgress)
        val now = System.currentTimeMillis()
        val folders =
            requested.folders.map { folder ->
                if (!folder.enabled) {
                    folder
                } else {
                    summaries[folder.id]?.let { summary ->
                        folder.copy(
                            lastBookCount = summary.books,
                            lastLinkCount = summary.links,
                            lastImportedAt = now,
                            lastError = summary.failedFiles.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        )
                    } ?: folder.copy(lastImportedAt = folder.lastImportedAt ?: now, lastError = null)
                }
            }
        val synchronized =
            requested.copy(
                folders = folders,
                activeGeneration = artifacts.generation,
                synchronizedFingerprint = fingerprint,
            )
        store.save(synchronized)
        rememberBase(artifacts)
        PersonalLibraryRuntime.activeDatabase = artifacts.databasePath
        return synchronized to artifacts
    }

    fun activeArtifacts(configuration: PersonalLibraryConfiguration = store.load()): PersonalLibraryArtifacts? =
        configuration.activeGeneration
            ?.takeIf { configuration.folders.any { folder -> folder.enabled } }
            ?.let(::artifacts)
            ?.takeIf { it.isComplete() }

    private fun artifacts(generation: String): PersonalLibraryArtifacts {
        val root = generationsDirectory.resolve(generation)
        return PersonalLibraryArtifacts(generation, root.resolve("personal.db"), root.resolve("personal.lucene"))
    }

    private fun PersonalLibraryArtifacts.isComplete(): Boolean = Files.isRegularFile(databasePath) && Files.isDirectory(indexPath)
}

object PersonalLibraryRuntime {
    @Volatile var overlay: PersonalLibraryOverlay? = null

    @Volatile var startupError: String? = null

    @Volatile var activeDatabase: Path? = null
}
