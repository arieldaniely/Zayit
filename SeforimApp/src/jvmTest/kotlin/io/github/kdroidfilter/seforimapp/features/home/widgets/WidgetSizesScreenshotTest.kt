package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.ClockWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every widget at every size the grid lets it take, and measures the widest empty band across each card: a
 * widget must never be resizable to a size it can't fill. Every size and the report go to build/screenshots/sizes. The Filament widgets (the Earth, the sky, the solar system) need a real window and fill
 * their card with their view; they're left out. So is the dictionary: it reads its books on first use, and the tests have
 * no library DB, so its card holds its title and field over the room its words would take. The logo and search controls
 * deliberately allow whitespace around their content; they are not content cards.
 * The clock is measured inside its square dial; the surrounding space preserves its circular shape.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetSizesScreenshotTest {
    private val graph by lazy {
        testAppGraph().also(::seed)
    }

    /** Enough history, favorites and notes in the tests' own user DB for their widgets to fill every size. */
    private fun seed(graph: AppGraph) =
        runBlocking {
            val now = System.currentTimeMillis()
            repeat(40) { i ->
                if (i % 4 == 3) {
                    graph.historyStore.recordSearchVisit(SEARCHES[i % SEARCHES.size], now - i * HOUR)
                } else {
                    graph.historyStore.recordBookVisit(1000L + i, TITLES[i % TITLES.size], now - i * HOUR)
                }
            }
            val folder =
                graph.favoritesStore
                    .folders()
                    .firstOrNull()
                    ?.id ?: graph.favoritesStore.createFolder("תיקייה", now)
            repeat(40) { i ->
                graph.favoritesStore.add(2000L + i, TITLES[i % TITLES.size], now - i * HOUR, folderId = folder.takeIf { i % 3 == 0 })
            }
            graph.noteStore.recent(100).forEach { graph.noteStore.removeNote(it.bookId, it.note.id) }
            repeat(20) { i ->
                graph.noteStore.addNote(3000L + i, 1L, 0, 5, NOTES[i % NOTES.size], now - i * HOUR, quote = QUOTES[i % QUOTES.size])
            }
        }

    private val pitch = CellPitch(MAX_GRID_WIDTH)
    private val out =
        File("build/screenshots/sizes").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun `no widget size leaves its card half empty`() {
        val faults = mutableListOf<String>()
        val report = StringBuilder()
        val contentWidgets =
            availableHomeWidgets.filterNot {
                it.id in FILAMENT_WIDGETS || it.id in LIBRARY_DB_WIDGETS || it.id in HOME_CONTROL_WIDGETS
            }
        for (widget in contentWidgets) {
            for (w in widget.minSpan.w..minOf(widget.maxSpan.w, HOME_GRID_COLUMNS)) {
                val minRows = widget.minRows(w, pitch)
                for (h in minRows..maxOf(widget.maxSpan.h, minRows)) {
                    val maxEmptyHeight =
                        when (widget.id) {
                            "zmanim" -> ZMANIM_CARD_ROWS_GAP
                            // A calendar spaces its week rows evenly. One row's gap isn't an unused part of the card.
                            "calendar" -> (pitch.height(h).value / 5).toInt()
                            // The number of prayer additions changes with the date; short days keep the list centred.
                            "tefila" -> 64
                            else -> MAX_EMPTY_HEIGHT
                        }
                    val image = shot(widget, w, h, maxEmptyHeight)
                    val (rows, columns) = emptyBands(image)
                    val filled =
                        if (widget == ClockWidget) {
                            dialFillsFrame(image)
                        } else {
                            rows <= maxEmptyHeight && columns <= MAX_EMPTY_WIDTH
                        }
                    val measurement =
                        if (widget == ClockWidget) "dial fills its frame: $filled" else "empty band ${rows}dp high, ${columns}dp wide"
                    val line = "${widget.id} ${w}x$h: $measurement"
                    report.appendLine(line)
                    ImageIO.write(image, "png", File(out, "${widget.id}-${w}x$h.png"))
                    if (!filled) {
                        faults += line
                    }
                }
            }
        }
        File(out, "report.txt").writeText(report.toString())
        assertTrue(faults.isEmpty(), faults.joinToString("\n"))
    }

    private fun shot(
        widget: HomeWidget,
        w: Int,
        h: Int,
        maxEmptyHeight: Int,
    ): BufferedImage {
        var image: BufferedImage? = null
        val width = pitch.width(w)
        val height = maxOf(pitch.height(h), widget.heightAt(width) ?: 0.dp)
        runDesktopComposeUiTest(width = width.value.toInt(), height = height.value.toInt()) {
            setContent {
                IntUiTheme(isDark = true) {
                    CompositionLocalProvider(
                        LocalAppGraph provides graph,
                        LocalTabSelected provides false,
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
                        Box(Modifier.background(JewelTheme.globalColors.panelBackground)) {
                            val modifier = Modifier.size(width, height)
                            widget.Content(state, modifier)
                        }
                    }
                }
            }
            waitForIdle()
            val content =
                if (widget == ClockWidget) {
                    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                } else {
                    onRoot()
                }

            fun captureContent(): BufferedImage {
                val captured = content.captureToImage().toAwtImage()
                if (widget != ClockWidget) return captured
                // The circular dial keeps its aspect ratio inside a resizable rectangular card.
                val side = minOf(captured.width, captured.height)
                assertTrue(side > FRAME_INSET * 2 + MAX_EMPTY_HEIGHT, "Clock dial is too small to measure: $side")
                return captured.getSubimage((captured.width - side) / 2, (captured.height - side) / 2, side, side)
            }

            // Store reads and image decoding run outside Compose's idle tracking. Wait for the rendered content,
            // rather than racing them with a fixed sleep; retain the final frame for the failure report.
            try {
                waitUntil(timeoutMillis = 3_000) {
                    image = captureContent()
                    if (widget == ClockWidget) {
                        dialFillsFrame(image!!)
                    } else {
                        val (rows, columns) = emptyBands(image!!)
                        rows <= maxEmptyHeight && columns <= MAX_EMPTY_WIDTH
                    }
                }
            } catch (_: ComposeTimeoutException) {
                image = captureContent()
            }
        }
        return image!!
    }

    /** A dial may have an empty centre, but its visible ticks must reach near all four sides of its square. */
    private fun dialFillsFrame(image: BufferedImage): Boolean {
        val background = image.getRGB(0, 0)
        var left = image.width
        var right = -1
        var top = image.height
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val difference =
                    maxOf(
                        abs((pixel shr 16 and 0xFF) - (background shr 16 and 0xFF)),
                        abs((pixel shr 8 and 0xFF) - (background shr 8 and 0xFF)),
                        abs((pixel and 0xFF) - (background and 0xFF)),
                    )
                if (difference < FLAT_RANGE) continue
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        return right - left >= image.width * 0.8f && bottom - top >= image.height * 0.8f
    }

    /** The highest run of rows, and the widest run of columns, with nothing drawn on them, in dp, inside the frame. */
    private fun emptyBands(image: BufferedImage): Pair<Int, Int> {
        val inset = FRAME_INSET
        val xs = inset until image.width - inset
        val ys = inset until image.height - inset

        fun luminance(
            x: Int,
            y: Int,
        ): Int {
            val rgb = image.getRGB(x, y)
            return ((rgb shr 16 and 0xFF) * 3 + (rgb shr 8 and 0xFF) * 6 + (rgb and 0xFF)) / 10
        }

        fun flat(values: Sequence<Int>): Boolean {
            var min = 255
            var max = 0
            for (v in values) {
                min = minOf(min, v)
                max = maxOf(max, v)
            }
            return max - min < FLAT_RANGE
        }

        fun longestRun(flags: List<Boolean>): Int =
            flags
                .fold(0 to 0) { (best, run), empty ->
                    if (empty) {
                        maxOf(best, run + 1) to run + 1
                    } else {
                        best to
                            0
                    }
                }.first
        val rows = longestRun(ys.map { y -> flat(xs.asSequence().map { x -> luminance(x, y) }) })
        val columns = longestRun(xs.map { x -> flat(ys.asSequence().map { y -> luminance(x, y) }) })
        return rows to columns
    }

    private companion object {
        val HOME_CONTROL_WIDGETS = homeControlWidgets.map { it.widget.id }.toSet()
        val FILAMENT_WIDGETS = setOf("earth", "sky", "solar_system")
        val LIBRARY_DB_WIDGETS = setOf("dictionary")

        const val HOUR = 3_600_000L

        // As long as real entries: a book, its part and its chapter
        val TITLES =
            listOf(
                "שולחן ערוך, אורח חיים, סימן קכח",
                "משנה ברורה, סימן רסג",
                "ברכות, דף כו.",
                "רמב״ם, הלכות תפילה, פרק ד",
                "בראשית, פרק כח",
                "מסילת ישרים, פרק יט",
            )
        val SEARCHES = listOf("תפילת הדרך", "קריאת שמע על המטה", "ברכת הלבנה")
        val NOTES = listOf("לעיין בשיטת הרמב״ם כאן", "השווה למשנה ברורה ס״ק ב", "קושיא: למה לא הביא את הירושלמי?")
        val QUOTES = listOf("והוא רחום יכפר עון", "מאימתי קורין את שמע בערבין", "ויצא יעקב מבאר שבע")

        /** The frame, its rounded corners and the cards' own padding: not content, never counted. */
        const val FRAME_INSET = 16

        /** Below this luminance spread a line holds no text, only backgrounds. */
        const val FLAT_RANGE = 28

        // Wider than a gap between two lines of text or two columns of it
        const val MAX_EMPTY_HEIGHT = 40

        // The zmanim are rows of cards: the gap between two rows and the cards' padding read as one empty band
        const val ZMANIM_CARD_ROWS_GAP = 56
        const val MAX_EMPTY_WIDTH = 120
    }
}
