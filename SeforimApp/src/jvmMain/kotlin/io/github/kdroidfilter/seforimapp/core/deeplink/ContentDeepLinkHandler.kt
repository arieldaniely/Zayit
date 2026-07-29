package io.github.kdroidfilter.seforimapp.core.deeplink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Resolves incoming zayit:// content deep links and opens them in a new tab of the window that is
 * focused when the link arrives.
 *
 * Cross-platform (unlike [AppJumpList], which is Windows-only): the transport — cold-start CLI
 * arg, single-instance relay, macOS Apple Events — is provided by Nucleus and surfaced through
 * [pendingDeepLink]. Internal seforim:// jump-list URIs are ignored here and left to [AppJumpList].
 */
@Composable
fun ContentDeepLinkHandler(
    desktopManager: DesktopManager,
    repository: SeforimRepository,
    pendingDeepLink: StateFlow<String?>,
    onClearDeepLink: () -> Unit,
) {
    val currentClear by rememberUpdatedState(onClearDeepLink)
    LaunchedEffect(Unit) {
        pendingDeepLink.filterNotNull().collect { raw ->
            val parsed = parseContentDeepLink(raw) ?: return@collect
            val destination = resolveContentDeepLink(parsed, repository)
            if (destination == null) {
                warnln { "Ignoring deep link to unknown reference: $raw" }
            } else {
                applyDeepLinkHighlight(parsed, destination)
                val window = desktopManager.focusedWindow()
                if (window != null) {
                    window.tabsViewModel.openTab(destination)
                    window.requestFocus()
                }
            }
            currentClear()
        }
    }
}

suspend fun resolveContentDeepLink(
    parsed: ParsedContentDeepLink,
    repository: SeforimRepository,
): TabsDestination? {
    val destination = parsed.destination
    if (destination !is TabsDestination.BookContent) return destination
    if (repository.getBookCore(destination.bookId) == null) return null
    val lineId = parsed.lineIndex?.let { repository.getLineByIndex(destination.bookId, it)?.id ?: return null }
    return if (lineId == null) destination else destination.copy(lineId = lineId)
}

fun applyDeepLinkHighlight(
    parsed: ParsedContentDeepLink,
    destination: TabsDestination,
) {
    if (destination !is TabsDestination.BookContent) return
    AppSettings.setDeepLinkMarkedLine(destination.tabId, destination.lineId.takeIf { parsed.markLine })
    parsed.highlightText?.let { text ->
        AppSettings.setFindSmartMode(destination.tabId, false)
        AppSettings.setFindQuery(destination.tabId, text)
        AppSettings.openFindBar(destination.tabId)
    }
}
