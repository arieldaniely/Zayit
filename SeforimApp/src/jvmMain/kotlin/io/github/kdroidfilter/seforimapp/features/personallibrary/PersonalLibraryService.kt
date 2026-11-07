package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager

class PersonalLibraryService(
    private val manager: PersonalLibraryManager,
    private val overlay: PersonalLibraryOverlay,
    private val search: CompositeSearchEngine,
    private val repository: SeforimRepository,
) {
    private val _state = MutableStateFlow(PersonalLibraryUiState(configuration = manager.store.load()))
    val state: StateFlow<PersonalLibraryUiState> = _state.asStateFlow()

    @Volatile
    private var incrementalSemanticJob: Job? = null

    fun cancelIncrementalSemanticIndex() {
        incrementalSemanticJob?.cancel()
    }

    fun configuration(): PersonalLibraryConfiguration = _state.value.configuration

    suspend fun pdfFile(bookId: Long): File? =
        withContext(Dispatchers.IO) {
            if (bookId >= -1L) return@withContext null
            val artifacts = manager.activeArtifacts() ?: return@withContext null
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.prepareStatement("SELECT value FROM schema_meta WHERE key=?").use { statement ->
                    statement.setString(1, "personal_pdf:$bookId")
                    statement.executeQuery().use { rows ->
                        if (rows.next()) File(rows.getString(1)).takeIf { it.isFile } else null
                    }
                }
            }
        }

    suspend fun synchronizeAfterStartup() {
        val configuration = withContext(Dispatchers.IO) { manager.takeStartupSynchronization() } ?: return
        // Let restored windows become interactive before checking external folders.
        delay(2_000)
        if (!_state.value.isWorking && manager.store.load() == configuration) {
            requestSynchronize(configuration)
        }
    }

    // Closing the settings view must not interrupt publication after the generation has been saved.
    suspend fun requestSynchronize(
        configuration: PersonalLibraryConfiguration,
        force: Boolean = false,
    ) = withContext(NonCancellable) {
        if (_state.value.isWorking) return@withContext
        _state.update { it.copy(isWorking = true, error = null, success = false, progress = 0f) }
        val result =
            runCatching {
                withContext(Dispatchers.IO) {
                    val previous = manager.store.load()
                    val previousGeneration = previous.activeGeneration
                    val publishedDatabase = PersonalLibraryRuntime.activeDatabase
                    val (imported, artifacts) =
                        manager.synchronize(configuration, force) { progress ->
                            _state.update { it.copy(progress = progress) }
                        }
                    if (imported.activeGeneration == previousGeneration && artifacts?.databasePath == publishedDatabase) {
                        return@withContext imported
                    }
                    val updated =
                        try {
                            synchronizeSemanticIndex(previous, imported, artifacts, publishedDatabase)
                        } catch (failure: Exception) {
                            manager.store.save(previous)
                            PersonalLibraryRuntime.activeDatabase = publishedDatabase
                            throw failure
                        }
                    overlay.attach(artifacts?.databasePath)
                    search.replacePersonal(
                        artifacts?.let {
                            personalSearchEngine(it, updated, repository, Path.of(getDatabasePath()), search::sharedEmbedder)
                        },
                    )
                    CatalogCache.reloadCatalog()
                    updated
                }
            }
        result
            .onSuccess { updated ->
                _state.update { it.copy(configuration = updated, isWorking = false, success = true, progress = 1f) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        configuration = manager.store.load(),
                        isWorking = false,
                        error = if (error is CancellationException) null else error.message ?: error::class.simpleName,
                        progress = 0f,
                    )
                }
            }
    }

    private suspend fun synchronizeSemanticIndex(
        previous: PersonalLibraryConfiguration,
        imported: PersonalLibraryConfiguration,
        artifacts: PersonalLibraryArtifacts?,
        previousDatabase: Path?,
    ): PersonalLibraryConfiguration {
        if (!previous.semanticEnabled || artifacts == null) return imported
        val model = checkNotNull(search.sharedEmbedder()) { "SEMANTIC_MODEL_REQUIRED" }
        val output = artifacts.newSemanticDirectory()
        var saved = false
        try {
            val indexed =
                coroutineScope {
                    val job =
                        async(start = CoroutineStart.LAZY) {
                            buildPersonalSemanticIndex(
                                artifacts.databasePath,
                                Path.of("${getDatabasePath()}.semantic/model"),
                                output,
                                embed = { model.embedClean(it, io.github.kdroidfilter.seforimlibrary.search.SeforimEmbedder.Role.PASSAGE) },
                                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                                previousIndex = previous.semanticDirectory?.let(Path::of),
                                previousDatabase = previousDatabase,
                                allowEmpty = true,
                            )
                        }
                    incrementalSemanticJob = job
                    _state.update { it.copy(semanticWorking = true, progress = 0f) }
                    job.await()
                }
            val updated =
                imported.copy(
                    semanticGeneration = artifacts.generation.takeIf { indexed > 0 },
                    semanticDirectory = output.toString().takeIf { indexed > 0 },
                )
            manager.store.save(updated)
            saved = indexed > 0
            return updated
        } finally {
            if (!saved &&
                java.nio.file.Files
                    .exists(output)
            ) {
                deletePersonalSemanticBuild(output)
            }
            incrementalSemanticJob = null
            _state.update { it.copy(semanticWorking = false) }
        }
    }

    suspend fun buildSemanticIndex() {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, semanticWorking = true, error = null, progress = 0f, success = false) }
        try {
            withContext(Dispatchers.IO) {
                val configuration = manager.store.load()
                val artifacts = checkNotNull(manager.activeArtifacts(configuration))
                val model = checkNotNull(search.sharedEmbedder()) { "SEMANTIC_MODEL_REQUIRED" }
                val output = artifacts.newSemanticDirectory()
                var published = false
                try {
                    buildPersonalSemanticIndex(
                        artifacts.databasePath,
                        Path.of("${getDatabasePath()}.semantic/model"),
                        output,
                        embed = { model.embedClean(it, io.github.kdroidfilter.seforimlibrary.search.SeforimEmbedder.Role.PASSAGE) },
                        onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                    )
                    val updated =
                        configuration.copy(
                            semanticEnabled = true,
                            semanticGeneration = artifacts.generation,
                            semanticDirectory = output.toString(),
                        )
                    withContext(NonCancellable) {
                        val engine =
                            personalSearchEngine(artifacts, updated, repository, Path.of(getDatabasePath()), search::sharedEmbedder)
                        manager.store.save(updated)
                        search.replacePersonal(engine)
                        published = true
                        _state.update { it.copy(configuration = updated, success = true) }
                    }
                } finally {
                    if (!published &&
                        java.nio.file.Files
                            .exists(output)
                    ) {
                        deletePersonalSemanticBuild(output)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: error::class.simpleName) }
        } finally {
            _state.update { it.copy(isWorking = false, semanticWorking = false, progress = 0f) }
        }
    }

    suspend fun disableSemanticIndex() {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                val configuration = manager.store.load().copy(semanticEnabled = false)
                val engine =
                    manager.activeArtifacts(configuration)?.let {
                        personalSearchEngine(it, configuration, repository, Path.of(getDatabasePath()), search::sharedEmbedder)
                    }
                manager.store.save(configuration)
                search.replacePersonal(engine)
                _state.update { it.copy(configuration = configuration, success = false) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: error::class.simpleName) }
        } finally {
            _state.update { it.copy(isWorking = false) }
        }
    }
}
