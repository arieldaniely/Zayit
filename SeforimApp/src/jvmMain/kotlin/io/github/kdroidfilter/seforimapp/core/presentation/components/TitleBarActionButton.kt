package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleBarActionButton(
    key: IconKey,
    onClick: () -> Unit,
    contentDescription: String,
    tooltipText: String,
    shortcutHint: String? = null,
    enabled: Boolean = true,
    isActive: Boolean = false,
    indicatorColor: Color? = null,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val baseStyle = JewelTheme.iconButtonStyle
    val isIslands = ThemeUtils.isIslandsStyle()
    val activeTabBg =
        JewelTheme.defaultTabStyle.colors
            .backgroundFor(TabState.of(selected = true, active = true))
            .value

    val style =
        remember(accent, baseStyle, isIslands, isActive, activeTabBg) {
            val c = baseStyle.colors
            val normalBg =
                if (isActive) {
                    if (isIslands) accent.copy(alpha = 0.20f) else activeTabBg
                } else {
                    c.background
                }
            val hoverBg =
                if (isActive) {
                    if (isIslands) accent.copy(alpha = 0.25f) else activeTabBg
                } else {
                    accent.copy(alpha = 0.12f)
                }
            val pressBg = accent.copy(alpha = 0.20f)

            IconButtonStyle(
                colors =
                    IconButtonColors(
                        foregroundSelectedActivated = c.foregroundSelectedActivated,
                        background = normalBg,
                        backgroundDisabled = c.backgroundDisabled,
                        backgroundSelected = normalBg,
                        backgroundSelectedActivated = normalBg,
                        backgroundFocused = c.backgroundFocused,
                        backgroundPressed = pressBg,
                        backgroundHovered = hoverBg,
                        border = c.border,
                        borderDisabled = c.borderDisabled,
                        borderSelected = c.borderSelected,
                        borderSelectedActivated = c.borderSelectedActivated,
                        borderFocused = c.borderFocused,
                        borderPressed = Color.Transparent,
                        borderHovered = Color.Transparent,
                    ),
                metrics =
                    if (isIslands) {
                        IconButtonMetrics(
                            cornerSize = CornerSize(8.dp),
                            borderWidth = baseStyle.metrics.borderWidth,
                            padding = baseStyle.metrics.padding,
                            minSize = baseStyle.metrics.minSize,
                        )
                    } else {
                        baseStyle.metrics
                    },
            )
        }

    val buttonModifier = Modifier.width(36.dp).fillMaxHeight().padding(horizontal = 2.dp, vertical = 4.dp)

    Tooltip(
        tooltip = {
            if (shortcutHint.isNullOrBlank()) {
                Text(tooltipText)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tooltipText)
                    Text(shortcutHint, color = JewelTheme.globalColors.text.disabled)
                }
            }
        },
    ) {
        Box(modifier = buttonModifier) {
            IconActionButton(
                key = key,
                onClick = onClick,
                enabled = enabled,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                style = style,
            )
            indicatorColor?.let { color ->
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(8.dp)
                        .background(color, CircleShape),
                )
            }
        }
    }
}
