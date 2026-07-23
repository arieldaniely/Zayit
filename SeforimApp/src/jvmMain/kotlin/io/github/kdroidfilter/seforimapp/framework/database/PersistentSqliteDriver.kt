package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import io.github.kdroidfilter.seforimlibrary.dao.repository.LinkPartitionQueryDriver
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * SQLite JDBC driver backed by a single persistent connection with a per-identifier
 * [PreparedStatement] cache.
 *
 * Profiling (JFR 2026-04-23) showed the stock [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver]
 * routing every non-transactional query through `ThreadedConnectionManager` — which
 * closes and re-opens the SQLite connection after each call, paying `sqlite3_open`
 * + `sqlite3_prepare` + `sqlite3_close` per query. On a read-only reader workload
 * that is pure waste: the reader corpus is immutable, so one connection suffices
 * and every repeatedly-issued query benefits from a cached prepared statement.
 *
 * Design:
 *  - A single [Connection] opened at construction time, kept alive until [close].
 *  - `getConnection()` returns that connection; `closeConnection()` is a no-op.
 *  - Read-tuning PRAGMAs applied once at init (WAL / NORMAL sync / page+mmap caches).
 *  - Prepared-statement cache keyed by SqlDelight's `identifier: Int` — the same
 *    integer it passes on every generated query call. Statements are synchronized
 *    on the single connection because SQLite's JDBC connection is not thread-safe.
 *
 * Transactions are handled by [JdbcDriver]'s base `autoCommit` machinery; we just
 * ensure our cached prepared statements aren't handed out while another thread holds
 * the connection by synchronizing the execute methods on `connection`.
 */
