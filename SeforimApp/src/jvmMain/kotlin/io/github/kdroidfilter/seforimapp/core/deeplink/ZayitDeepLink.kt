package io.github.kdroidfilter.seforimapp.core.deeplink

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Public, shareable deep link scheme for Zayit.
 *
 * Links reference the stable book / line database identifiers, so a link resolves to the
 * same content on any machine running the same database version:
 *
 *  - zayit://book/<bookId>                 -> open the book
 *  - zayit://book/<bookId>/line/<lineId>   -> open the book scrolled to a precise line
 *  - zayit://search/<url-encoded-query>    -> open a search
 */
const val ZAYIT_SCHEME = "zayit"
const val OTZARIA_SCHEME = "otzaria"

private const val PREFIX = "$ZAYIT_SCHEME://"
private const val HOST_BOOK = "book"
private const val HOST_SEARCH = "search"
private const val SEGMENT_LINE = "line"

data class ParsedContentDeepLink(
    val destination: TabsDestination,
    val lineIndex: Int? = null,
    val highlightText: String? = null,
    val markLine: Boolean = false,
)

/** Builds a shareable link to a book, optionally pinned to a precise line. */
fun bookShareLink(
    bookId: Long,
    lineId: Long? = null,
): String =
    buildString {
        append(PREFIX).append(HOST_BOOK).append('/').append(bookId)
        if (lineId != null && lineId != 0L && lineId != -1L) append('/').append(SEGMENT_LINE).append('/').append(lineId)
    }

/** Builds a shareable link to a search query. */
fun searchShareLink(query: String): String = PREFIX + HOST_SEARCH + "/" + URLEncoder.encode(query, StandardCharsets.UTF_8)

/**
 * Returns a shareable link for this destination, or null when there is nothing worth sharing
 * (the Home screen, or a book that has not finished loading — bookId not yet assigned).
 */
fun TabsDestination.toShareLink(): String? =
    when (this) {
        is TabsDestination.BookContent -> if (bookId != 0L && bookId != -1L) bookShareLink(bookId, lineId) else null
        is TabsDestination.PdfContent -> if (bookId != 0L && bookId != -1L) bookShareLink(bookId, lineId) else null
        is TabsDestination.Search -> searchShareLink(searchQuery)
        is TabsDestination.Home -> null
        is TabsDestination.History -> null
        is TabsDestination.Favorites -> null
    }

/**
 * Parses a zayit:// deep link into a navigable destination carrying a fresh tabId, or null when
 * the URI does not match a known content scheme. Resolution against the database (e.g. checking
 * the book exists) is the caller's responsibility.
 */
fun parseZayitDeepLink(uri: String): TabsDestination? {
    val parsedUri = runCatching { URI(uri.trim()) }.getOrNull() ?: return null
    if (!parsedUri.scheme.equals(ZAYIT_SCHEME, ignoreCase = true)) return null
    return parseNativeLink(parsedUri)?.destination
}

/** Parses Zayit links and the book-opening subset of the compatible `otzaria://` scheme. */
fun parseContentDeepLink(uri: String): ParsedContentDeepLink? {
    val parsedUri = runCatching { URI(uri.trim()) }.getOrNull() ?: return null
    return when (parsedUri.scheme?.lowercase()) {
        ZAYIT_SCHEME -> parseNativeLink(parsedUri)
        OTZARIA_SCHEME -> parseOtzariaBookLink(parsedUri)
        else -> null
    }
}

private fun parseNativeLink(uri: URI): ParsedContentDeepLink? {
    val host = uri.host?.lowercase() ?: return null
    val path =
        buildString {
            append(host)
            if (!uri.rawPath.isNullOrEmpty()) append(uri.rawPath)
        }
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    val newTabId = UUID.randomUUID().toString()
    val destination =
        when (segments[0].lowercase()) {
            HOST_BOOK -> {
                val bookId = segments.getOrNull(1)?.toLongOrNull() ?: return null
                val lineId =
                    if (segments.getOrNull(2)?.equals(SEGMENT_LINE, ignoreCase = true) == true) {
                        segments.getOrNull(3)?.toLongOrNull() ?: return null
                    } else {
                        null
                    }
                TabsDestination.BookContent(bookId = bookId, tabId = newTabId, lineId = lineId)
            }
            HOST_SEARCH -> {
                val encoded = segments.drop(1).joinToString("/")
                if (encoded.isEmpty()) return null
                TabsDestination.Search(searchQuery = URLDecoder.decode(encoded, StandardCharsets.UTF_8), tabId = newTabId)
            }
            else -> null
        }
    return destination?.let(::ParsedContentDeepLink)
}

private fun parseOtzariaBookLink(uri: URI): ParsedContentDeepLink? {
    if (!uri.host.equals("open", ignoreCase = true)) return null
    val segments =
        uri.path
            .orEmpty()
            .split('/')
            .filter { it.isNotEmpty() }
    if (!segments.getOrNull(0).equals("book", ignoreCase = true)) return null
    val bookId = segments.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    if (segments.size != 2) return null

    val params = parseQuery(uri.rawQuery)
    val lineIndex = params["index"]?.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 }
    val highlightText =
        params["m"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: params["q"]?.firstOrNull()?.takeIf { it.isNotBlank() }
    return ParsedContentDeepLink(
        destination = TabsDestination.BookContent(bookId, UUID.randomUUID().toString()),
        lineIndex = lineIndex,
        highlightText = highlightText,
        markLine = params.containsKey("mark"),
    )
}

private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
    if (rawQuery.isNullOrEmpty()) return emptyMap()
    return rawQuery.split('&').groupBy(
        keySelector = { it.substringBefore('=').lowercase() },
        valueTransform = { URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) },
    )
}
