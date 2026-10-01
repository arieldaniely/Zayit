package io.github.kdroidfilter.seforimapp.framework.di.modules

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseCleanupUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.DatabasePreparationUseCase
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.data.databaseFetcher
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileUseCase
import io.github.kdroidfilter.seforimapp.framework.database.DatabasePathProvider
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

@ContributesTo(AppScope::class)
@BindingContainer
object OnboardingBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProcessRepository(): OnboardingProcessRepository = OnboardingProcessRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadUseCase(appSettings: AppSettings): DownloadUseCase =
        DownloadUseCase(
            gitHubReleaseFetcher = databaseFetcher,
            appSettings = appSettings,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideExtractUseCase(
        appSettings: AppSettings,
        databasePathProvider: DatabasePathProvider,
        talmudPdfService: io.github.kdroidfilter.seforimapp.features.pdf.TalmudPdfService,
    ): ExtractUseCase = ExtractUseCase(appSettings, databasePathProvider, talmudPdfService)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAvailableDiskSpaceUseCase(appSettings: AppSettings): AvailableDiskSpaceUseCase = AvailableDiskSpaceUseCase(appSettings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideRegionConfigUseCase(): RegionConfigUseCase = RegionConfigUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideUserProfileUseCase(): UserProfileUseCase = UserProfileUseCase()

    @Provides
    @SingleIn(AppScope::class)
    fun provideOnboardingProgressBarState(): ProgressBarState = ProgressBarState()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseUpdateProgressBarState(): DatabaseUpdateProgressBarState = DatabaseUpdateProgressBarState()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseCleanupUseCase(
        databasePathProvider: DatabasePathProvider,
        appSettings: AppSettings,
    ): DatabaseCleanupUseCase = DatabaseCleanupUseCase(databasePathProvider, appSettings)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePreparationUseCase(
        cleanupUseCase: DatabaseCleanupUseCase,
        diskSpaceUseCase: AvailableDiskSpaceUseCase,
    ): DatabasePreparationUseCase = DatabasePreparationUseCase(cleanupUseCase, diskSpaceUseCase)
}
