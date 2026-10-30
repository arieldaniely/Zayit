package io.github.kdroidfilter.seforimapp.features.home

import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeWallpaperTest {
    @Test
    fun copiesAndDownsamplesImagesIndependentlyOfTheirSource() {
        val temp = Files.createTempDirectory("home-wallpaper")
        try {
            val source = temp.resolve("source.png")
            val original = BufferedImage(3000, 10, BufferedImage.TYPE_INT_RGB)
            ImageIO.write(original, "png", source.toFile())
            original.flush()
            val storage = HomeWallpaperStorage(temp.resolve("wallpapers"))
            val name = storage.importImage(source)
            Files.delete(source)
            val stored = assertNotNull(storage.image(name))
            val image = ImageIO.read(stored.toFile())
            assertTrue(image.width <= 2560)
            image.flush()
            assertNull(storage.image("../source.png"))
            assertNull(storage.image(""))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnreadableImagesWithoutPublishingAWallpaper() {
        val temp = Files.createTempDirectory("bad-wallpaper")
        try {
            val source = Files.writeString(temp.resolve("invalid.png"), "invalid image")
            val directory = temp.resolve("wallpapers")
            assertFails { HomeWallpaperStorage(directory).importImage(source) }
            assertTrue(!Files.exists(directory))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
