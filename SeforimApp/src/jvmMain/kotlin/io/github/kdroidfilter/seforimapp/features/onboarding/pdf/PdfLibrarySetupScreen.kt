package io.github.kdroidfilter.seforimapp.features.onboarding.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.features.pdf.TalmudPdfService
import io.github.kdroidfilter.seforimapp.icons.Book
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.pdf_download_library
import seforimapp.seforimapp.generated.resources.pdf_import_archive
import seforimapp.seforimapp.generated.resources.pdf_install_failed
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_body
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_later
import seforimapp.seforimapp.generated.resources.pdf_install_prompt_title
import seforimapp.seforimapp.generated.resources.pdf_install_success
import seforimapp.seforimapp.generated.resources.pdf_installing
import java.io.File

@Composable
fun PdfLibrarySetupScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(false) }

    fun continueToProfile() {
        navController.navigate(OnBoardingDestination.UserProfilScreen) {
            popUpTo<OnBoardingDestination.PdfLibrarySetupScreen> { inclusive = true }
        }
    }

    LaunchedEffect(Unit) { progressBarState.setProgress(0.9f) }

    OnBoardingScaffold(title = stringResource(Res.string.pdf_install_prompt_title)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Icon(Book, contentDescription = null, modifier = Modifier.size(72.dp), tint = JewelTheme.globalColors.text.normal)
            Text(
                text = stringResource(Res.string.pdf_install_prompt_body),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            if (installing) Text(stringResource(Res.string.pdf_installing))
            if (installed) InlineSuccessBanner(text = stringResource(Res.string.pdf_install_success), modifier = Modifier.fillMaxWidth(0.7f))
            error?.let { InlineErrorBanner(text = stringResource(Res.string.pdf_install_failed).format(it), modifier = Modifier.fillMaxWidth(0.7f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(enabled = !installing, onClick = {
                    installing = true
                    error = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { TalmudPdfService.downloadAndInstall() } }
                            .onSuccess { installed = true; continueToProfile() }
                            .onFailure { error = it.message }
                        installing = false
                    }
                }) { Text(stringResource(Res.string.pdf_download_library)) }
                OutlinedButton(enabled = !installing, onClick = {
                    scope.launch {
                        val selected = withContext(Dispatchers.IO) {
                            FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("zst", "tar.zst")))
                        }
                        if (selected != null) {
                            installing = true
                            error = null
                            runCatching { withContext(Dispatchers.IO) { TalmudPdfService.importArchive(File(selected.path)) } }
                                .onSuccess { installed = true; continueToProfile() }
                                .onFailure { error = it.message }
                            installing = false
                        }
                    }
                }) { Text(stringResource(Res.string.pdf_import_archive)) }
            }
            OutlinedButton(enabled = !installing, onClick = ::continueToProfile) {
                Text(stringResource(Res.string.pdf_install_prompt_later))
            }
        }
    }
}
