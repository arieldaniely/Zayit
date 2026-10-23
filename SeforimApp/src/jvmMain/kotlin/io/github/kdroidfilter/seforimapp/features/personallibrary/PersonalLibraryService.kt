package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.nio.file.Path

class PersonalLibraryService(
    private val manager: PersonalLibraryManager,
    private val overlay: PersonalLibraryOverlay,
    private val search: CompositeSearchEngine,
    private val repository: SeforimRepository,
) {
    private val _state = MutableStateFlow(PersonalLibraryUiState(configuration = manager.store.load()))
    val state: StateFlow<PersonalLibraryUiState> = _state.asStateFlow()

    fun configuration(): PersonalLibraryConfiguration = _state.value.configuration

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
                    val previousGeneration = _state.value.configuration.activeGeneration
                    val publishedDatabase = PersonalLibraryRuntime.activeDatabase
                    val (updated, artifacts) =
                        manager.synchronize(configuration, force) { progress ->
                            _state.update { it.copy(progress = progress) }
                        }
                    if (updated.activeGeneration == previousGeneration && artifacts?.databasePath == publishedDatabase) {
                        return@withContext updated
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
                        error = error.message ?: error::class.simpleName,
                        progress = 0f,
                    )
                }
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