class PersistentSqliteDriver(
    url: String,
    properties: Properties = Properties(),
) : JdbcDriver(), LinkPartitionQueryDriver {
    private val connection: Connection = DriverManager.getConnection(url, properties)

    // Statement cache keyed by SQL text — SqlDelight sometimes reuses the same
    // `identifier` for queries whose SQL varies (e.g. `IN (?,?,?)` with variable
    // arity), so we avoid crashes by keying on the actual SQL string. Bounded in
    // practice: a few hundred distinct queries across the whole app. Never evicted.
    private val statementCache = HashMap<String, PreparedStatement>()

    @Volatile
    private var personalOverlayAttached = false
    @Volatile
    private var personalTargetBookIds: Set<Long> = emptySet()
    private val forcedLinkSchema = ThreadLocal<String?>()

    init {
        connection.autoCommit = true
        // Don't apply PRAGMAs here: `SeforimRepository.init` already issues its own
        // tuned set (256 MB cache / 512 MB mmap). Running them twice while the repo
        // still holds open cursors from its schema-create pass triggers SQLITE_BUSY.
    }

    override fun getConnection(): Connection = connection

    /** Enables direct partition routing while the personal-library database is attached. */
    fun setPersonalOverlayAttached(
        attached: Boolean,
        targetBookIds: Set<Long> = emptySet(),
    ) {
        if (!attached) personalOverlayAttached = false
        personalTargetBookIds = if (attached) targetBookIds else emptySet()
        if (attached) personalOverlayAttached = true
    }

    override fun <T> queryEachLinkPartition(query: () -> T): List<T> {
        if (!personalOverlayAttached) return listOf(query())
        return listOf(
            withForcedLinkSchema("main", query),
            withForcedLinkSchema("personal", query),
        )
    }

    override fun <T> queryEachLinkPartitionForTargetLines(
        targetLineIds: Collection<Long>,
        query: () -> T,
    ): List<T> {
        if (!personalOverlayAttached) return listOf(query())
        if (targetLineIds.isEmpty()) return emptyList()

        val results = ArrayList<T>(2)
        // Main entities always have positive IDs; the immutable main database can
        // never contain a link targeting a later personal (negative) line.
        if (targetLineIds.any { it > 0L }) {
            results += withForcedLinkSchema("main", query)
        }
        // Negative lines belong to the personal DB. Positive lines use a small,
        // indexed targetLineId probe only after book-level hints allowed this path.
        if (targetLineIds.any { it < 0L } || personalContainsAnyTargetLine(targetLineIds)) {
            results += withForcedLinkSchema("personal", query)
        }
        return results
    }

    private fun personalContainsAnyTargetLine(targetLineIds: Collection<Long>): Boolean {
        if (personalTargetBookIds.isEmpty()) return false
        val mainLineIds = targetLineIds.filter { it > 0L }
        if (mainLineIds.isEmpty()) return false
        synchronized(connection) {
            val placeholders = List(mainLineIds.size) { "?" }.joinToString(",")
            val statement = prepare(
                "SELECT 1 FROM personal.link WHERE targetLineId IN ($placeholders) LIMIT 1",
            )
            statement.clearParameters()
            mainLineIds.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
            statement.executeQuery().use { rows -> return rows.next() }
        }
    }

    override fun hasAdditionalLinksTargetingBook(bookId: Long): Boolean =
        personalOverlayAttached && bookId in personalTargetBookIds

    private fun <T> withForcedLinkSchema(
        schema: String,
        query: () -> T,
    ): T {
        val previous = forcedLinkSchema.get()
        forcedLinkSchema.set(schema)
        return try {
            query()
        } finally {
            if (previous == null) forcedLinkSchema.remove() else forcedLinkSchema.set(previous)
        }
    }

    override fun closeConnection(connection: Connection) = Unit

    override fun close() {
        synchronized(connection) {
            statementCache.values.forEach { runCatching { it.close() } }
            statementCache.clear()
            connection.close()
        }
    }

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        synchronized(connection) {
            val stmt = prepare(sql)
            stmt.clearParameters()
            if (binders != null) JdbcPreparedStatement(stmt).binders()
            val hasResultSet = stmt.execute()
            val rows =
                if (hasResultSet) {
                    // Drain any result set to release the statement's cursor so the next
                    // call (e.g. a subsequent PRAGMA) doesn't hit SQLITE_BUSY.
                    stmt.resultSet?.close()
                    0L
                } else {
                    stmt.updateCount.toLong()
                }
            return QueryResult.Value(rows)
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        synchronized(connection) {
            val forcedSchema = forcedLinkSchema.get()
            val effectiveSql =
                when {
                    !personalOverlayAttached || binders == null -> sql
                    forcedSchema != null && PersonalLibraryQueryRouter.isLinkQuery(sql) ->
                        PersonalLibraryQueryRouter.routeLinkQueryToSchema(sql, forcedSchema)
                    PersonalLibraryQueryRouter.isLinkQuery(sql) -> {
                        val bindings = LinkBindingProbe().also { probe -> binders(probe) }.bindings
                        PersonalLibraryQueryRouter.route(sql, bindings, attached = true)
                    }
                    else ->
                        PersonalLibraryQueryRouter.routeEntityQuery(
                            sql,
                            captureFirstBinding(binders),
                            attached = true,
                        )
                }
            val stmt = prepare(effectiveSql)
            stmt.clearParameters()
            if (binders != null) JdbcPreparedStatement(stmt).binders()
            // Inlined from `JdbcPreparedStatement.executeQuery`, minus the `preparedStatement.close()`
            // in its `finally` — closing would defeat the whole point of caching. We close the
            // ResultSet via `.use { }` instead so SQLite frees the cursor for the next call.
            stmt.executeQuery().use { rs ->
                return mapper(JdbcCursor(rs))
            }
        }
    }

    /**
     * Returns a cached [PreparedStatement] for [sql], preparing it lazily on first
     * use. Caller must hold the connection's monitor.
     */
    private fun prepare(sql: String): PreparedStatement {
        statementCache[sql]?.let { cached ->
            if (!cached.isClosed) return cached
            statementCache.remove(sql)
        }
        val fresh = connection.prepareStatement(sql)
        statementCache[sql] = fresh
        return fresh
    }
}

/** Stops the generated binder immediately after parameter zero instead of walking large IN lists twice. */
internal fun captureFirstBinding(bind: SqlPreparedStatement.() -> Unit): Any? {
    val probe = FirstBindingProbe()
    try {
        bind(probe)
    } catch (_: FirstBindingCaptured) {
        // Parameter zero was captured; the remaining generated bind calls are irrelevant.
    }
    return probe.firstBinding
}

private object FirstBindingCaptured : Throwable(null, null, false, false)

private class FirstBindingProbe : SqlPreparedStatement {
    var firstBinding: Any? = null
        private set

    private fun record(index: Int, value: Any?) {
        if (index == 0) {
            firstBinding = value
            throw FirstBindingCaptured
        }
    }

    override fun bindBytes(index: Int, bytes: ByteArray?) = record(index, bytes)
    override fun bindLong(index: Int, long: Long?) = record(index, long)
    override fun bindDouble(index: Int, double: Double?) = record(index, double)
    override fun bindString(index: Int, string: String?) = record(index, string)
    override fun bindBoolean(index: Int, boolean: Boolean?) = record(index, boolean)
}

