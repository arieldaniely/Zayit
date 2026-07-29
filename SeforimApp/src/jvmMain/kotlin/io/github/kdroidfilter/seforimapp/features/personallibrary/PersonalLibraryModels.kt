package io.github.kdroidfilter.seforimapp.features.personallibrary

import kotlinx.serialization.Serializable

@Serializable
enum class PersonalFolderPlacement { PERSONAL_BOOKS, MERGE_WITH_LIBRARY }

@Serializable
data class PersonalBookFolder(
    val id: String,
    val path: String,
    val displayName: String,
    val placement: PersonalFolderPlacement = PersonalFolderPlacement.PERSONAL_BOOKS,
    val enabled: Boolean = true,
    val lastBookCount: Int = 0,
    val lastLinkCount: Int = 0,
    val lastImportedAt: Long? = null,
    val lastError: String? = null,
)

@Serializable
data class PersonalLibraryConfiguration(
    val folders: List<PersonalBookFolder> = emptyList(),
    val activeGeneration: String? = null,
    val synchronizedFingerprint: String? = null,
)

data class PersonalLibraryArtifacts(
    val generation: String,
    val databasePath: java.nio.file.Path,
    val indexPath: java.nio.file.Path,
)

data class PersonalImportSummary(val books: Int, val links: Int)
