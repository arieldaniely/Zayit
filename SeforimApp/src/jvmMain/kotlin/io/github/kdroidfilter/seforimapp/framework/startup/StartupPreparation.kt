package io.github.kdroidfilter.seforimapp.framework.startup

import io.github.kdroidfilter.seforimapp.core.coroutines.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Repository construction can import personal books and must finish before reader queries start. */
internal suspend fun prepareStartup(prepare: () -> Unit): Result<Unit> =
    runSuspendCatching {
        withContext(Dispatchers.IO) { prepare() }
    }
