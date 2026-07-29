package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalLibraryCatalogMergerTest {
    @Test
    fun preservesCanonicalBaseCategoryOrderWhenAddingPersonalCategories() {
        val database = Files.createTempFile("personal-catalog", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE category(" +
                            "id INTEGER PRIMARY KEY,parentId INTEGER,title TEXT,level INTEGER,orderIndex REAL)",
                    )
                    statement.execute(
                        "CREATE TABLE book(" +
                            "id INTEGER PRIMARY KEY,title TEXT,categoryId INTEGER,orderIndex REAL,totalLines INTEGER," +
                            "isBaseBook INTEGER,hasTargumConnection INTEGER,hasReferenceConnection INTEGER," +
                            "hasSourceConnection INTEGER,hasCommentaryConnection INTEGER,hasOtherConnection INTEGER," +
                            "hasAltStructures INTEGER)",
                    )
                    statement.execute("CREATE TABLE book_author(bookId INTEGER,authorId INTEGER)")
                    statement.execute("CREATE TABLE author(id INTEGER PRIMARY KEY,name TEXT)")
                    statement.execute(
                        "INSERT INTO category(id,parentId,title,level,orderIndex) " +
                            "VALUES(-1,1,'A personal category',1,0)",
                    )
                }
            }

            val base =
                PrecomputedCatalog(
                    rootCategories =
                        listOf(
                            category(
                                id = 1,
                                title = "Root",
                                children =
                                    listOf(
                                        category(id = 2, title = "Zulu"),
                                        category(id = 3, title = "Alpha"),
                                    ),
                            ),
                        ),
                    totalCategories = 3,
                )

            val merged = PersonalLibraryCatalogMerger.merge(base, database)

            assertEquals(
                listOf(2L, 3L, -1L),
                merged.rootCategories.single().subcategories.map(CatalogCategory::id),
            )
        } finally {
            Files.deleteIfExists(database)
        }
    }

    private fun category(
        id: Long,
        title: String,
        children: List<CatalogCategory> = emptyList(),
    ) =
        CatalogCategory(
            id = id,
            title = title,
            level = if (id == 1L) 0 else 1,
            parentId = if (id == 1L) null else 1,
            books = emptyList(),
            subcategories = children,
        )
}
