package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

class PersonalLibraryService(
    private val manager: PersonalLibraryManager,
    private val overlay: PersonalLibraryOverlay,
    private val search: CompositeSearchEngine,
    private val repository: SeforimRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PersonalLibraryUiState(configuration = manager.store.load()))
    val state: StateFlow<PersonalLibraryUiState> = _state.asStateFlow()

    fun configuration(): PersonalLibraryConfiguration = _state.value.configuration

    fun requestSynchronize(configuration: PersonalLibraryConfiguration, force: Boolean = false) {
        if (_state.value.isWorking) return
        scope.launch {
            _state.update { it.copy(isWorking = true, error = null, success = false, progress = 0f) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val (updated, artifacts) = manager.synchronize(configuration, force) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                    overlay.attach(artifacts?.databasePath)
                    val dictionary = Path.of(getDatabasePath()).resolveSibling("lexical.db")
                    search.replacePersonal(
                        artifacts?.let {
                            LuceneSearchEngine(
                                indexDir = it.indexPath,
                                snippetProvider = RepositorySnippetSourceProvider(repository),
                                dictionaryPath = dictionary,
                            )
                        },
                    )
                    CatalogCache.reloadCatalog()
                    updated
                }
            }
            result.onSuccess { updated ->
                _state.update { it.copy(configuration = updated, isWorking = false, success = true, progress = 1f) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isWorking = false,
                        error = error.message ?: error::class.simpleName,
                        progress = 0f,
                    )
                }
            }
        }
    }
}
