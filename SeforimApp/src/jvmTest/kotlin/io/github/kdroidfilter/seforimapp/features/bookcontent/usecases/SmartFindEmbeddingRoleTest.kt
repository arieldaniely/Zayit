package io.github.kdroidfilter.seforimapp.features.bookcontent.usecases

import io.github.kdroidfilter.seforimlibrary.search.DenseHit
import io.github.kdroidfilter.seforimlibrary.search.HybridSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SeforimEmbedder
import io.github.kdroidfilter.seforimlibrary.search.VectorSearcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SmartFindEmbeddingRoleTest {
    @Test
    fun sourceSearchAndHighlightsUsePassageRoleWhileGeneralHighlightsUseQueryRole() =
        runTest {
            val query = "ברכת פירות האילן"
            val related = "מברכים על תפוחים בורא פרי העץ"
            val unrelated = "דיני טומאה וטהרה של כלי מתכת"
            val embedder = mockk<SeforimEmbedder>()
            val vectors = mockk<VectorSearcher>()
            val lexical = mockk<LuceneSearchEngine>(relaxed = true)
            every { embedder.embed(query, SeforimEmbedder.Role.PASSAGE) } returns floatArrayOf(1f, 0f)
            every { embedder.embed(query, SeforimEmbedder.Role.QUERY) } returns floatArrayOf(0f, 1f)
            every { embedder.embed("$related.", SeforimEmbedder.Role.PASSAGE) } returns floatArrayOf(1f, 0f)
            every { embedder.embed("$unrelated.", SeforimEmbedder.Role.PASSAGE) } returns floatArrayOf(0f, 1f)
            every { vectors.search(any(), any(), false, listOf(7L)) } returns listOf(DenseHit(11, 7, 1f))
            every { lexical.openSession(any(), any(), any(), any(), any(), any(), any(), any()) } returns null
            val engine = HybridSearchEngine(lexical, null, Path.of("unused"), resolveLine = { _, _, _ -> null })
            // Publish a ready, mocked dense backend without loading the native model or files.
            for ((name, value) in listOf("embedder" to embedder, "vectorSearcher" to vectors, "denseTried" to true)) {
                HybridSearchEngine::class.java
                    .getDeclaredField(name)
                    .apply { isAccessible = true }
                    .set(engine, value)
            }
            assertEquals(listOf(11L), engine.semanticFind(query, 7, 150))
            assertEquals(listOf("$related."), engine.passagesInBookByMeaning(query, listOf("$related. $unrelated.")))
            verify(exactly = 2) { embedder.embed(query, SeforimEmbedder.Role.PASSAGE) }
            verify(exactly = 0) { embedder.embed(query, SeforimEmbedder.Role.QUERY) }
            assertEquals(listOf("$unrelated."), engine.passagesByMeaning(query, listOf("$related. $unrelated.")))
            verify(exactly = 1) { embedder.embed(query, SeforimEmbedder.Role.QUERY) }
        }
}
