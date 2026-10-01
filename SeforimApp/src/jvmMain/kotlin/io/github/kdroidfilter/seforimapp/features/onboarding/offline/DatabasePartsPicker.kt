package io.github.kdroidfilter.seforimapp.features.onboarding.offline

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Select the first part or a complete archive. Extraction discovers and validates all
 * consecutive numbered parts in the same directory, regardless of the part count.
 * Run the native file picker on IO to keep the GTK event loop responsive.
 */
suspend fun pickDatabaseParts(onPart01Picked: (String?) -> Unit = {}): String? {
    val part01Path =
        withContext(Dispatchers.IO) {
            FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("part01", "zst")))
        }?.path
    onPart01Picked(part01Path)
    if (part01Path.isNullOrBlank()) return null

    if (part01Path.endsWith(".zst", ignoreCase = true)) return part01Path

    return part01Path
}
