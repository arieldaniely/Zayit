package io.github.kdroidfilter.seforimapp.core.history

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*

@Composable
fun VisitEntry.searchDescription(): String? {
    if (kind != VisitKind.SEARCH) return null
    val context = searchContext ?: return stringResource(Res.string.history_search_details_unavailable)
    val mode =
        stringResource(
            when (context.mode) {
                "EXACT" -> Res.string.search_mode_exact
                "SMART" -> Res.string.search_mode_smart
                else -> Res.string.search_mode_flexible
            },
        )
    val scope =
        when {
            context.tocId != 0L -> stringResource(Res.string.history_search_section, context.scopeTitle)
            context.bookId != 0L -> stringResource(Res.string.history_search_book, context.scopeTitle)
            context.categoryId != 0L -> stringResource(Res.string.history_search_category, context.scopeTitle)
            context.globalExtended -> stringResource(Res.string.history_search_all)
            else -> stringResource(Res.string.history_search_base)
        }
    val scopedBase =
        if (!context.globalExtended &&
            (context.tocId != 0L || context.bookId != 0L || context.categoryId != 0L)
        ) {
            stringResource(Res.string.history_search_details, scope, stringResource(Res.string.history_search_base))
        } else {
            scope
        }
    return stringResource(Res.string.history_search_details, mode, scopedBase)
}
