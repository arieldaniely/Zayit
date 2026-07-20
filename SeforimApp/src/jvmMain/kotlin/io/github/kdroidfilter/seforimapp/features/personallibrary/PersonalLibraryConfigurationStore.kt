package io.github.kdroidfilter.seforimapp.features.personallibrary

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PersonalLibraryConfigurationStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun load(): PersonalLibraryConfiguration =
        settings.getStringOrNull(KEY)
            ?.let { runCatching { json.decodeFromString<PersonalLibraryConfiguration>(it) }.getOrNull() }
            ?: PersonalLibraryConfiguration()

    @Synchronized
    fun save(configuration: PersonalLibraryConfiguration) {
        settings.putString(KEY, json.encodeToString(configuration))
    }

    private companion object { const val KEY = "personal_library.configuration.v1" }
}