/** Link routing needs numeric selector parameters only; never retain text or binary query payloads. */
private class LinkBindingProbe : SqlPreparedStatement {
    private val mutableBindings = HashMap<Int, Long>()
    val bindings: Map<Int, Long> get() = mutableBindings

    override fun bindLong(index: Int, long: Long?) {
        if (long != null) mutableBindings[index] = long
    }

    override fun bindBytes(index: Int, bytes: ByteArray?) = Unit
    override fun bindDouble(index: Int, double: Double?) = Unit
    override fun bindString(index: Int, string: String?) = Unit
    override fun bindBoolean(index: Int, boolean: Boolean?) = Unit
}

/**
 * Bypasses UNION ALL views for queries whose first parameter is a stable entity id.
 * Base ids are positive and personal ids are negative, so one database owns the entity.
 * Link queries remain merged because imported links may connect both databases.
 */
internal object PersonalLibraryQueryRouter {
    private val idSelector =
        Regex(
            """(?is)\b(?:id|bookId|lineId|tocEntryId|structureId|altTocEntryId)\s*(?:=|IN\s*\()\s*$""",
        )
    private val linkQuery = Regex("""(?i)\b(?:FROM|JOIN)\s+\"?link\"?\b""")
    private val whitespace = Regex("""\s+""")
    private val linkSelectorPatterns =
        listOf("sourceLineId", "sourceBookId", "targetBookId", "id").associateWith { field ->
            Regex(
                """(?is)\bl\.${Regex.escape(field)}\s*(?:=\s*\?|IN\s*\(\s*(?:\?\s*,?\s*)+\))""",
            )
        }
    private val sourceLineJoin =
        Regex("""(?i)\bl\.sourceLineId\s*=\s*sl\.id\b|\bsl\.id\s*=\s*l\.sourceLineId\b""")
    private val sourceBookJoin =
        Regex("""(?i)\bl\.sourceBookId\s*=\s*b\.id\b|\bb\.id\s*=\s*l\.sourceBookId\b""")
    private val targetBookJoin =
        Regex("""(?i)\bl\.targetBookId\s*=\s*b\.id\b|\bb\.id\s*=\s*l\.targetBookId\b""")
    private val targetLineJoin =
        Regex("""(?i)\bl\.targetLineId\s*=\s*tl\.id\b|\btl\.id\s*=\s*l\.targetLineId\b""")
    private val linkAliasPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?link\"?\s+l\b""")
    private val bareLinkPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?link\"?\b""")
    private val connectionTypeAliasPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?connection_type\"?\s+ct\b""")
    private val sourceLineAliasPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?line\"?\s+sl\b""")
    private val targetLineAliasPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?line\"?\s+tl\b""")
    private val bookAliasPattern = Regex("""(?i)\b(FROM|JOIN)\s+\"?book\"?\s+b\b""")
    private val linkQueryPresence = ConcurrentHashMap<String, Boolean>()
    private val mainEntityRoutes = ConcurrentHashMap<String, String>()
    private val personalEntityRoutes = ConcurrentHashMap<String, String>()
    private val overlayTables =
        listOf(
            "category",
            "category_closure",
            "author",
            "topic",
            "pub_place",
            "pub_date",
            "source",
            "book",
            "book_pub_place",
            "book_pub_date",
            "book_topic",
            "book_author",
            "line",
            "tocText",
            "tocEntry",
            "connection_type",
            "book_has_links",
            "line_toc",
            "alt_toc_structure",
            "alt_toc_entry",
            "line_alt_toc",
            "book_acronym",
            "default_commentator",
            "default_targum",
        )
    private val tablePatterns =
        overlayTables.associateWith { table ->
            Regex("""(?i)\b(FROM|JOIN)\s+\"?${Regex.escape(table)}\"?\b""")
        }

    fun route(sql: String, firstBinding: Any?, attached: Boolean): String {
        if (!attached || isLinkQuery(sql)) return sql
        return routeEntityQuery(sql, firstBinding, attached = true)
    }

    fun isLinkQuery(sql: String): Boolean =
        linkQueryPresence.computeIfAbsent(sql) { candidate -> linkQuery.containsMatchIn(candidate) }

    /** Used by partitioned inverse-link reads; source-side JOINs live with their link row. */
    fun routeLinkQueryToSchema(sql: String, schema: String): String =
        routeLinkQuery(sql, emptyMap(), forcedLinkSchema = schema)

    fun route(sql: String, bindings: Map<Int, Any?>, attached: Boolean): String {
        if (!attached) return sql
        if (isLinkQuery(sql)) return routeLinkQuery(sql, bindings)

        return routeEntityQuery(sql, bindings[0], attached = true)
    }

    fun routeEntityQuery(sql: String, firstBinding: Any?, attached: Boolean): String {
        if (!attached || firstBinding !is Long) return sql
        val schema = if (firstBinding < 0) "personal" else "main"
        val cache = if (schema == "personal") personalEntityRoutes else mainEntityRoutes
        return cache.computeIfAbsent(sql) {
            val selectorPrefix = sql.substringBefore('?', missingDelimiterValue = "")
            if (selectorPrefix.isEmpty() || !idSelector.containsMatchIn(selectorPrefix)) {
                return@computeIfAbsent sql
            }
            tablePatterns.entries.fold(sql) { routed, (table, pattern) ->
                pattern.replace(routed) { match ->
                    "${match.groupValues[1]} $schema.\"$table\""
                }
            }
        }
    }

    /**
     * Link rows belong to the database of their source book/line. Route that table directly,
     * then route JOINs when their owning IDs are also known. This preserves cross-library links
     * while avoiding the combinatorial UNION-view query plan on commentary hot paths.
     */
    private fun routeLinkQuery(
        sql: String,
        bindings: Map<Int, Any?>,
        forcedLinkSchema: String? = null,
    ): String {
        val sourceLineSchema = schemaFor(selectorValues(sql, "sourceLineId", bindings))
        val sourceBookSchema = schemaFor(selectorValues(sql, "sourceBookId", bindings))
        val filteredTargetSchema = schemaFor(selectorValues(sql, "targetBookId", bindings))
        val linkIdSchema = schemaFor(selectorValues(sql, "id", bindings))
        val linkSchema =
            forcedLinkSchema ?: if (filteredTargetSchema == "personal") {
                // The immutable base DB can never point at a later personal entity.
                "personal"
            } else {
                sourceLineSchema ?: sourceBookSchema ?: linkIdSchema ?: return sql
            }

        var routed = qualifyTableAlias(sql, "link", "l", linkSchema)
        routed =
            bareLinkPattern.replace(routed) { match ->
                match.groupValues[1] + " " + linkSchema + ".\"link\""
            }
        // Personal links deliberately reuse the immutable base connection-type IDs.
        routed = qualifyTableAlias(routed, "connection_type", "ct", "main")

        val normalized = sql.replace(whitespace, " ")
        if (sourceLineJoin.containsMatchIn(normalized)) {
            routed = qualifyTableAlias(routed, "line", "sl", linkSchema)
        }
        if (sourceBookJoin.containsMatchIn(normalized)) {
            routed = qualifyTableAlias(routed, "book", "b", linkSchema)
        }

        val targetSchema =
            if (linkSchema == "main") {
                // The immutable base database cannot contain references to later personal IDs.
                "main"
            } else {
                filteredTargetSchema
            }
        if (targetSchema != null) {
            if (targetBookJoin.containsMatchIn(normalized)) {
                routed = qualifyTableAlias(routed, "book", "b", targetSchema)
            }
            if (targetLineJoin.containsMatchIn(normalized)) {
                routed = qualifyTableAlias(routed, "line", "tl", targetSchema)
            }
        }
        return routed
    }

    private fun selectorValues(
        sql: String,
        field: String,
        bindings: Map<Int, Any?>,
    ): List<Long> {
        val selector = linkSelectorPatterns.getValue(field).find(sql) ?: return emptyList()
        val firstIndex = sql.substring(0, selector.range.first).count { it == '?' }
        val count = selector.value.count { it == '?' }
        return (firstIndex until firstIndex + count).mapNotNull { bindings[it] as? Long }
    }

    private fun schemaFor(values: List<Long>): String? {
        if (values.isEmpty() || values.any { it == 0L }) return null
        return when {
            values.all { it < 0L } -> "personal"
            values.all { it > 0L } -> "main"
            else -> null
        }
    }

    private fun qualifyTableAlias(
        sql: String,
        table: String,
        alias: String,
        schema: String,
    ): String {
        val pattern =
            when {
                table == "link" && alias == "l" -> linkAliasPattern
                table == "connection_type" && alias == "ct" -> connectionTypeAliasPattern
                table == "line" && alias == "sl" -> sourceLineAliasPattern
                table == "line" && alias == "tl" -> targetLineAliasPattern
                table == "book" && alias == "b" -> bookAliasPattern
                else -> error("Unsupported routed alias: $table $alias")
            }
        return pattern.replace(sql) { match ->
            "${match.groupValues[1]} $schema.\"$table\" $alias"
        }
    }
}
