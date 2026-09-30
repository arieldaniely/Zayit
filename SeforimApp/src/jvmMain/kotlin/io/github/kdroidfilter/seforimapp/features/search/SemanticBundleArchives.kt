package io.github.kdroidfilter.seforimapp.features.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal enum class SemanticBundleImportProblem {
    MISSING_PARTS,
    INVALID_MANIFEST,
    INVALID_SELECTION,
    DAMAGED_ARCHIVE,
}

internal class SemanticBundleImportException(
    val problem: SemanticBundleImportProblem,
    val missingParts: List<String> = emptyList(),
    cause: Throwable? = null,
) : IOException(problem.name, cause)

/** Selecting any split part discovers the other parts beside it, in numeric order. */
internal fun resolveSemanticBundleArchives(selected: List<Path>): List<Path> {
    val archives = selected.map { it.toAbsolutePath().normalize() }.distinct()
    val pattern = Regex("semantic-bundle\\.tar\\.zst\\.part(\\d+)")
    if (archives.isEmpty() || archives.any { !pattern.matches(it.fileName.toString()) }) return archives

    val directory = archives.first().parent
    if (archives.any { it.parent != directory }) {
        throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_SELECTION)
    }
    val candidates =
        Files.list(directory).use { files ->
            files.filter { Files.isRegularFile(it) && pattern.matches(it.fileName.toString()) }.toList()
        }
    val parts =
        candidates.associateBy { path ->
            pattern.matchEntire(path.fileName.toString())!!.groupValues[1].toIntOrNull()
                ?: throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_SELECTION)
        }
    if (parts.isEmpty() || parts.size != candidates.size || parts.keys.any { it !in 1..10_000 }) {
        throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_SELECTION)
    }

    val manifestPath = directory.resolve("semantic-bundle.json")
    val expectedParts =
        if (Files.isRegularFile(manifestPath)) {
            try {
                val manifest = Json.parseToJsonElement(Files.readString(manifestPath)).jsonObject
                val count = manifest["parts"]?.jsonPrimitive?.intOrNull
                if (
                    manifest["archiveType"]?.jsonPrimitive?.content != "tar.zst" ||
                    count == null ||
                    count !in 1..10_000 ||
                    count < parts.keys.max()
                ) {
                    throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_MANIFEST)
                }
                count
            } catch (failure: SemanticBundleImportException) {
                throw failure
            } catch (failure: Exception) {
                throw SemanticBundleImportException(SemanticBundleImportProblem.INVALID_MANIFEST, cause = failure)
            }
        } else {
            parts.keys.max()
        }
    val missing = (1..expectedParts).filter { it !in parts }
    if (missing.isNotEmpty()) {
        throw SemanticBundleImportException(
            SemanticBundleImportProblem.MISSING_PARTS,
            missingParts = missing.map { "semantic-bundle.tar.zst.part${it.toString().padStart(2, '0')}" },
        )
    }
    return (1..expectedParts).map { parts.getValue(it) }
}
