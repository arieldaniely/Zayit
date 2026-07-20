package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.CatalogCache
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.search.RepositorySnippetSourceProvider
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.CompositeSearchEngine
import io.github.kdroidfilter.seforimlibrary.search.LuceneSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class PersonalLibraryService(
    private val manager: PersonalLibraryManager,
    private val overlay: PersonalLibraryOverlay,
    private val search: CompositeSearchEngine,
    private val repository: SeforimRepository,
) {
    fun configuration(): PersonalLibraryConfiguration = manager.store.load()

    suspend fun synchronize(configuration: PersonalLibraryConfiguration, force: Boolean = false): PersonalLibraryConfiguration =
        withContext(Dispatchers.IO) {
            val (updated, artifacts) = manager.synchronize(configuration, force)
            overlay.attach(artifacts?.databasePath)
            val dictionary = Path.of(getDatabasePath()).resolveSibling("lexical.db")
            search.replacePersonal(
                artifacts?.let {
                    LuceneSearchEngine(
                        indexDir = it.indexPath,
                        snippetProvider = RepositorySnippetSourceProvider(repository),
                        dictionaryPath = dictionary,
                    )
                },
            )
            CatalogCache.reloadCatalog()
            updated
        }
}
