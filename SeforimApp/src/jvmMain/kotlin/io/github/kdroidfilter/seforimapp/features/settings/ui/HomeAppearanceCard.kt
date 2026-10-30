package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.features.home.*
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*
import java.nio.file.Path

@Composable
internal fun AppearancePreview(
    style: ThemeStyle,
    accent: AccentColor,
) {
    val color = accent.resolveColor(JewelTheme.isDark)
    val shape = RoundedCornerShape(if (style == ThemeStyle.Islands) 12.dp else 2.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.appearance_preview), fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .height(225.dp)
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().background(JewelTheme.globalColors.toolwindowBackground).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(Res.string.app_name), fontSize = 12.sp)
                Box(Modifier.size(60.dp, 4.dp).align(Alignment.CenterVertically).background(color, shape))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ConfiguredHomeWallpaper(Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.app_name), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier
                            .widthIn(max = 380.dp)
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                JewelTheme.globalColors.panelBackground.copy(alpha = 0.9f),
                            ).border(1.dp, color, shape)
                            .padding(10.dp),
                    ) { Text(stringResource(Res.string.home_widget_search), fontSize = 12.sp) }
                    Text(stringResource(Res.string.appearance_preview_sample), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
internal fun ThemeModeChoices(
    mode: IntUiThemes,
    onChange: (IntUiThemes) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IntUiThemes.entries.forEach { theme ->
            val label =
                when (theme) {
                    IntUiThemes.Light -> Res.string.light_theme
                    IntUiThemes.Dark -> Res.string.dark_theme
                    IntUiThemes.System -> Res.string.system_theme
                }
            RadioButtonRow(text = stringResource(label), selected = mode == theme, onClick = { onChange(theme) })
        }
    }
}

@Composable
internal fun HomeAppearanceCard() {
    val settings = LocalAppGraph.current.appSettings
    val kind by settings.flow(HomeAppearanceSettings.wallpaper).collectAsState()
    val imageName by settings.flow(HomeAppearanceSettings.imageName).collectAsState()
    val fit by settings.flow(HomeAppearanceSettings.fit).collectAsState()
    val strength by settings.flow(HomeAppearanceSettings.strength).collectAsState()
    val storage = remember { HomeWallpaperStorage() }
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf(false) }

    fun chooseImage() {
        scope.launch {
            try {
                val file =
                    FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("png", "jpg", "jpeg", "bmp", "gif")))
                        ?: return@launch
                importing = true
                imageError = false
                val name = withContext(Dispatchers.IO) { storage.importImage(Path.of(file.path)) }
                settings[HomeAppearanceSettings.imageName] = name
                settings[HomeAppearanceSettings.wallpaper] = HomeWallpaperKind.CUSTOM
                settings.setShowHomeWallpaperEnabled(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                imageError = true
            } finally {
                importing = false
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.wallpaper_title), fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeWallpaperKind.entries.forEach { choice ->
                val label =
                    when (choice) {
                        HomeWallpaperKind.OLIVE -> Res.string.wallpaper_olive
                        HomeWallpaperKind.DAWN -> Res.string.wallpaper_dawn
                        HomeWallpaperKind.FOREST -> Res.string.wallpaper_forest
                        HomeWallpaperKind.CUSTOM -> Res.string.wallpaper_custom
                    }
                Column(
                    Modifier.weight(1f).selectable(selected = kind == choice, role = Role.RadioButton) {
                        if (choice == HomeWallpaperKind.CUSTOM && imageName.isEmpty()) {
                            chooseImage()
                        } else {
                            settings[HomeAppearanceSettings.wallpaper] = choice
                            settings.setShowHomeWallpaperEnabled(true)
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HomeWallpaper(
                        choice,
                        imageName = imageName,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .clip(shape)
                                .border(
                                    if (kind ==
                                        choice
                                    ) {
                                        2.dp
                                    } else {
                                        1.dp
                                    },
                                    if (kind ==
                                        choice
                                    ) {
                                        JewelTheme.globalColors.outlines.focused
                                    } else {
                                        JewelTheme.globalColors.borders.normal
                                    },
                                    shape,
                                ),
                    )
                    Text(stringResource(label), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = ::chooseImage, enabled = !importing) { Text(stringResource(Res.string.wallpaper_choose_image)) }
            if (importing) CircularProgressIndicator(Modifier.size(18.dp))
            OutlinedButton(onClick = { settings.reset(HomeAppearanceSettings) }) { Text(stringResource(Res.string.wallpaper_reset)) }
        }
        if (imageError) Text(stringResource(Res.string.wallpaper_image_error), color = JewelTheme.globalColors.text.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.wallpaper_fit), modifier = Modifier.weight(1f))
            ListComboBox(
                items = listOf(stringResource(Res.string.wallpaper_fill), stringResource(Res.string.wallpaper_fit_image)),
                selectedIndex = fit.ordinal,
                onSelectedItemChange = { settings[HomeAppearanceSettings.fit] = WallpaperFit.entries[it] },
                modifier = Modifier.width(180.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.wallpaper_strength), modifier = Modifier.weight(1f))
            val values = listOf(0.15f, 0.3f, 0.6f, 1f)
            ListComboBox(
                items =
                    listOf(
                        Res.string.wallpaper_subtle,
                        Res.string.wallpaper_balanced,
                        Res.string.wallpaper_vivid,
                        Res.string.wallpaper_full,
                    ).map {
                        stringResource(it)
                    },
                selectedIndex = values.indices.minBy { kotlin.math.abs(values[it] - strength) },
                onSelectedItemChange = { settings[HomeAppearanceSettings.strength] = values[it] },
                modifier = Modifier.width(180.dp),
            )
        }
    }
}
