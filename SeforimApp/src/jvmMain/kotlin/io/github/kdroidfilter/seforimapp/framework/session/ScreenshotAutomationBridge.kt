package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Opt-in file bridge used by the interactive website-screenshot recorder.
 *
 * It is completely dormant unless [BRIDGE_DIRECTORY_ENV] is set before the app starts. A request
 * file is claimed atomically, processed on the app coroutine, and answered with a matching response
 * file. This lets an external recorder export and restore the real in-memory tab/session state
 * without exposing a production network endpoint.
 */
object ScreenshotAutomationBridge {
    private const val BRIDGE_DIRECTORY_ENV = "ZAYIT_SCREENSHOT_BRIDGE_DIR"

    val isEnabled: Boolean
        get() = !System.getenv(BRIDGE_DIRECTORY_ENV).isNullOrBlank()

    suspend fun run(appGraph: AppGraph) {
        val directory =
            System.getenv(BRIDGE_DIRECTORY_ENV)
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?: return
        directory.mkdirs()

        while (true) {
            currentCoroutineContext().ensureActive()
            directory.listFiles { file -> file.extension == "request" }
                .orEmpty()
                .sortedBy { it.name }
                .forEach { request -> processRequest(appGraph, directory, request) }
            delay(100)
        }
    }

    private suspend fun processRequest(
        appGraph: AppGraph,
        directory: File,
        request: File,
    ) {
        val claimed = File(directory, "${request.nameWithoutExtension}.processing")
        if (!request.renameTo(claimed)) return

        val response = File(directory, "${request.nameWithoutExtension}.response")
        val temporaryResponse = File(directory, "${request.nameWithoutExtension}.response.tmp")
        val result =
            runCatching {
                val fields = claimed.readText().split('\t', limit = 2)
                require(fields.size == 2) { "Malformed screenshot automation request" }
                val snapshot = File(fields[1])
                when (fields[0]) {
                    "record" -> SessionManager.exportSnapshot(appGraph, snapshot)
                    "record-settings" -> ScreenshotVisualSettings.exportTo(snapshot)
                    "settings" -> ScreenshotVisualSettings.importFrom(snapshot, appGraph)
                    "clipboard-demo" -> {
                        ScreenshotAutomationState.requestClipboardDemo()
                        delay(500)
                    }
                    "verify-density" -> ScreenshotAutomationState.requireRenderedDensity(fields[1].toFloat())
                    "verify-platform" ->
                        require(PlatformInfo.isWindows) {
                            "Screenshot replay requires Windows controls; detected ${PlatformInfo.currentOS}"
                        }
                    "restore" -> SessionManager.importSnapshot(appGraph, snapshot)
                    "scenario" -> ScreenshotScenarioReplay.prepare(appGraph, fields[1])
                    "theme" -> {
                        val theme =
                            when (fields[1].lowercase()) {
                                "light" -> IntUiThemes.Light
                                "dark" -> IntUiThemes.Dark
                                else -> error("Unknown screenshot theme: ${fields[1]}")
                            }
                        appGraph.mainAppState.setTheme(theme)
                        delay(500)
                    }
                    else -> error("Unknown screenshot automation action: ${fields[0]}")
                }
            }.fold(
                onSuccess = { "ok" },
                onFailure = { error -> "error\t${error.message.orEmpty()}" },
            )
        temporaryResponse.writeText(result)
        check(temporaryResponse.renameTo(response)) { "Could not publish automation response" }
        claimed.delete()
    }
}
