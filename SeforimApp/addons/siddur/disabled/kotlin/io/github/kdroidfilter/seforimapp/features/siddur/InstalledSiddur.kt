package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.e2e.E2eScenario
import java.util.UUID

/** The independent, source-backed siddur in the community build. */
val installedSiddur: SiddurFeature =
    object : SiddurFeature {
        @Composable
        override fun TabContent(
            tabId: String,
            destination: TabsDestination.Siddur,
        ) = LocalSiddurContent(tabId, destination)

        override suspend fun runE2e(sc: E2eScenario) {
            val tabs =
                sc
                    .graph()
                    .desktopManager.windows.value
                    .first()
                    .tabsViewModel
            tabs.openTab(TabsDestination.Siddur(tabId = UUID.randomUUID().toString()))
            sc.step("local-siddur")
        }
    }
