package io.github.kdroidfilter.seforimapp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RealDatabaseVerificationTest {

    @Test
    fun verifySourcesAndMentionsOnRealDatabaseIfPresent() = runBlocking {
        val dbFile = File("""C:\Users\kobi\AppData\Roaming\io.github.kdroidfilter.seforimapp\databases\seforim.db""")
        if (!dbFile.exists()) {
            println("Skipping RealDatabaseVerificationTest because database file is not at default path.")
            return@runBlocking
        }

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        val repository = SeforimRepository(dbFile.absolutePath, driver)

        // Find a Tanakh book ID (e.g. Genesis / בראשית)
        val books = repository.searchBooks("בראשית", limit = 10)
        val genesis = books.firstOrNull { it.title.trim() == "בראשית" } ?: books.firstOrNull()
        if (genesis != null) {
            val lines = repository.getLinesForBook(genesis.id, offset = 0, limit = 10)
            if (lines.isNotEmpty()) {
                val lineId = lines.first().id
                val sources = repository.getSourceSummariesForLines(listOf(lineId))
                val mentions = repository.getMentionSummariesForLines(listOf(lineId))

                println("Genesis Line 1 Sources count: ${sources.size}")
                println("Genesis Line 1 Mentions count: ${mentions.size}")

                // No item should exist in BOTH sources and mentions
                val sourceTargetBookIds = sources.map { it.link.targetBookId }.toSet()
                val mentionTargetBookIds = mentions.map { it.link.targetBookId }.toSet()
                val overlap = sourceTargetBookIds.intersect(mentionTargetBookIds)

                assertTrue(
                    overlap.isEmpty(),
                    "Overlap found between Sources and Mentions on Genesis line $lineId: $overlap"
                )
            }
        }

        // Find a Talmud book ID (e.g. Berakhot / ברכות)
        val berakhotBooks = repository.searchBooks("ברכות", limit = 10)
        val berakhot = berakhotBooks.firstOrNull { it.title.contains("בבלי") || it.title.contains("ברכות") }
        if (berakhot != null) {
            val lines = repository.getLinesForBook(berakhot.id, offset = 0, limit = 20)
            if (lines.isNotEmpty()) {
                val lineId = lines.first().id
                val sources = repository.getSourceSummariesForLines(listOf(lineId))
                val mentions = repository.getMentionSummariesForLines(listOf(lineId))

                println("Berakhot Line 1 Sources count: ${sources.size}")
                println("Berakhot Line 1 Mentions count: ${mentions.size}")

                val sourceTargetBookIds = sources.map { it.link.targetBookId }.toSet()
                val mentionTargetBookIds = mentions.map { it.link.targetBookId }.toSet()
                val overlap = sourceTargetBookIds.intersect(mentionTargetBookIds)

                assertTrue(
                    overlap.isEmpty(),
                    "Overlap found between Sources and Mentions on Berakhot line $lineId: $overlap"
                )
            }
        }
    }
}
