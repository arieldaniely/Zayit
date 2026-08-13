package io.github.kdroidfilter.seforimapp.features.onboarding.data

import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.github.kdroidfilter.seforimapp.releasefetcher.github.GitHubReleaseFetcher

val databaseFetcher =
    GitHubReleaseFetcher(
        // Zayita distributes only the application. The books database continues to be
        // downloaded from the upstream Zayit data release.
        owner = "kdroidFilter",
        repo = "SeforimLibrary",
        httpClient = KtorConfig.createHttpClient(),
    )
