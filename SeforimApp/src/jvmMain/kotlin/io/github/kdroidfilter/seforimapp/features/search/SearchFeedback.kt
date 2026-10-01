package io.github.kdroidfilter.seforimapp.features.search

import dev.nucleusframework.updater.UpdaterConfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.warnln
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class SearchFeedbackType { LIKE, DISLIKE, ENTRY }

@Serializable
internal data class FeedbackSearchContext(
    val searchId: String = UUID.randomUUID().toString(),
    val query: String,
    val requestedMode: SearchMode,
    val near: Int,
    val bookFilter: Long? = null,
    val categoryFilter: Long? = null,
    val bookIds: List<Long>? = null,
    val lineIds: List<Long>? = null,
    val baseBookOnly: Boolean,
)

internal interface FeedbackSearchSession : SearchSession {
    val feedbackContext: FeedbackSearchContext
}

@Serializable
internal data class SearchFeedbackPayload(
    val schemaVersion: Int = 1,
    val eventId: String = UUID.randomUUID().toString(),
    // The server uses this ID as its dataset filename, making resubmissions idempotent.
    val id: String = eventId,
    val occurredAt: String = Instant.now().toString(),
    val software: String = "זיתא",
    val softwareVersion: String,
    val databaseVersion: String?,
    val modelVersion: String?,
    val feedbackType: String,
    val search: FeedbackSearchContext,
    val effectiveSearchMode: String,
    val bookId: Long,
    val bookTitle: String,
    val lineId: Long,
    val lineIndex: Int,
    val paragraph: String,
    val paragraphHtml: String,
    val snippetHtml: String,
    val matchedParts: List<String>,
    val resultPosition: Int?,
    val retrievalPosition: Int?,
    val relevanceScore: Double,
    val loadedResultCount: Int,
    val totalResultCount: Long?,
    val selectedBookIds: List<Long>,
    val selectedCategoryIds: List<Long>,
    val selectedTocIds: List<Long>,
    val viewBookId: Long?,
    val viewCategoryId: Long?,
    val viewTocId: Long?,
    val destination: String?,
)

internal fun matchedFeedbackParts(snippet: String): List<String> =
    Jsoup
        .parseBodyFragment(snippet)
        .select("b, strong, mark")
        .map { it.text() }
        .filter { it.isNotBlank() }

/** App lifetime queue: navigating away from a search must not cancel an entry event. */
@SingleIn(AppScope::class)
@Inject
class SearchFeedbackService(
    private val repository: SeforimRepository,
) {
    private val queue = Channel<suspend () -> Unit>(capacity = 128)
    private val sendSignal = Channel<Unit>(Channel.CONFLATED)
    private val transport = GradioFeedbackTransport()
    private val payloadJson = Json { encodeDefaults = true }
    private val outbox by lazy {
        SearchFeedbackOutbox(Path.of(getUserSettingsDatabasePath()).parent.resolve("search-feedback"))
    }

    suspend fun run(): Unit =
        coroutineScope {
            launch(Dispatchers.IO) {
                for (save in queue) {
                    if (!AppSettings.isSearchFeedbackEnabled()) continue
                    try {
                        save()
                        sendSignal.trySend(Unit)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        // Do not put user queries or paragraphs in logs.
                        warnln { "Search feedback could not be cached (${failure.javaClass.simpleName})" }
                    }
                }
            }
            launch(Dispatchers.IO) {
                while (true) {
                    var failed = false
                    try {
                        outbox.flush(transport, AppSettings::isSearchFeedbackEnabled)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failed = true
                        warnln { "Search feedback delivery deferred (${failure.javaClass.simpleName})" }
                    }
                    // Retry on startup, after a new event, and every five minutes while idle/offline.
                    if (failed) delay(300_000L) else withTimeoutOrNull(300_000L) { sendSignal.receive() }
                }
            }
        }

    internal fun record(
        result: SearchResult,
        type: SearchFeedbackType,
        context: FeedbackSearchContext,
        effectiveMode: SearchMode,
        position: Int?,
        retrievalPosition: Int?,
        loadedCount: Int,
        totalCount: Long?,
        selectedBooks: List<Long>,
        selectedCategories: List<Long>,
        selectedTocs: List<Long>,
        viewBookId: Long?,
        viewCategoryId: Long?,
        viewTocId: Long?,
        destination: String? = null,
    ) {
        if (!AppSettings.isSearchFeedbackEnabled()) return
        if (type != SearchFeedbackType.ENTRY && effectiveMode != SearchMode.SMART) return
        val occurredAt = Instant.now().toString()
        val accepted =
            queue.trySend {
                val line = repository.getLine(result.lineId) ?: return@trySend
                val payload =
                    SearchFeedbackPayload(
                        occurredAt = occurredAt,
                        softwareVersion = UpdaterConfig().currentVersion,
                        databaseVersion = DatabaseVersionManager.getCurrentDatabaseVersion(),
                        modelVersion = if (effectiveMode == SearchMode.SMART) SemanticAssetsManager.feedbackModelVersion else null,
                        feedbackType = type.name.lowercase(),
                        search = context,
                        effectiveSearchMode = effectiveMode.name.lowercase(),
                        bookId = result.bookId,
                        bookTitle = result.bookTitle,
                        lineId = result.lineId,
                        lineIndex = result.lineIndex,
                        paragraph = Jsoup.parseBodyFragment(line.content).text(),
                        paragraphHtml = line.content,
                        snippetHtml = result.snippet,
                        matchedParts = matchedFeedbackParts(result.snippet),
                        resultPosition = position,
                        retrievalPosition = retrievalPosition,
                        relevanceScore = result.rank,
                        loadedResultCount = loadedCount,
                        totalResultCount = totalCount,
                        selectedBookIds = selectedBooks,
                        selectedCategoryIds = selectedCategories,
                        selectedTocIds = selectedTocs,
                        viewBookId = viewBookId,
                        viewCategoryId = viewCategoryId,
                        viewTocId = viewTocId,
                        destination = destination,
                    )
                if (AppSettings.isSearchFeedbackEnabled()) outbox.save(payload.eventId, occurredAt, payloadJson.encodeToString(payload))
            }
        if (accepted.isFailure) warnln { "Search feedback queue is full" }
    }
}

