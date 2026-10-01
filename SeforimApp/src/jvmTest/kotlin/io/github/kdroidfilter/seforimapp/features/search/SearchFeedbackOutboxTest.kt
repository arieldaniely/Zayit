package io.github.kdroidfilter.seforimapp.features.search

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFeedbackOutboxTest {
    @Test
    fun `failed completion survives restart and resumes same job before deletion`() {
        val directory = Files.createTempDirectory("feedback-retry-")
        val posts = AtomicInteger()
        val status = AtomicInteger(503)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/call/v2/save_feedback") { exchange ->
            posts.incrementAndGet()
            val response = "{\"event_id\":\"saved-job\"}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/call/save_feedback/saved-job") { exchange ->
            val response = "event: complete\ndata: [{\"success\":true}]\n\n".toByteArray()
            exchange.sendResponseHeaders(status.get(), response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val transport = GradioFeedbackTransport("http://127.0.0.1:${server.address.port}")
            val original = SearchFeedbackOutbox(directory)
            original.save("event", Instant.now().toString(), "{\"eventId\":\"event\",\"query\":\"שלום\"}")
            assertFailsWith<IllegalStateException> { original.flush(transport) { true } }
            assertTrue(Files.exists(directory.resolve("event.json")))
            assertTrue(Files.readString(directory.resolve("event.json")).contains("saved-job"))
            status.set(200)
            SearchFeedbackOutbox(directory).flush(transport) { true }
            assertEquals(1, posts.get())
            assertFalse(Files.exists(directory.resolve("event.json")))
        } finally {
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `expired events are pruned after two calendar months while disabled`() {
        val directory = Files.createTempDirectory("feedback-expiry-")
        try {
            val now = Instant.parse("2026-10-01T12:00:00Z")
            val outbox = SearchFeedbackOutbox(directory) { now }
            outbox.save("expired", "2026-08-01T12:00:00Z", "{}")
            outbox.save("recent", "2026-08-01T12:00:01Z", "{}")
            outbox.flush(GradioFeedbackTransport("http://127.0.0.1:1")) { false }
            assertFalse(Files.exists(directory.resolve("expired.json")))
            assertTrue(Files.exists(directory.resolve("recent.json")))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `offline submission retains original event for future attempt`() {
        val directory = Files.createTempDirectory("feedback-offline-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/call/v2/save_feedback") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val outbox = SearchFeedbackOutbox(directory)
            outbox.save("offline", Instant.now().toString(), "{\"eventId\":\"offline\"}")
            val original = Files.readString(directory.resolve("offline.json"))
            assertFailsWith<IllegalStateException> {
                outbox.flush(GradioFeedbackTransport("http://127.0.0.1:${server.address.port}")) { true }
            }
            assertEquals(original, Files.readString(directory.resolve("offline.json")))
        } finally {
            server.stop(0)
            directory.toFile().deleteRecursively()
        }
    }
}
