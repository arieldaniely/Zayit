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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PersonalLibraryUiState(
    val configuration: PersonalLibraryConfiguration = PersonalLibraryConfiguration(),
    val isWorking: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val success: Boolean = false,
)

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class PersonalLibraryViewModel(private val service: PersonalLibraryService) : ViewModel() {
    private val _state = MutableStateFlow(PersonalLibraryUiState(configuration = service.configuration()))
    val state: StateFlow<PersonalLibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            service.state.collectLatest { _state.value = it }
        }
    }

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
        _state.value = _state.value.copy(configuration = configuration)
        service.requestSynchronize(configuration, force)
    }
}
