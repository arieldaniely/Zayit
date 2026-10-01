package io.github.kdroidfilter.seforimapp.features.search.domain

import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.search.LineHit
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Serializes snippet work for displayed pages and discards updates from replaced sessions. */
internal class ProgressiveSearchHighlighter(
    private val scope: CoroutineScope,
    private val onUpdate: (LineHit) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var session: SearchSession? = null
    private var pages: Channel<List<LineHit>>? = null
    private var worker: Job? = null

    @Synchronized
    fun start(nextSession: SearchSession) {
        cancel()
        session = nextSession
        val queue = Channel<List<LineHit>>(Channel.UNLIMITED)
        pages = queue
        worker =
            launchWorker(scope) {
                for (hits in queue) {
                    try {
                        nextSession.highlightUpdates(hits).collect { hit ->
                            ensureActive()
                            synchronized(this@ProgressiveSearchHighlighter) {
                                if (session === nextSession) onUpdate(hit)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        warnln { "Background search highlighting failed: ${e.message}" }
                    }
                }
            }
    }

    private fun launchWorker(
        @StructuredScope target: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = target.launch(dispatcher, block = block)

    @Synchronized
    fun submit(
        source: SearchSession,
        hits: List<LineHit>,
    ) {
        if (source === session && hits.isNotEmpty()) pages?.trySend(hits)
    }

    @Synchronized
    fun cancel() {
        session = null
        pages?.cancel()
        pages = null
        worker?.cancel()
        worker = null
    }
}
