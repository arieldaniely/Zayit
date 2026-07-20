package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

object PersonalLibraryCatalogMerger {
    fun merge(base: PrecomputedCatalog): PrecomputedCatalog {
        val database = PersonalLibraryRuntime.activeDatabase ?: return base
        if (!Files.isRegularFile(database)) return base
        return runCatching { merge(base, database) }.getOrDefault(base)
    }

    private fun merge(base: PrecomputedCatalog, database: Path): PrecomputedCatalog {
        data class CategoryRow(val id: Long, val parentId: Long?, val title: String, val level: Int)
        val categories = ArrayList<CategoryRow>()
        val books = ArrayList<CatalogBook>()
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id,parentId,title,level FROM category ORDER BY orderIndex,title").use { rows ->
                    while (rows.next()) {
                        val parent = rows.getLong(2).let { if (rows.wasNull()) null else it }
                        categories += CategoryRow(rows.getLong(1), parent, rows.getString(3), rows.getInt(4))
                    }
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """SELECT b.id,b.title,b.categoryId,b.orderIndex,b.totalLines,b.isBaseBook,
                       b.hasTargumConnection,b.hasReferenceConnection,b.hasSourceConnection,
                       b.hasCommentaryConnection,b.hasOtherConnection,b.hasAltStructures,
                       GROUP_CONCAT(a.name,'|')
                       FROM book b LEFT JOIN book_author ba ON ba.bookId=b.id LEFT JOIN author a ON a.id=ba.authorId
                       GROUP BY b.id ORDER BY b.orderIndex,b.title""".trimIndent(),
                ).use { rows ->
                    while (rows.next()) {
                        books += CatalogBook(
                            id = rows.getLong(1), title = rows.getString(2), categoryId = rows.getLong(3),
                            order = rows.getFloat(4), totalLines = rows.getInt(5), isBaseBook = rows.getInt(6) != 0,
                            hasTargumConnection = rows.getInt(7) != 0, hasReferenceConnection = rows.getInt(8) != 0,
                            hasSourceConnection = rows.getInt(9) != 0, hasCommentaryConnection = rows.getInt(10) != 0,
                            hasOtherConnection = rows.getInt(11) != 0, hasAltStructures = rows.getInt(12) != 0,
                            authors = rows.getString(13)?.split('|')?.filter(String::isNotBlank).orEmpty(),
                        )
                    }
                }
            }
        }
        val children = categories.groupBy(CategoryRow::parentId)
        val booksByCategory = books.groupBy(CatalogBook::categoryId)
        fun personalNode(row: CategoryRow): CatalogCategory = CatalogCategory(
            id = row.id, title = row.title, level = row.level, parentId = row.parentId,
            books = booksByCategory[row.id].orEmpty(),
            subcategories = children[row.id].orEmpty().map(::personalNode),
        )
        fun augment(node: CatalogCategory): CatalogCategory = node.copy(
            books = (node.books + booksByCategory[node.id].orEmpty()).sortedBy(CatalogBook::order),
            subcategories = (
                node.subcategories.map(::augment) + children[node.id].orEmpty().map(::personalNode)
            ).sortedBy(CatalogCategory::title),
        )
        val roots = base.rootCategories.map(::augment) + children[null].orEmpty().map(::personalNode)
        return base.copy(
            rootCategories = roots,
            totalBooks = base.totalBooks + books.size,
            totalCategories = base.totalCategories + categories.size,
        )
    }
}
