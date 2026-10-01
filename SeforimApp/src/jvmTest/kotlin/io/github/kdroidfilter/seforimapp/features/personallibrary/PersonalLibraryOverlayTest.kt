package io.github.kdroidfilter.seforimapp.features.personallibrary

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalLibraryOverlayTest {
    @Test
    fun attachmentWaitsForActiveQueriesAndCanReplaceCachedSchema() {
        val temp = Files.createTempDirectory("personal-overlay")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val base = temp.resolve("base.db")
            val personal = temp.resolve("personal.db")
            JdbcSqliteDriver("jdbc:sqlite:$base").use(SeforimDb.Schema::create)
            JdbcSqliteDriver("jdbc:sqlite:$personal").use(SeforimDb.Schema::create)
            PersistentSqliteDriver("jdbc:sqlite:$base").use { driver ->
                val overlay = PersonalLibraryOverlay(driver)
                val started = CountDownLatch(1)
                val attached = CountDownLatch(1)
                val future =
                    synchronized(driver.getConnection()) {
                        val result =
                            executor.submit {
                                started.countDown()
                                overlay.attach(personal)
                                attached.countDown()
                            }
                        assertTrue(started.await(5, TimeUnit.SECONDS))
                        // The connection lock held here must prevent attachment from completing.
                        assertTrue(!attached.await(100, TimeUnit.MILLISECONDS))
                        result
                    }
                future.get(10, TimeUnit.SECONDS)

                fun countBooks(): Long? =
                    driver
                        .executeQuery(
                            1,
                            "SELECT COUNT(*) FROM book",
                            { cursor ->
                                cursor.next()
                                QueryResult.Value(cursor.getLong(0))
                            },
                            0,
                            null,
                        ).value
                assertEquals(0L, countBooks())
                overlay.attach(personal)
                assertEquals(0L, countBooks())
                overlay.attach(null)
                assertEquals(0L, countBooks())
            }
        } finally {
            executor.shutdownNow()
            temp.toFile().deleteRecursively()
        }
    }
}
