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
    val clearCenter by boolean(true)
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
    val clearCenter by settings.flow(HomeAppearanceSettings.clearCenter).collectAsState()
    if (enabled) HomeWallpaper(kind, modifier, image, fit, strength, clearCenter)
}

/** Shared by Home, the appearance preview, and the wallpaper thumbnails. */
@Composable
internal fun HomeWallpaper(
    kind: HomeWallpaperKind,
    modifier: Modifier = Modifier,
    imageName: String = "",
    fit: WallpaperFit = WallpaperFit.FILL,
    strength: Float = 0.75f,
    clearCenter: Boolean = false,
) {
    val background = JewelTheme.globalColors.panelBackground
    val dark = JewelTheme.isDark
    val resource =
        when (kind) {
            HomeWallpaperKind.OLIVE -> if (dark) Res.drawable.homepage_wallpaper_dark_medium else Res.drawable.homepage_wallpaper_medium
            HomeWallpaperKind.DAWN -> if (dark) Res.drawable.wallpaper_dawn_dark else Res.drawable.wallpaper_dawn_light
            HomeWallpaperKind.FOREST -> if (dark) Res.drawable.wallpaper_forest_dark else Res.drawable.wallpaper_forest_light
            HomeWallpaperKind.CUSTOM -> null
        }
    val environment = rememberResourceEnvironment()
    val bitmap by produceState<ImageBitmap?>(null, kind, imageName, resource) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    when (kind) {
                        HomeWallpaperKind.CUSTOM ->
                            HomeWallpaperStorage()
                                .image(
                                    imageName,
                                )?.let { Files.readAllBytes(it).decodeToImageBitmap() }
                        else -> resource?.let { getDrawableResourceBytes(environment, it).decodeToImageBitmap() }
                    }
                }.getOrNull()
            }
    }
    Box(modifier.background(background)) {
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
        Box(
            Modifier.fillMaxSize().background(
                background.copy(
                    alpha =
                        1f - strength.takeIf(Float::isFinite).let { it ?: 0.3f }.coerceIn(0f, 1f),
                ),
            ),
        )
        if (clearCenter) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        0.00f to background.copy(alpha = 0.10f),
                        0.04f to background.copy(alpha = 0.20f),
                        0.08f to background.copy(alpha = 0.35f),
                        0.13f to background.copy(alpha = 0.50f),
                        0.20f to background.copy(alpha = 0.65f),
                        0.28f to background.copy(alpha = 0.78f),
                        0.38f to background.copy(alpha = 0.85f),
                        0.50f to background.copy(alpha = 0.78f),
                        0.62f to background.copy(alpha = 0.85f),
                        0.72f to background.copy(alpha = 0.78f),
                        0.80f to background.copy(alpha = 0.65f),
                        0.87f to background.copy(alpha = 0.50f),
                        0.92f to background.copy(alpha = 0.35f),
                        0.96f to background.copy(alpha = 0.20f),
                        1.00f to background.copy(alpha = 0.10f),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        0.00f to background,
                        0.35f to background,
                        0.55f to background.copy(alpha = 0.75f),
                        0.75f to background.copy(alpha = 0.40f),
                        1.00f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.65f),
                        radius = size.width * 0.55f,
                    ),
                )
            }
        }
    }
}
