package io.github.kdroidfilter.seforimapp.features.search

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFeedbackTest {
    @Test
    fun `matched parts preserve phrases and decode Hebrew and entities`() {
        assertEquals(
            listOf("שלום עולם", "תורה & מצוות"),
            matchedFeedbackParts("לפני <b>שלום <i>עולם</i></b> אחרי <b>תורה &amp; מצוות</b>"),
        )
        assertEquals(emptyList(), matchedFeedbackParts("תוצאה ללא מילים מודגשות"))
    }

    @Test
    fun `heartbeat and error are not save confirmations`() {
        assertFalse(feedbackCompleted("event: heartbeat\ndata: null\n\n"))
        assertFalse(feedbackCompleted("event: error\ndata: null\n\n"))
        assertFalse(feedbackCompleted("event: complete\ndata: [{\"success\":false}]\n\n"))
        assertFalse(feedbackCompleted("event: complete\ndata: [{\"status\":\"error\"}]\n\n"))
        assertFalse(feedbackCompleted("event: complete\ndata: []\n\nevent: error\ndata: null\n\n"))
        assertTrue(feedbackCompleted("event: heartbeat\ndata: null\n\nevent: complete\ndata: [{\"success\":true}]\n\n"))
    }

    @Test
    fun `transport uses v2 object envelope and consumes completion stream`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        var contentType: String? = null
        var completionRequested = false
        server.createContext("/call/v2/save_feedback") { exchange ->
            requestBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            val response = "{\"event_id\":\"test-event\"}".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/call/save_feedback/test-event") { exchange ->
            completionRequested = true
            val response = "event: complete\ndata: [{\"success\":true}]\n\n".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            GradioFeedbackTransport("http://127.0.0.1:${server.address.port}").deliver("{\"query\":\"שלום\"}")
            assertEquals("application/json", contentType)
            assertEquals(
                "שלום",
                Json
                    .parseToJsonElement(requestBody)
                    .jsonObject["data"]
                    ?.jsonObject
                    ?.get("query")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertTrue(completionRequested)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `transport rejects HTTP failures`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/call/v2/save_feedback") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            assertFailsWith<IllegalStateException> {
                GradioFeedbackTransport("http://127.0.0.1:${server.address.port}").deliver("{}")
            }
        } finally {
            server.stop(0)
        }
    }
}
