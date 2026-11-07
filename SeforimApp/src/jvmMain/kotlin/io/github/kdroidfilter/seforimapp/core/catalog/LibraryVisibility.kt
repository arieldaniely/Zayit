package io.github.kdroidfilter.seforimapp.core.catalog

import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.domain.AuthorNames
import io.github.kdroidfilter.seforimapp.features.search.domain.searchKey
import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.DriverManager

enum class LibraryEntryKind { CATEGORY, BOOK, AUTHOR }

data class LibraryAuthor(
    val id: Long,
    val name: String,
    val era: String?,
    val bookIds: Set<Long>,
)

internal fun readLibraryAuthors(): List<LibraryAuthor> {
    DriverManager.getConnection("jdbc:sqlite:${Path.of(getDatabasePath()).toUri()}?mode=ro").use { connection ->
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    "SELECT a.id, a.name, a.era, ba.bookId FROM author a JOIN book_author ba ON a.id = ba.authorId ORDER BY a.id",
                ).use { rows ->
                    val names = mutableMapOf<Long, Pair<String, String?>>()
                    val books = mutableMapOf<Long, MutableSet<Long>>()
                    while (rows.next()) {
                        val id = rows.getLong(1)
                        names.getOrPut(id) { rows.getString(2) to rows.getString(3) }
                        books.getOrPut(id, ::mutableSetOf).add(rows.getLong(4))
                    }
                    return names.map { (id, name) -> LibraryAuthor(id, name.first, name.second, books.getValue(id)) }
                }
        }
    }
}

data class LibraryVisibilitySelection(
    val categoryIds: Set<Long> = emptySet(),
    val bookIds: Set<Long> = emptySet(),
    val authorIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = categoryIds.isEmpty() && bookIds.isEmpty() && authorIds.isEmpty()
}

data class LibraryVisibilitySnapshot(
    val hiddenCategoryIds: Set<Long> = emptySet(),
    val hiddenBookIds: Set<Long> = emptySet(),
    /** Null means unrestricted; an empty set means every book is hidden. */
    val allowedBookIds: Set<Long>? = null,
    val hiddenAuthorIds: Set<Long> = emptySet(),
) {
    fun isCategoryVisible(id: Long): Boolean = id !in hiddenCategoryIds

    fun isBookVisible(id: Long): Boolean = id !in hiddenBookIds

    fun isAuthorVisible(id: Long): Boolean = id !in hiddenAuthorIds
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
    private val authorProvider: () -> List<LibraryAuthor> = { emptyList() },
    private val catalogProvider: () -> PrecomputedCatalog?,
) {
    private val mutex = Mutex()
    private val _selection = MutableStateFlow(readSelection())
    val selection = _selection.asStateFlow()

    private var cachedSource: PrecomputedCatalog? = null
    private var cachedSelection: LibraryVisibilitySelection? = null
    private var cachedSnapshot = LibraryVisibilitySnapshot()
    private var authorsRevision: Long? = null
    private var cachedAuthors: List<LibraryAuthor> = emptyList()

    @Synchronized
    fun authors(): List<LibraryAuthor> {
        val revision = CatalogCache.revision.value
        if (authorsRevision != revision) {
            cachedAuthors = runCatching(authorProvider).getOrElse { return emptyList() }
            authorsRevision = revision
        }
        return cachedAuthors
    }

    private fun readSelection(): LibraryVisibilitySelection {
        val rows = database.libraryVisibilityQueries.selectAll().executeAsList()
        return LibraryVisibilitySelection(
            categoryIds = rows.filter { it.kind == LibraryEntryKind.CATEGORY.name }.mapTo(HashSet()) { it.itemId },
            bookIds = rows.filter { it.kind == LibraryEntryKind.BOOK.name }.mapTo(HashSet()) { it.itemId },
            authorIds = rows.filter { it.kind == LibraryEntryKind.AUTHOR.name }.mapTo(HashSet()) { it.itemId },
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
        return buildVisibilitySnapshot(catalog, selected, authors()).also {
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
        entries +=
            authors().sortedBy { it.name }.map { author ->
                LibraryVisibilityEntry(LibraryEntryKind.AUTHOR, author.id, AuthorNames.display(author.name), emptyList(), 0)
            }
        return entries
    }
}

internal fun buildVisibilitySnapshot(
    catalog: PrecomputedCatalog?,
    selection: LibraryVisibilitySelection,
    authors: List<LibraryAuthor> = emptyList(),
): LibraryVisibilitySnapshot {
    if (selection.isEmpty) return LibraryVisibilitySnapshot()
    val hiddenCategories = selection.categoryIds.toMutableSet()
    val hiddenBooks = selection.bookIds.toMutableSet()
    authors.filter { it.id in selection.authorIds }.forEach { hiddenBooks += it.bookIds }
    val allowedBooks = HashSet<Long>()

    fun walk(
        category: CatalogCategory,
        parentHidden: Boolean,
    ) {
        val hidden = parentHidden || category.id in selection.categoryIds
        if (hidden) {
            hiddenCategories += category.id
            category.books.forEach { hiddenBooks += it.id }
        }
        category.subcategories.forEach { walk(it, hidden) }
    }

    fun collectVisible(category: CatalogCategory): Boolean {
        var hasVisibleBooks = false
        category.books.forEach { book ->
            if (book.id !in hiddenBooks) {
                hasVisibleBooks = true
                allowedBooks += book.id
            }
        }
        category.subcategories.forEach { if (collectVisible(it)) hasVisibleBooks = true }
        if (!hasVisibleBooks) hiddenCategories += category.id
        return hasVisibleBooks
    }

    catalog?.rootCategories?.forEach { walk(it, false) }
    catalog?.rootCategories?.forEach(::collectVisible)
    val hiddenAuthors = selection.authorIds + authors.filter { it.bookIds.all(hiddenBooks::contains) }.map { it.id }
    return LibraryVisibilitySnapshot(hiddenCategories, hiddenBooks, allowedBooks, hiddenAuthors)
}
