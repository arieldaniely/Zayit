package io.github.kdroidfilter.seforimapp.core.favorites

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.db.Favorite
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One favorited book position, shared by every folder it belongs to. */
@Stable
data class FavoriteEntry(
    val key: String,
    val bookId: Long,
    val title: String,
    val folderId: Long?,
    val createdAt: Long,
    val tocEntryId: Long? = null,
    val lineId: Long? = null,
    val folderIds: Set<Long> = folderId?.let(::setOf).orEmpty(),
)

@Stable
data class FavoriteFolder(
    val id: Long,
    val name: String,
)

/**
 * Persistent favorites (the Chrome bookmarks equivalent): book positions the user starred,
 * deduplicated by target key and optionally included in several user-created folders.
 *
 * Reads are on-demand SQL queries; [revision] bumps on every write so UI can re-run its
 * current query reactively.
 */
class FavoritesStore(
    database: UserSettingsDb,
) {
    private val queries = database.favoritesQueries

    init {
        queries.transaction {
            queries.migrateLegacyFolders()
            queries.clearLegacyFolders()
        }
    }

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Same key scheme as the visit history so a position stars consistently. */
    fun keyFor(
        bookId: Long,
        tocEntryId: Long?,
    ): String = if (tocEntryId != null) "book:$bookId:$tocEntryId" else "book:$bookId"

    suspend fun add(
        bookId: Long,
        title: String,
        timestamp: Long,
        tocEntryId: Long? = null,
        lineId: Long? = null,
        folderId: Long? = null,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext
            val key = keyFor(bookId, tocEntryId)
            queries.transaction {
                queries.upsertFavorite(
                    key = key,
                    bookId = bookId,
                    title = title,
                    folderId = null,
                    createdAt = timestamp,
                    tocEntryId = tocEntryId,
                    lineId = lineId,
                )
                folderId?.let { queries.addFavoriteToFolder(key, it) }
            }
            _revision.update { it + 1 }
        }

    suspend fun remove(key: String): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.clearFavoriteFolders(key)
                queries.deleteFavoriteByKey(key)
            }
            _revision.update { it + 1 }
        }

    suspend fun isFavorite(key: String): Boolean =
        withContext(Dispatchers.IO) {
            queries.countFavorite(key).executeAsOne() > 0
        }

    suspend fun get(key: String): FavoriteEntry? =
        withContext(Dispatchers.IO) {
            queries.selectFavoriteByKey(key).executeAsOneOrNull()?.toEntry()
        }

    suspend fun setFolder(
        key: String,
        folderId: Long?,
    ): Unit = setFolders(key, folderId?.let(::setOf).orEmpty())

    suspend fun setFolders(
        key: String,
        folderIds: Set<Long>,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.clearFavoriteFolders(key)
                folderIds.forEach { queries.addFavoriteToFolder(key, it) }
            }
            _revision.update { it + 1 }
        }

    suspend fun toggleFolder(
        key: String,
        folderId: Long,
    ): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                if (folderId in queries.selectFavoriteFolderIds(key).executeAsList()) {
                    queries.removeFavoriteFromFolder(key, folderId)
                } else {
                    queries.addFavoriteToFolder(key, folderId)
                }
            }
            _revision.update { it + 1 }
        }

    /** Creates a folder and returns its id. */
    suspend fun createFolder(
        name: String,
        timestamp: Long,
    ): Long =
        withContext(Dispatchers.IO) {
            queries.insertFolder(name = name.trim(), createdAt = timestamp)
            val id = queries.lastInsertedFolderId().executeAsOne()
            _revision.update { it + 1 }
            id
        }

    /** Deletes a folder; its favorites move back to the root. */
    suspend fun deleteFolder(id: Long): Unit =
        withContext(Dispatchers.IO) {
            queries.transaction {
                queries.clearFolderMemberships(id)
                queries.deleteFolder(id)
            }
            _revision.update { it + 1 }
        }

    suspend fun folders(): List<FavoriteFolder> =
        withContext(Dispatchers.IO) {
            queries.selectFolders().executeAsList().map { FavoriteFolder(id = it.id, name = it.name) }
        }

    /** All favorites, or those whose title matches [query] when non-blank. */
    suspend fun query(query: String = ""): List<FavoriteEntry> =
        withContext(Dispatchers.IO) {
            val rows =
                if (query.isBlank()) {
                    queries.selectAllFavorites().executeAsList()
                } else {
                    val escaped =
                        query
                            .trim()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_")
                    queries.searchFavoritesByTitle(escaped).executeAsList()
                }
            rows.toEntries()
        }

    /** A bounded Home card query; no other folders' entries are loaded. */
    suspend fun recent(
        limit: Int,
        folderId: Long? = null,
    ): List<FavoriteEntry> =
        withContext(Dispatchers.IO) {
            require(limit in 1..100)
            val rows =
                if (folderId == null) {
                    queries.selectRecentFavorites(limit.toLong()).executeAsList()
                } else {
                    queries.selectRecentInFolder(folderId, limit.toLong()).executeAsList()
                }
            rows.toEntries()
        }

    private fun List<Favorite>.toEntries(): List<FavoriteEntry> {
        val memberships = queries.selectFavoriteMemberships().executeAsList().groupBy({ it.favoriteKey }, { it.folderId })
        return map { it.toEntry(memberships[it.key].orEmpty().toSet()) }
    }

    private fun Favorite.toEntry(folderIds: Set<Long> = queries.selectFavoriteFolderIds(key).executeAsList().toSet()): FavoriteEntry =
        FavoriteEntry(
            key = key,
            bookId = bookId,
            title = title,
            folderId = folderIds.firstOrNull(),
            createdAt = createdAt,
            tocEntryId = tocEntryId,
            lineId = lineId,
            folderIds = folderIds,
        )
}
