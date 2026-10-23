package io.github.kdroidfilter.seforimapp.core.catalog

import io.github.kdroidfilter.seforimapp.catalog.CatalogPresets
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogAccessDisplayTest {
    @Test
    fun prefixesKeepTheirExistingPunctuationAndAncestorRules() {
        val label = "הלכה.[א]"
        val separators = listOf(",", " , ", ": ", " – ", "—", "-", "+", " ", "\t")
        val titles = separators.map { "$label${it}דינים" } + listOf("$label ספרים דינים", label, "${label}א", "אחר")
        val catalog = catalog(label, titles)
        val access = CatalogAccess { catalog }
        assertEquals(
            List(separators.size + 1) { "דינים" } + listOf(label, "${label}א", "אחר"),
            access.booksFor(LEAF_ID).map { it.title },
        )
        assertEquals(titles.first(), access.bookTitle(1))
    }

    @Test
    fun exclusionsStillApplyAfterPrefixesAreRemoved() {
        val shulchanAruch = CatalogPresets.Ids.Categories.SHULCHAN_ARUCH
        val category =
            CatalogCategory(
                id = shulchanAruch,
                title = "שולחן ערוך",
                level = 0,
                books =
                    listOf("שולחן ערוך, הקדמה", "שולחן ערוך: פרי מגדים", "שולחן ערוך – מחבר").mapIndexed { index, title ->
                        CatalogBook(id = index.toLong() + 1, title = title, categoryId = shulchanAruch)
                    },
                subcategories = emptyList(),
            )
        val access = CatalogAccess { PrecomputedCatalog(rootCategories = listOf(category)) }
        assertEquals(listOf("מחבר"), access.booksFor(shulchanAruch).map { it.title })
    }

    @Test
    fun catalogReplacementRebuildsTheDisplayIndices() {
        var current = catalog("דינים", listOf("דינים, ראשון"))
        val access = CatalogAccess { current }
        access.warm()
        assertEquals("ראשון", access.booksFor(LEAF_ID).single().title)
        current = catalog("דינים", listOf("דינים, שני"))
        assertEquals("שני", access.booksFor(LEAF_ID).single().title)
    }

    @Test
    fun concurrentWarmupsBuildTheCatalogOnlyOnce() {
        val source = catalog("דינים", listOf("דינים, ראשון"))
        val visits = AtomicInteger()
        val roots =
            object : AbstractList<CatalogCategory>() {
                override val size = source.rootCategories.size

                override fun get(index: Int): CatalogCategory {
                    visits.incrementAndGet()
                    return source.rootCategories[index]
                }
            }
        val shared = source.copy(rootCategories = roots)
        val access = CatalogAccess { shared }
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val tasks =
                List(4) {
                    executor.submit(
                        Callable {
                            check(start.await(5, TimeUnit.SECONDS))
                            access.warm()
                        },
                    )
                }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(2, visits.get()) // One title traversal and one book traversal.
            assertEquals("ראשון", access.booksFor(LEAF_ID).single().title)
            assertEquals(2, visits.get())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun catalog(
        label: String,
        titles: List<String>,
    ): PrecomputedCatalog {
        val leaf =
            CatalogCategory(
                id = LEAF_ID,
                title = label,
                level = 1,
                parentId = ROOT_ID,
                books = titles.mapIndexed { index, title -> CatalogBook(index.toLong() + 1, title, LEAF_ID) },
                subcategories = emptyList(),
            )
        val root =
            CatalogCategory(
                id = ROOT_ID,
                title = "ספרים",
                level = 0,
                books = emptyList(),
                subcategories = listOf(leaf),
            )
        return PrecomputedCatalog(rootCategories = listOf(root))
    }

    private companion object {
        const val ROOT_ID = 900_001L
        const val LEAF_ID = 900_002L
    }
}
