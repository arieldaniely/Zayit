package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset

@Serializable
internal data class PendingSearchFeedback(
    val occurredAt: String,
    val payload: JsonObject,
    val serverEventId: String? = null,
)

/** Atomic per-event files survive crashes and keep the original payload across retries. */
internal class SearchFeedbackOutbox(
    private val directory: Path,
    private val now: () -> Instant = Instant::now,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(
        eventId: String,
        occurredAt: String,
        payload: String,
    ) {
        require(eventId.matches(Regex("[A-Za-z0-9_-]+")))
        write(directory.resolve("$eventId.json"), PendingSearchFeedback(occurredAt, json.parseToJsonElement(payload).jsonObject))
    }

    fun flush(
        transport: GradioFeedbackTransport,
        enabled: () -> Boolean,
    ) {
        if (!Files.isDirectory(directory)) return
        val cutoff = now().atZone(ZoneOffset.UTC).minusMonths(2).toInstant()
        Files.newDirectoryStream(directory, "feedback-*.tmp").use { paths ->
            for (path in paths) {
                if (!Files.getLastModifiedTime(path).toInstant().isAfter(cutoff)) Files.deleteIfExists(path)
            }
        }
        val pending = mutableListOf<Pair<Path, PendingSearchFeedback>>()
        Files.newDirectoryStream(directory, "*.json").use { paths ->
            for (path in paths) {
                try {
                    val event = json.decodeFromString<PendingSearchFeedback>(Files.readString(path))
                    if (!Instant.parse(event.occurredAt).isAfter(cutoff)) {
                        Files.deleteIfExists(path)
                    } else {
                        pending += path to event
                    }
                } catch (failure: Exception) {
                    if (!Files.getLastModifiedTime(path).toInstant().isAfter(cutoff)) Files.deleteIfExists(path)
                    warnln { "Unreadable search feedback cache entry (${failure.javaClass.simpleName})" }
                }
            }
        }
        for ((path, original) in pending.sortedBy { it.second.occurredAt }) {
            if (!enabled()) return
            var event = original
            try {
                val serverEventId =
                    event.serverEventId ?: transport.submit(event.payload.toString()).also { id ->
                        event = event.copy(serverEventId = id)
                        write(path, event)
                    }
                transport.awaitCompletion(serverEventId)
                Files.deleteIfExists(path)
            } catch (expired: FeedbackEventExpiredException) {
                // Gradio no longer knows this job: submit the same eventId on the next attempt.
                write(path, event.copy(serverEventId = null))
                throw expired
            }
        }
    }

    private fun write(
        path: Path,
        event: PendingSearchFeedback,
    ) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "feedback-", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(event))
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