internal class GradioFeedbackTransport(
    private val apiBase: String = FEEDBACK_API,
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()

    fun deliver(payload: String) {
        awaitCompletion(submit(payload))
    }

    fun submit(payload: String): String {
        val request =
            HttpRequest
                .newBuilder(URI.create("$apiBase/call/v2/save_feedback"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":$payload}"))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Feedback submission failed" }
        val eventId =
            Json
                .parseToJsonElement(response.body())
                .jsonObject["event_id"]
                ?.jsonPrimitive
                ?.content
        check(eventId != null && eventId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid feedback event ID" }
        return eventId
    }

    fun awaitCompletion(eventId: String) {
        val completion =
            client.send(
                HttpRequest
                    .newBuilder(URI.create("$apiBase/call/save_feedback/$eventId"))
                    .timeout(Duration.ofSeconds(45))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        if (completion.statusCode() == 404 ||
            completion.statusCode() == 410 ||
            completion.body().lineSequence().any { it.trim() == "event: error" } ||
            (completion.body().lineSequence().any { it.trim() == "event: complete" } && !feedbackCompleted(completion.body()))
        ) {
            throw FeedbackEventExpiredException()
        }
        check(completion.statusCode() in 200..299 && feedbackCompleted(completion.body())) { "Feedback save failed" }
    }
}

internal fun feedbackCompleted(events: String): Boolean {
    if (events.lineSequence().any { it.trim() == "event: error" }) return false
    val frame =
        events
            .split(Regex("\\r?\\n\\r?\\n"))
            .firstOrNull { block -> block.lineSequence().any { it.trim() == "event: complete" } } ?: return false
    val data = frame.lineSequence().filter { it.startsWith("data:") }.joinToString("\n") { it.removePrefix("data:").trim() }
    val result = runCatching { Json.parseToJsonElement(data) }.getOrNull() ?: return false
    val returned = (result as? JsonArray)?.singleOrNull() as? JsonObject ?: return false
    return returned["success"]?.jsonPrimitive?.booleanOrNull == true && !feedbackResponseFailed(result)
}

private fun feedbackResponseFailed(result: JsonElement): Boolean =
    when (result) {
        is JsonArray -> result.any(::feedbackResponseFailed)
        is JsonObject ->
            result["success"]?.jsonPrimitive?.content == "false" ||
                result["status"]?.jsonPrimitive?.content in setOf("error", "failed", "failure") ||
                (result["error"] != null && result["error"] != JsonNull)
        else -> false
    }

private const val FEEDBACK_API = "https://arielll123-meivin-api-data.hf.space/gradio_api"

internal class FeedbackEventExpiredException : Exception()
