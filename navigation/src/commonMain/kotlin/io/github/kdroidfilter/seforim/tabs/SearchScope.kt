package io.github.kdroidfilter.seforim.tabs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Where a search runs: the whole library, one author's books, or one book or TOC entry. */
@Serializable
@Immutable
sealed interface SearchScope {
    @Serializable
    data class Favorites(
        val folderId: Long? = null,
    ) : SearchScope

    @Serializable
    data object Global : SearchScope

    /** All books in a category and every descendant category. */
    @Serializable
    data class Category(
        val categoryId: Long,
    ) : SearchScope

    @Serializable
    data class Book(
        val bookId: Long,
    ) : SearchScope

    @Serializable
    data class Toc(
        val bookId: Long,
        val tocId: Long,
    ) : SearchScope

    /** All the books of one author, base books or not. */
    @Serializable
    data class Author(
        val authorId: Long,
    ) : SearchScope
}
