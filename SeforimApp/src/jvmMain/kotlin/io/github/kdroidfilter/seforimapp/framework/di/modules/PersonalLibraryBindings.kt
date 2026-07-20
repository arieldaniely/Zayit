package io.github.kdroidfilter.seforimapp.framework.di.modules

import com.russhwolf.settings.Settings
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalLibraryManager
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalLibraryOverlay
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalLibraryRuntime
import io.github.kdroidfilter.seforimapp.features.personallibrary.PersonalLibraryService
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import java.nio.file.Path

@ContributesTo(AppScope::class)
@BindingContainer
object PersonalLibraryBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun providePersonalLibraryManager(settings: Settings): PersonalLibraryManager {
        val storage = Path.of(getUserSettingsDatabasePath()).toAbsolutePath().parent.resolve("personal-library")
        return PersonalLibraryManager(settings, Path.of(getDatabasePath()), storage)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providePersonalLibraryOverlay(_repository: SeforimRepository): PersonalLibraryOverlay =
        checkNotNull(PersonalLibraryRuntime.overlay) { "Personal library overlay was not initialized" }

    @Provides
    @SingleIn(AppScope::class)
    fun providePersonalLibraryService(
        manager: PersonalLibraryManager,
        overlay: PersonalLibraryOverlay,
        search: CompositeSearchEngine,
        repository: SeforimRepository,
    ): PersonalLibraryService = PersonalLibraryService(manager, overlay, search, repository)
}
