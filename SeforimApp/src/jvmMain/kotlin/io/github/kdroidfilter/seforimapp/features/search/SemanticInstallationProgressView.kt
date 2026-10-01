package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.semantic_extract_progress
import seforimapp.seforimapp.generated.resources.semantic_install_busy
import seforimapp.seforimapp.generated.resources.semantic_install_validating

@Composable
internal fun SemanticInstallationProgressView() {
    val progress by LocalAppGraph.current.semanticAssetsManager.installationProgress
        .collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val fraction = progress?.fraction
        if (progress?.phase == SemanticAssetsManager.InstallationPhase.EXTRACTING && fraction != null) {
            Text(stringResource(Res.string.semantic_extract_progress, (fraction * 100).toInt()))
            AnimatedHorizontalProgressBar(fraction, Modifier.fillMaxWidth())
        } else {
            Text(
                stringResource(
                    if (progress?.phase == SemanticAssetsManager.InstallationPhase.VALIDATING) {
                        Res.string.semantic_install_validating
                    } else {
                        Res.string.semantic_install_busy
                    },
                ),
            )
            CircularProgressIndicator()
        }
    }
}
