package io.github.kdroidfilter.seforimapp.core.catalog

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class LibraryEntryKind { CATEGORY, BOOK }

data class LibraryVisibilitySelection(
    val categoryIds: Set<Long> = emptySet(),
    val bookIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = categoryIds.isEmpty() && bookIds.isEmpty()
}

data class LibraryVisibilitySnapshot(
    val hiddenCategoryIds: Set<Long> = emptySet(),
    val hiddenBookIds: Set<Long> = emptySet(),
    /** Null means unrestricted; an empty set means every book is hidden. */
    val allowedBookIds: Set<Long>? = null,
) {
    fun isCategoryVisible(id: Long): Boolean = id !in hiddenCategoryIds

    fun isBookVisible(id: Long): Boolean = id !in hiddenBookIds
}

data class LibraryVisibilityEntry(
    val kind: LibraryEntryKind,
    val id: Long,
    val title: String,
    val path: List<String>,
    val categoryId: Long,
    val authors: List<String> = emptyList(),
) {
    private val titleKey = (path + title).joinToString(" ").searchKey()
    private val authorKey = authors.joinToString(" ").searchKey()

    internal fun matches(
        titleWords: List<String>,
        authorWords: List<String>,
    ): Boolean =
        titleWords.all { titleKey.contains(it, ignoreCase = true) } &&
            authorWords.all { authorKey.contains(it, ignoreCase = true) }
}

internal fun filterLibraryEntries(
    entries: List<LibraryVisibilityEntry>,
    kind: LibraryEntryKind,
    title: String,
    author: String,
): List<LibraryVisibilityEntry> {
    val titleWords = title.searchKey().split(' ').filter(String::isNotEmpty)
    val authorWords = if (kind == LibraryEntryKind.BOOK) author.searchKey().split(' ').filter(String::isNotEmpty) else emptyList()
    return entries.filter { it.kind == kind && it.matches(titleWords, authorWords) }
}

/** Local display preferences stored in the backed-up user database, without changing the books database. */
class LibraryVisibility(
    private val database: UserSettingsDb,
    private val catalogProvider: () -> PrecomputedCatalog?,
) {
    private val mutex = Mutex()
    private val _selection = MutableStateFlow(readSelection())
    val selection = _selection.asStateFlow()

    private var cachedSource: PrecomputedCatalog? = null
    private var cachedSelection: LibraryVisibilitySelection? = null
    private var cachedSnapshot = LibraryVisibilitySnapshot()

    private fun readSelection(): LibraryVisibilitySelection {
        val rows = database.libraryVisibilityQueries.selectAll().executeAsList()
        return LibraryVisibilitySelection(
            categoryIds = rows.filter { it.kind == LibraryEntryKind.CATEGORY.name }.mapTo(HashSet()) { it.itemId },
            bookIds = rows.filter { it.kind == LibraryEntryKind.BOOK.name }.mapTo(HashSet()) { it.itemId },
        )
    }

    suspend fun setHidden(
        kind: LibraryEntryKind,
        ids: Collection<Long>,
        hidden: Boolean,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.libraryVisibilityQueries.transaction {
                ids.forEach { id ->
                    if (hidden) {
                        database.libraryVisibilityQueries.hide(kind.name, id)
                    } else {
                        database.libraryVisibilityQueries.show(kind.name, id)
                    }
                }
            }
            _selection.value = readSelection()
        }
    }

    suspend fun restoreAll() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                database.libraryVisibilityQueries.restoreAll()
                _selection.value = LibraryVisibilitySelection()
            }
        }

    @Synchronized
    fun snapshot(): LibraryVisibilitySnapshot {
        val selected = selection.value
        if (selected.isEmpty) return LibraryVisibilitySnapshot()
        val catalog = catalogProvider()
        if (cachedSource === catalog && cachedSelection == selected) return cachedSnapshot
        return buildVisibilitySnapshot(catalog, selected).also {
            cachedSource = catalog
            cachedSelection = selected
            cachedSnapshot = it
        }
    }

    fun entries(): List<LibraryVisibilityEntry> {
        val entries = ArrayList<LibraryVisibilityEntry>()

        fun walk(
            category: CatalogCategory,
            path: List<String>,
        ) {
            entries += LibraryVisibilityEntry(LibraryEntryKind.CATEGORY, category.id, category.title, path, category.id)
            val nextPath = path + category.title
            category.books.forEach { book ->
                entries += LibraryVisibilityEntry(LibraryEntryKind.BOOK, book.id, book.title, nextPath, category.id, book.authors)
            }
            category.subcategories.forEach { walk(it, nextPath) }
        }
        catalogProvider()?.rootCategories?.forEach { walk(it, emptyList()) }
        return entries
    }
}

internal fun buildVisibilitySnapshot(
    catalog: PrecomputedCatalog?,
    selection: LibraryVisibilitySelection,
): LibraryVisibilitySnapshot {
    if (selection.isEmpty) return LibraryVisibilitySnapshot()
    val hiddenCategories = selection.categoryIds.toMutableSet()
    val hiddenBooks = selection.bookIds.toMutableSet()
    val allowedBooks = HashSet<Long>()

    fun walk(
        category: CatalogCategory,
        parentHidden: Boolean,
    ) {
        val hidden = parentHidden || category.id in selection.categoryIds
        if (hidden) hiddenCategories += category.id
        category.books.forEach { book ->
            if (hidden || book.id in selection.bookIds) hiddenBooks += book.id else allowedBooks += book.id
        }
        category.subcategories.forEach { walk(it, hidden) }
    }
    catalog?.rootCategories?.forEach { walk(it, false) }
    allowedBooks.removeAll(hiddenBooks)
    return LibraryVisibilitySnapshot(hiddenCategories, hiddenBooks, allowedBooks)
}
