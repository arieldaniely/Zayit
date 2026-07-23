package io.github.kdroidfilter.seforimapp.features.personallibrary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.core.models.BookMetadata
import io.github.kdroidfilter.seforimlibrary.core.models.ConnectionType
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import io.github.kdroidfilter.seforimlibrary.search.PersonalLuceneIndexBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.streams.toList

class PersonalLibraryImporter(
    private val baseDatabase: Path,
    private val generationsDirectory: Path,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun fingerprint(folders: List<PersonalBookFolder>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.add(baseDatabase.toAbsolutePath().normalize().toString())
        digest.add(Files.size(baseDatabase).toString())
        digest.add(Files.getLastModifiedTime(baseDatabase).toMillis().toString())
        folders.filter { it.enabled }.sortedBy { it.id }.forEach { folder ->
            digest.add(folder.id); digest.add(folder.path); digest.add(folder.placement.name)
            val root = Path.of(folder.path)
            require(root.isDirectory()) { "תיקיית הספרים אינה זמינה: ${folder.path}" }
            Files.walk(root).use { stream ->
                stream.filter { it.isRegularFile() }
                    .filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                    .sorted()
                    .forEach { file ->
                        digest.add(root.relativize(file).toString().replace('\\', '/'))
                        digest.add(Files.size(file).toString())
                        digest.add(Files.getLastModifiedTime(file).toMillis().toString())
                    }
            }
        }
        return digest.digest().toHex()
    }

    fun build(folders: List<PersonalBookFolder>, generation: String): Pair<PersonalLibraryArtifacts, Map<String, PersonalImportSummary>> {
        val generationDir = generationsDirectory.resolve(generation)
        val database = generationDir.resolve("personal.db")
        val index = generationDir.resolve("personal.lucene")
        if (database.isRegularFile() && Files.isDirectory(index)) {
            return PersonalLibraryArtifacts(generation, database, index) to emptyMap()
        }
        Files.createDirectories(generationDir)
        val stagingDb = generationDir.resolve("personal.db.building")
        Files.deleteIfExists(stagingDb)
        createSchema(stagingDb)
        val summaries = importInto(stagingDb, folders.filter { it.enabled })
        PersonalLuceneIndexBuilder.build(stagingDb, index)
        Files.move(stagingDb, database)
        return PersonalLibraryArtifacts(generation, database, index) to summaries
    }

    private fun createSchema(database: Path) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$database")
        try { SeforimDb.Schema.create(driver) } finally { driver.close() }
    }

    private fun importInto(database: Path, folders: List<PersonalBookFolder>): Map<String, PersonalImportSummary> {
        val base = BaseLibraryIndex.load(baseDatabase)
        val ids = StableNegativeIds()
        DriverManager.getConnection("jdbc:sqlite:$database").use { target ->
            target.createStatement().use {
                it.execute("PRAGMA foreign_keys=OFF")
                it.execute("PRAGMA journal_mode=DELETE")
                it.execute("PRAGMA synchronous=NORMAL")
                it.execute(
                    "CREATE TABLE IF NOT EXISTS personal_link_target_book " +
                        "(bookId INTEGER PRIMARY KEY NOT NULL)",
                )
            }
            target.autoCommit = false
            try {
                val context = ImportContext(target, base, ids)
                val counts = folders.associate { folder -> folder.id to context.importFolder(folder) }.toMutableMap()
                folders.forEach { folder ->
                    val added = context.importLinks(folder)
                    counts[folder.id] = counts.getValue(folder.id).copy(links = added)
                }
                context.finishLinks()
                target.commit()
                return counts
            } catch (error: Throwable) {
                target.rollback()
                throw error
            }
        }
    }

    private inner class ImportContext(
        private val connection: Connection,
        private val base: BaseLibraryIndex,
        private val ids: StableNegativeIds,
    ) {
        private val categoryParents = HashMap<Long, Long?>(base.categoryParents)
        private val categoryLevels = HashMap<Long, Int>(base.categoryLevels)
        private val personalCategories = HashMap<String, Long>()
        private val booksByTitle = HashMap<String, BookRef>(base.booksByTitle)
        private val personalBooksByFolderAndTitle = HashMap<Pair<String, String>, BookRef>()
        private val booksWithSourceLinks = HashSet<Long>()
        private val booksWithTargetLinks = HashSet<Long>()
        private val flagsByBook = HashMap<Long, MutableSet<ConnectionType>>()

        fun importFolder(folder: PersonalBookFolder): PersonalImportSummary {
            val root = Path.of(folder.path)
            val metadata = loadMetadata(root)
            val sourceId = ids.id("source:${folder.id}")
            execute("INSERT INTO source(id,name) VALUES(?,?)", sourceId, "Personal:${folder.id}")
            val folderRoot = when (folder.placement) {
                PersonalFolderPlacement.PERSONAL_BOOKS -> {
                    val personalRoot = ensurePersonalCategory("global:personal", null, "ספרים אישיים", 0)
                    ensurePersonalCategory("folder:${folder.id}", personalRoot, folder.displayName, 1)
                }
                PersonalFolderPlacement.MERGE_WITH_LIBRARY -> null
            }
            val files = Files.walk(root).use { stream ->
                stream.filter { it.isRegularFile() && it.extension.equals("txt", true) }
                    .filter { !it.startsWith(root.resolve("links")) }
                    .filter { !it.nameWithoutExtension.startsWith("הערות על ") }
                    .sorted().toList()
            }
            var bookCount = 0
            files.forEach { file ->
                val relative = root.relativize(file)
                val parentSegments = (0 until relative.nameCount - 1).map { relative.getName(it).toString() }
                val categoryId = resolveBookCategory(folder, folderRoot, parentSegments)
                val rawTitle = file.nameWithoutExtension
                val title = normalizeLabel(rawTitle)
                val bookId = ids.id("book:${folder.id}:${relative.toString().replace('\\', '/')}")
                val meta = metadata[rawTitle] ?: metadata[title]
                val lines = Files.readAllLines(file, Charsets.UTF_8)
                val notes = listOf(title, rawTitle).distinct().asSequence()
                    .map { file.parent.resolve("הערות על $it.txt") }
                    .firstOrNull { it.isRegularFile() }
                    ?.readText(Charsets.UTF_8)
                execute(
                    """INSERT INTO book(id,categoryId,sourceId,title,heRef,heShortDesc,notesContent,orderIndex,totalLines)
                       VALUES(?,?,?,?,?,?,?,?,?)""".trimIndent(),
                    bookId, categoryId, sourceId, title, title, meta?.heShortDesc, notes, meta?.order?.toLong() ?: 999L, lines.size,
                )
                meta?.author?.takeIf { it.isNotBlank() }?.let { author ->
                    val authorId = ids.id("author:$author")
                    execute("INSERT OR IGNORE INTO author(id,name) VALUES(?,?)", authorId, author)
                    execute("INSERT INTO book_author(bookId,authorId) VALUES(?,?)", bookId, authorId)
                }
                meta?.pubPlace?.takeIf { it.isNotBlank() }?.let { place ->
                    val id = ids.id("place:$place")
                    execute("INSERT OR IGNORE INTO pub_place(id,name) VALUES(?,?)", id, place)
                    execute("INSERT INTO book_pub_place(bookId,pubPlaceId) VALUES(?,?)", bookId, id)
                }
                meta?.pubDate?.takeIf { it.isNotBlank() }?.let { date ->
                    val id = ids.id("date:$date")
                    execute("INSERT OR IGNORE INTO pub_date(id,date) VALUES(?,?)", id, date)
                    execute("INSERT INTO book_pub_date(bookId,pubDateId) VALUES(?,?)", bookId, id)
                }
                meta?.extraTitles.orEmpty().filter { it.isNotBlank() }.forEach { term ->
                    execute("INSERT OR IGNORE INTO book_acronym(bookId,term) VALUES(?,?)", bookId, term)
                }
                val lineIds = insertLinesAndToc(bookId, title, lines)
                val ref = BookRef(bookId, title, categoryId, meta?.order?.toInt() ?: 999, lineIds)
                booksByTitle[comparable(title)] = ref
                personalBooksByFolderAndTitle[folder.id to comparable(title)] = ref
                bookCount++
            }
            return PersonalImportSummary(bookCount, 0)
        }

        private fun resolveBookCategory(folder: PersonalBookFolder, folderRoot: Long?, segments: List<String>): Long {
            if (segments.isEmpty()) {
                if (folderRoot != null) return folderRoot
                return ensurePersonalCategory("merge-root:${folder.id}", null, folder.displayName, 0)
            }
            var parent = folderRoot
            var logicalPath = ""
            segments.forEachIndexed { index, raw ->
                val title = normalizeLabel(raw)
                logicalPath = if (logicalPath.isEmpty()) title else "$logicalPath/$title"
                val baseMatch = if (folder.placement == PersonalFolderPlacement.MERGE_WITH_LIBRARY) {
                    base.categoryByParentAndTitle[parent to comparable(title)]
                } else null
                parent = baseMatch ?: ensurePersonalCategory(
                    "category:${folder.id}:$logicalPath", parent, title,
                    (parent?.let { categoryLevels[it] + 1 } ?: index),
                )
            }
            return requireNotNull(parent)
        }

        private fun ensurePersonalCategory(key: String, parent: Long?, title: String, level: Int): Long {
            personalCategories[key]?.let { return it }
            val id = ids.id(key)
            execute("INSERT INTO category(id,parentId,title,level,orderIndex) VALUES(?,?,?,?,999)", id, parent, title, level)
            categoryParents[id] = parent
            categoryLevels[id] = level
            execute("INSERT INTO category_closure(ancestorId,descendantId) VALUES(?,?)", id, id)
            var ancestor = parent
            val seen = HashSet<Long>()
            while (ancestor != null && seen.add(ancestor)) {
                execute("INSERT OR IGNORE INTO category_closure(ancestorId,descendantId) VALUES(?,?)", ancestor, id)
                ancestor = categoryParents[ancestor]
            }
            personalCategories[key] = id
            return id
        }

        private fun insertLinesAndToc(bookId: Long, title: String, lines: List<String>): List<Long> {
            val result = ArrayList<Long>(lines.size)
            val occurrences = HashMap<String, Int>()
            val parentStack = HashMap<Int, Long>()
            var currentToc: Long? = null
            lines.forEachIndexed { index, content ->
                val occurrence = occurrences.merge(content, 1, Int::plus)!! - 1
                val lineId = ids.id("line:$bookId:${sha256(content)}:$occurrence")
                val level = HEADER.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                var tocId: Long? = currentToc
                if (level > 0) {
                    val heading = Jsoup.clean(content, Safelist.none()).trim()
                    if (heading.isNotBlank()) {
                        val textId = ids.id("toc-text:$heading")
                        execute("INSERT OR IGNORE INTO tocText(id,text) VALUES(?,?)", textId, heading)
                        val parent = (level - 1 downTo 1).firstNotNullOfOrNull { parentStack[it] }
                        val entryId = ids.id("toc:$bookId:$level:$heading:$index")
                        execute(
                            "INSERT INTO tocEntry(id,bookId,parentId,textId,level,lineId) VALUES(?,?,?,?,?,?)",
                            entryId, bookId, parent, textId, level, lineId,
                        )
                        parentStack.keys.filter { it >= level }.forEach(parentStack::remove)
                        parentStack[level] = entryId
                        currentToc = entryId
                        tocId = entryId
                    }
                }
                execute(
                    "INSERT INTO line(id,bookId,lineIndex,content,heRef,tocEntryId,charCount) VALUES(?,?,?,?,?,?,?)",
                    lineId, bookId, index, content, "$title ${index + 1}", tocId, visibleLength(content),
                )
                tocId?.let { execute("INSERT INTO line_toc(lineId,tocEntryId) VALUES(?,?)", lineId, it) }
                result += lineId
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """UPDATE tocEntry SET hasChildren=1 WHERE id IN
                       (SELECT DISTINCT parentId FROM tocEntry WHERE bookId=$bookId AND parentId IS NOT NULL)""".trimIndent(),
                )
                statement.executeUpdate(
                    """UPDATE tocEntry SET isLastChild=1 WHERE id IN
                       (SELECT MAX(id) FROM tocEntry WHERE bookId=$bookId GROUP BY parentId)""".trimIndent(),
                )
            }
            return result
        }

        fun importLinks(folder: PersonalBookFolder): Int {
            val linksDirectory = Path.of(folder.path).resolve("links")
            if (!linksDirectory.isDirectory()) return 0
            var count = 0
            Files.list(linksDirectory).use { stream ->
                stream.filter { it.isRegularFile() && it.extension.equals("json", true) }.sorted().forEach { file ->
                    val sourceTitle = comparable(file.nameWithoutExtension.removeSuffix("_links"))
                    val source = personalBooksByFolderAndTitle[folder.id to sourceTitle] ?: booksByTitle[sourceTitle] ?: return@forEach
                    val links = runCatching { json.decodeFromString<List<PersonalLinkData>>(file.readText()) }.getOrDefault(emptyList())
                    links.forEachIndexed { index, data ->
                        val targetTitle = comparable(data.path.substringAfterLast('\\').substringAfterLast('/').substringBeforeLast('.'))
                        val target = booksByTitle[targetTitle] ?: return@forEachIndexed
                        val sourceIndex = (data.sourceLine.toInt() - 1).coerceAtLeast(0)
                        val targetIndex = (data.targetLine.toInt() - 1).coerceAtLeast(0)
                        val sourceLineId = source.lineIds.getOrNull(sourceIndex) ?: return@forEachIndexed
                        val targetLineId = target.lineIds.getOrNull(targetIndex)
                            ?: base.lineId(target.id, targetIndex) ?: return@forEachIndexed
                        var type = ConnectionType.fromString(data.connectionType)
                        if (type == ConnectionType.SOURCE) type = ConnectionType.OTHER
                        val typeId = base.connectionTypes[type.name] ?: return@forEachIndexed
                        val linkId = ids.id("link:${folder.id}:${file.fileName}:$index:$sourceLineId:$targetLineId")
                        execute(
                            """INSERT INTO link(id,sourceBookId,targetBookId,sourceLineId,targetLineId,targetLineIndex,
                               targetBookOrderIndex,connectionTypeId,isDeclaredBase) VALUES(?,?,?,?,?,?,?,?,0)""".trimIndent(),
                            linkId, source.id, target.id, sourceLineId, targetLineId, targetIndex, target.order, typeId,
                        )
                        booksWithSourceLinks += source.id
                        booksWithTargetLinks += target.id
                        flagsByBook.getOrPut(source.id, ::mutableSetOf).add(type)
                        flagsByBook.getOrPut(target.id, ::mutableSetOf).add(type)
                        count++
                    }
                }
            }
            return count
        }

        fun finishLinks() {
            booksWithTargetLinks.filter { it > 0L }.forEach { bookId ->
                execute("INSERT OR IGNORE INTO personal_link_target_book(bookId) VALUES(?)", bookId)
            }
            execute(
                "INSERT INTO schema_meta(key,value) VALUES(?,?) " +
                    "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                TARGET_BOOK_HINTS_KEY,
                "1",
            )
            (booksWithSourceLinks + booksWithTargetLinks).filter { it < 0 }.forEach { bookId ->
                execute(
                    "INSERT INTO book_has_links(bookId,hasSourceLinks,hasTargetLinks) VALUES(?,?,?)",
                    bookId, if (bookId in booksWithSourceLinks) 1 else 0, if (bookId in booksWithTargetLinks) 1 else 0,
                )
            }
            flagsByBook.filterKeys { it < 0 }.forEach { (bookId, flags) ->
                execute(
                    """UPDATE book SET hasTargumConnection=?,hasReferenceConnection=?,hasSourceConnection=?,
                       hasCommentaryConnection=?,hasOtherConnection=? WHERE id=?""".trimIndent(),
                    if (ConnectionType.TARGUM in flags) 1 else 0,
                    if (ConnectionType.REFERENCE in flags) 1 else 0,
                    1,
                    if (flags.any { it == ConnectionType.COMMENTARY || it == ConnectionType.SUPER_COMMENTARY }) 1 else 0,
                    if (flags.any { it !in setOf(ConnectionType.TARGUM, ConnectionType.REFERENCE, ConnectionType.COMMENTARY, ConnectionType.SUPER_COMMENTARY) }) 1 else 0,
                    bookId,
                )
            }
        }

        private fun execute(sql: String, vararg values: Any?) {
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }

    private fun loadMetadata(root: Path): Map<String, BookMetadata> {
        val file = root.resolve("metadata.json")
        if (!file.isRegularFile()) return emptyMap()
        val content = file.readText(Charsets.UTF_8)
        return runCatching { json.decodeFromString<Map<String, BookMetadata>>(content) }.getOrElse {
            runCatching { json.decodeFromString<List<BookMetadata>>(content).associateBy(BookMetadata::title) }.getOrDefault(emptyMap())
        }
    }

    @Serializable
    private data class PersonalLinkData(
        @SerialName("line_index_1") val sourceLine: Double,
        @SerialName("path_2") val path: String,
        @SerialName("line_index_2") val targetLine: Double,
        @SerialName("Conection Type") val connectionType: String = "",
    )

    private data class BookRef(
        val id: Long,
        val title: String,
        val categoryId: Long,
        val order: Int,
        val lineIds: List<Long> = emptyList(),
    )

    private class BaseLibraryIndex private constructor(
        val categoryParents: Map<Long, Long?>,
        val categoryLevels: Map<Long, Int>,
        val categoryByParentAndTitle: Map<Pair<Long?, String>, Long>,
        val booksByTitle: Map<String, BookRef>,
        val connectionTypes: Map<String, Long>,
        private val database: Path,
    ) {
        private val lineIdsByBook = HashMap<Long, List<Long>>()

        @Synchronized
        fun lineId(bookId: Long, lineIndex: Int): Long? = lineIdsByBook.getOrPut(bookId) {
            DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
                connection.prepareStatement("SELECT id FROM line WHERE bookId=? ORDER BY lineIndex").use { statement ->
                    statement.setLong(1, bookId)
                    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
                }
            }
        }.getOrNull(lineIndex)

        companion object {
            fun load(database: Path): BaseLibraryIndex = DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
                val parents = HashMap<Long, Long?>(); val levels = HashMap<Long, Int>(); val categories = HashMap<Pair<Long?, String>, Long>()
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id,parentId,title,level FROM category").use { rows ->
                        while (rows.next()) {
                            val id = rows.getLong(1); val parent = rows.getLong(2).let { if (rows.wasNull()) null else it }
                            parents[id] = parent; levels[id] = rows.getInt(4); categories[parent to comparable(rows.getString(3))] = id
                        }
                    }
                }
                val books = HashMap<String, BookRef>()
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id,title,categoryId,orderIndex FROM book ORDER BY sourceId").use { rows ->
                        while (rows.next()) {
                            val ref = BookRef(rows.getLong(1), rows.getString(2), rows.getLong(3), rows.getInt(4))
                            books.putIfAbsent(comparable(ref.title), ref)
                        }
                    }
                }
                val types = HashMap<String, Long>()
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id,name FROM connection_type").use { rows ->
                        while (rows.next()) types[rows.getString(2)] = rows.getLong(1)
                    }
                }
                BaseLibraryIndex(parents, levels, categories, books, types, database)
            }
        }
    }

    private class StableNegativeIds {
        private val keysById = HashMap<Long, String>()
        fun id(key: String): Long {
            var salt = 0
            while (true) {
                val bytes = MessageDigest.getInstance("SHA-256").digest("$key#$salt".toByteArray())
                // Reserve -1 for legacy UI/session sentinels.
                val positive = (ByteBuffer.wrap(bytes).int.toLong() and 0x7fff_ffffL).coerceAtLeast(2L)
                val candidate = -positive
                val previous = keysById.putIfAbsent(candidate, key)
                if (previous == null || previous == key) return candidate
                salt++
            }
        }
    }

    private fun MessageDigest.add(value: String) = update(value.toByteArray(Charsets.UTF_8))
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        private const val TARGET_BOOK_HINTS_KEY = "personal_target_book_hints_v1"
        val SUPPORTED_EXTENSIONS = setOf("txt", "json")
        val HEADER = Regex("<h([1-6])(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        fun normalizeLabel(value: String): String = value.trim()
            .replace('"', '״').replace('׳', '’').replace(Regex("\\s+"), " ")
        fun comparable(value: String): String = normalizeLabel(value).replace("״", "").replace("’", "").lowercase()
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()
        fun visibleLength(value: String): Int = Jsoup.clean(value, Safelist.none()).length
    }
}
