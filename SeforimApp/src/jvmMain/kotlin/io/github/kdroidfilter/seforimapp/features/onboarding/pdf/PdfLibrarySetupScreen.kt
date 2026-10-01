package io.github.kdroidfilter.seforimapp.features.onboarding.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

@Composable
fun PdfLibrarySetupScreen(
    navController: NavController,
    progressBarState: ProgressBarState = LocalAppGraph.current.onboardingProgressBarState,
) {
    val talmudPdfService = LocalAppGraph.current.talmudPdfService
    val semanticAssetsManager = LocalAppGraph.current.semanticAssetsManager

    val scope = rememberCoroutineScope()
    var missingPdf by remember { mutableStateOf(false) }
    var missingVectors by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var installPdf by remember { mutableStateOf(false) }
    var installVectors by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun continueToProfile() {
        navController.navigate(OnBoardingDestination.UserProfilScreen) {
            popUpTo<OnBoardingDestination.PdfLibrarySetupScreen> { inclusive = true }
        }
    }

    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.9f)
        val missing =
            withContext(Dispatchers.IO) {
                !talmudPdfService.isInstalled() to !semanticAssetsManager.validatedReady()
            }
        missingPdf = missing.first
        missingVectors = missing.second
        checking = false
        if (!missingPdf && !missingVectors) continueToProfile()
    }

    // Do not show the optional-component step for a complete distribution.
    if (checking || (!missingPdf && !missingVectors)) return

    OnBoardingScaffold(
        title = stringResource(Res.string.optional_components_title),
        bottomAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(
                    enabled = !installing && (installPdf || installVectors),
                    onClick = {
                        installing = true
                        failed = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    if (missingPdf && installPdf) talmudPdfService.downloadAndInstall()
                                    if (missingVectors && installVectors) semanticAssetsManager.downloadBundle()
                                }
                                continueToProfile()
                            } catch (failure: kotlinx.coroutines.CancellationException) {
                                throw failure
                            } catch (_: Exception) {
                                failed = true
                                // Keep successful components installed; retry only what is still missing.
                                missingPdf = withContext(Dispatchers.IO) { !talmudPdfService.isInstalled() }
                                missingVectors = withContext(Dispatchers.IO) { !semanticAssetsManager.validatedReady() }
                            } finally {
                                installing = false
                            }
                        }
                    },
                ) { Text(stringResource(Res.string.optional_install_selected)) }
                OutlinedButton(enabled = !installing, onClick = ::continueToProfile) {
                    Text(stringResource(Res.string.optional_skip))
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(stringResource(Res.string.optional_components_body))
            if (missingPdf) {
                CheckboxRow(
                    text = stringResource(Res.string.optional_pdf),
                    checked = installPdf,
                    enabled = !installing,
                    onCheckedChange = { installPdf = it },
                )
            }
            if (missingVectors) {
                CheckboxRow(
                    text = stringResource(Res.string.optional_vectors),
                    checked = installVectors,
                    enabled = !installing,
                    onCheckedChange = { installVectors = it },
                )
            }
            if (installing) Text(stringResource(Res.string.pdf_installing))
            if (failed) {
                InlineErrorBanner(text = stringResource(Res.string.optional_install_failed))
            }
        }
    }
}
