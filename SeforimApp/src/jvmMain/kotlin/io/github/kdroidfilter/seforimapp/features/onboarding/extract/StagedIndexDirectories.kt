package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import java.io.Closeable
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Publish complete indexes only after the archive has been read successfully. */
internal class StagedIndexDirectories(
    destination: File,
) : Closeable {
    private val destination = destination.canonicalFile.toPath()
    private val staging = Files.createTempDirectory(this.destination, ".index-install-")
    private val components = linkedSetOf<String>()
    private var preserveBackup = false

    fun resolve(name: String): File {
        val selected =
            destination
                .resolve(name)
                .toFile()
                .canonicalFile
                .toPath()
        require(selected.startsWith(destination)) { "Unsafe archive entry" }
        val component = destination.relativize(selected).firstOrNull()?.toString()
        if (component !in INDEX_DIRECTORIES) return selected.toFile()
        components.add(component!!)
        return staging.resolve(destination.relativize(selected)).toFile()
    }

    fun commit() {
        val backups = staging.resolve(".previous")
        Files.createDirectories(backups)
        val replaced = mutableListOf<String>()
        try {
            for (component in components) {
                val target = destination.resolve(component)
                val backup = backups.resolve(component)
                if (Files.exists(target)) Files.move(target, backup)
                replaced.add(component)
                Files.move(staging.resolve(component), target)
            }
        } catch (failure: Exception) {
            for (component in replaced.asReversed()) {
                try {
                    val target = destination.resolve(component)
                    val backup = backups.resolve(component)
                    if (Files.exists(target)) Files.move(target, staging.resolve(component))
                    if (Files.exists(backup)) Files.move(backup, target)
                } catch (rollback: Exception) {
                    preserveBackup = true
                    failure.addSuppressed(rollback)
                }
            }
            throw failure
        }
    }

    override fun close() {
        if (preserveBackup) return
        // Do not follow links while removing our own staging directory or old index files.
        Files.walkFileTree(
            staging,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: java.io.IOException?,
                ): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private companion object {
        val INDEX_DIRECTORIES = setOf("seforim.db.lucene", "seforim.db.lookup.lucene", "seforim.db.semantic")
    }
}
