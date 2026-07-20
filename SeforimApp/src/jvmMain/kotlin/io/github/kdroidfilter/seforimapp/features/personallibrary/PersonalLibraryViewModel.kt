package io.github.kdroidfilter.seforimapp.features.personallibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PersonalLibraryUiState(
    val configuration: PersonalLibraryConfiguration = PersonalLibraryConfiguration(),
    val isWorking: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class PersonalLibraryViewModel(private val service: PersonalLibraryService) : ViewModel() {
    private val _state = MutableStateFlow(PersonalLibraryUiState(configuration = service.configuration()))
    val state: StateFlow<PersonalLibraryUiState> = _state.asStateFlow()

    fun addFolder(directory: File, placement: PersonalFolderPlacement) {
        val canonical = runCatching { directory.canonicalFile }.getOrElse { directory.absoluteFile }
        if (!canonical.isDirectory) return
        val current = _state.value.configuration
        if (current.folders.any { File(it.path) == canonical }) return
        val folder = PersonalBookFolder(
            id = UUID.randomUUID().toString(), path = canonical.path,
            displayName = canonical.name.ifBlank { canonical.path }, placement = placement,
        )
        apply(current.copy(folders = current.folders + folder))
    }

    fun setPlacement(id: String, placement: PersonalFolderPlacement) = mutate(id) { it.copy(placement = placement) }
    fun setEnabled(id: String, enabled: Boolean) = mutate(id) { it.copy(enabled = enabled) }
    fun remove(id: String) = apply(_state.value.configuration.copy(folders = _state.value.configuration.folders.filterNot { it.id == id }))
    fun reindex() = apply(_state.value.configuration, force = true)

    private fun mutate(id: String, transform: (PersonalBookFolder) -> PersonalBookFolder) {
        val current = _state.value.configuration
        apply(current.copy(folders = current.folders.map { if (it.id == id) transform(it) else it }))
    }

    private fun apply(configuration: PersonalLibraryConfiguration, force: Boolean = false) {
        if (_state.value.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null, success = false) }
            runCatching { service.synchronize(configuration, force) }
                .onSuccess { synchronized ->
                    _state.value = PersonalLibraryUiState(configuration = synchronized, success = true)
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, error = error.message ?: error::class.simpleName) }
                }
        }
    }
}
