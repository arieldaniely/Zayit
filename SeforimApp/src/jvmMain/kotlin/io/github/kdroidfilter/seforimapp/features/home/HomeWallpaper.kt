package io.github.kdroidfilter.seforimapp.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import io.github.kdroidfilter.seforimapp.core.settings.SettingCodec
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.*
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.ceil

internal enum class HomeWallpaperKind { OLIVE, DAWN, FOREST, CUSTOM }

internal enum class WallpaperFit { FILL, FIT }

internal object HomeAppearanceSettings : SettingsGroup("home_appearance") {
    val wallpaper by setting(
        HomeWallpaperKind.OLIVE,
        SettingCodec({ it.name }, { name -> HomeWallpaperKind.entries.find { it.name == name } }),
    )
    val imageName by setting("", SettingCodec({ it }, { it }))
    val fit by setting(WallpaperFit.FILL, SettingCodec({ it.name }, { name -> WallpaperFit.entries.find { it.name == name } }))
    val strength by float(0.3f)
}

/** Copies a validated, bounded image into app storage, independently of the original file. */
internal class HomeWallpaperStorage(
    private val configuredDirectory: Path? = null,
) {
    private val directory by lazy {
        configuredDirectory ?: Path
            .of(getUserSettingsDatabasePath())
            .toAbsolutePath()
            .parent
            .resolve("wallpapers")
    }

    fun image(name: String): Path? =
        name
            .takeIf { it.isNotBlank() && Path.of(it).fileName.toString() == it }
            ?.let(directory::resolve)
            ?.takeIf(Files::isRegularFile)

    fun importImage(source: Path): String {
        val image =
            ImageIO.createImageInputStream(source.toFile()).use { input ->
                val readers = ImageIO.getImageReaders(input)
                require(readers.hasNext()) { "Unsupported wallpaper image" }
                val reader = readers.next()
                try {
                    reader.input = input
                    val step = ceil(maxOf(reader.getWidth(0), reader.getHeight(0)) / 2560.0).toInt().coerceAtLeast(1)
                    val parameters = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
                    reader.read(0, parameters)
                } finally {
                    reader.dispose()
                }
            }
        Files.createDirectories(directory)
        val name = "${UUID.randomUUID()}.png"
        val staging = Files.createTempFile(directory, ".wallpaper-", ".png")
        try {
            check(ImageIO.write(image, "png", staging.toFile()))
            Files.move(staging, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            image.flush()
            Files.deleteIfExists(staging)
        }
        return name
    }
}

@Composable
internal fun ConfiguredHomeWallpaper(modifier: Modifier = Modifier) {
    val settings = LocalAppGraph.current.appSettings
    val enabled by settings.showHomeWallpaperFlow.collectAsState()
    val kind by settings.flow(HomeAppearanceSettings.wallpaper).collectAsState()
    val image by settings.flow(HomeAppearanceSettings.imageName).collectAsState()
    val fit by settings.flow(HomeAppearanceSettings.fit).collectAsState()
    val strength by settings.flow(HomeAppearanceSettings.strength).collectAsState()
    if (enabled) HomeWallpaper(kind, modifier, image, fit, strength)
}

/** Shared by Home, the appearance preview, and the wallpaper thumbnails. */
@Composable
internal fun HomeWallpaper(
    kind: HomeWallpaperKind,
    modifier: Modifier = Modifier,
    imageName: String = "",
    fit: WallpaperFit = WallpaperFit.FILL,
    strength: Float = 0.75f,
) {
    val background = JewelTheme.globalColors.panelBackground
    val dark = JewelTheme.isDark
    val resource = if (dark) Res.drawable.homepage_wallpaper_dark_medium else Res.drawable.homepage_wallpaper_medium
    val environment = rememberResourceEnvironment()
    val bitmap by produceState<ImageBitmap?>(null, kind, imageName, resource) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    when (kind) {
                        HomeWallpaperKind.OLIVE -> getDrawableResourceBytes(environment, resource).decodeToImageBitmap()
                        HomeWallpaperKind.CUSTOM ->
                            HomeWallpaperStorage()
                                .image(
                                    imageName,
                                )?.let { Files.readAllBytes(it).decodeToImageBitmap() }
                        else -> null
                    }
                }.getOrNull()
            }
    }
    Box(modifier.background(background)) {
        if (kind == HomeWallpaperKind.DAWN || kind == HomeWallpaperKind.FOREST) {
            LandscapeWallpaper(kind, dark, Modifier.fillMaxSize())
        } else {
            bitmap?.let {
                Image(
                    it,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale =
                        if (fit ==
                            WallpaperFit.FILL
                        ) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        },
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                background.copy(
                    alpha =
                        1f - strength.takeIf(Float::isFinite).let { it ?: 0.3f }.coerceIn(0f, 1f),
                ),
            ),
        )
    }
}

@Composable
private fun LandscapeWallpaper(
    kind: HomeWallpaperKind,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val dawn = kind == HomeWallpaperKind.DAWN
    val sky =
        when {
            dark && dawn -> listOf(Color(0xFF201E39), Color(0xFF845B6D))
            dark -> listOf(Color(0xFF10292D), Color(0xFF395B55))
            dawn -> listOf(Color(0xFFF3D9C6), Color(0xFFB8CCDB))
            else -> listOf(Color(0xFFD4E5DD), Color(0xFF83AFA2))
        }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(sky))
        drawCircle(
            Color(0xFFFFE8BB).copy(alpha = if (dark) 0.45f else 0.8f),
            size.minDimension * 0.11f,
            Offset(
                size.width * 0.72f,
                size.height * 0.28f,
            ),
        )
        repeat(4) { layer ->
            val y = size.height * (0.48f + layer * 0.13f)
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.25f,
                        y - size.height * 0.22f,
                        size.width * 0.55f,
                        y + size.height * 0.25f,
                        size.width,
                        y - size.height * 0.06f,
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(path, (if (dawn) Color(0xFF577A83) else Color(0xFF315F51)).copy(alpha = 0.25f + layer * 0.15f))
        }
    }
}
