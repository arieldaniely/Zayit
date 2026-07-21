package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val HOME_THEME_TRANSITION_DURATION_MS = 240
internal const val HOME_THEME_TRANSITION_FRAME_COUNT = 15
internal const val HOME_THEME_TRANSITION_SPRITE_COLUMNS = 3

/** Maps the continuous day-to-night position to the nearest frame in the transition sprite. */
internal fun homeThemeTransitionFrame(position: Float): Int =
    (position.coerceIn(0f, 1f) * (HOME_THEME_TRANSITION_FRAME_COUNT - 1))
        .roundToInt()
        .coerceIn(0, HOME_THEME_TRANSITION_FRAME_COUNT - 1)

/**
 * Keeps the generated transition fully opaque while it is in motion, then blends its final
 * frames into the exact target wallpaper. This hides minor compression and scaling differences
 * between the video endpoints and the high-resolution static assets.
 */
internal fun homeThemeTransitionAlpha(
    position: Float,
    targetIsDark: Boolean,
): Float {
    val target = if (targetIsDark) 1f else 0f
    return (abs(target - position) / 0.08f).coerceIn(0f, 1f)
}
