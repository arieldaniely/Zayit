package io.github.kdroidfilter.seforimapp.features.onboarding.data

import io.github.kdroidfilter.seforimapp.network.KtorConfig
import io.github.kdroidfilter.seforimapp.releasefetcher.github.GitHubReleaseFetcher

val databaseFetcher =
    GitHubReleaseFetcher(
        // Install the distribution published by Zayita.
        owner = "arieldaniely",
        repo = "SeforimLibrary",
        httpClient = KtorConfig.createHttpClient(),
    )
