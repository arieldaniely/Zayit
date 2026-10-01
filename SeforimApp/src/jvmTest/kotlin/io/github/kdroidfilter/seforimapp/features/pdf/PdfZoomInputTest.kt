package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfZoomInputTest {
    private val registeredControllers = mutableListOf<Pair<String, PdfZoomController>>()

    @AfterTest
    fun unregisterControllers() {
        registeredControllers.forEach { (tabId, controller) ->
            PdfZoomControllerRegistry.unregister(tabId, controller)
        }
    }

    @Test
    fun `ctrl and command zoom keys map to pdf commands`() {
        assertEquals(
            PdfZoomCommand.ZoomIn,
            pdfZoomCommand(Key.Equals, KeyEventType.KeyDown, isCtrlPressed = true, isMetaPressed = false),
        )
        assertEquals(
            PdfZoomCommand.ZoomIn,
            pdfZoomCommand(Key.NumPadAdd, KeyEventType.KeyDown, isCtrlPressed = false, isMetaPressed = true),
        )
        assertEquals(
            PdfZoomCommand.ZoomOut,
            pdfZoomCommand(Key.Minus, KeyEventType.KeyDown, isCtrlPressed = true, isMetaPressed = false),
        )
    }

    @Test
    fun `zoom shortcuts ignore key up and unmodified keys`() {
        assertNull(pdfZoomCommand(Key.Plus, KeyEventType.KeyUp, isCtrlPressed = true, isMetaPressed = false))
        assertNull(pdfZoomCommand(Key.Minus, KeyEventType.KeyDown, isCtrlPressed = false, isMetaPressed = false))
        assertNull(pdfZoomCommand(Key.F, KeyEventType.KeyDown, isCtrlPressed = true, isMetaPressed = false))
    }

    @Test
    fun `keyboard zoom is clamped to supported range`() {
        assertEquals(PDF_ZOOM_MAX, applyPdfZoomCommand(PDF_ZOOM_MAX, PdfZoomCommand.ZoomIn))
        assertEquals(PDF_ZOOM_MIN, applyPdfZoomCommand(PDF_ZOOM_MIN, PdfZoomCommand.ZoomOut))
    }

    @Test
    fun `trackpad scroll zooms using the dominant axis and clamps the result`() {
        val zoomedIn = pdfZoomFromScroll(PDF_DEFAULT_ZOOM, Offset(x = 0f, y = -2f))
        val zoomedOut = pdfZoomFromScroll(PDF_DEFAULT_ZOOM, Offset(x = 3f, y = 1f))

        assertTrue(requireNotNull(zoomedIn) > PDF_DEFAULT_ZOOM)
        assertTrue(requireNotNull(zoomedOut) < PDF_DEFAULT_ZOOM)
        assertEquals(PDF_ZOOM_MAX, pdfZoomFromScroll(PDF_ZOOM_MAX, Offset(0f, -100f)))
        assertEquals(PDF_ZOOM_MIN, pdfZoomFromScroll(PDF_ZOOM_MIN, Offset(0f, 100f)))
        assertNull(pdfZoomFromScroll(PDF_DEFAULT_ZOOM, Offset.Zero))
    }

    @Test
    fun `pinch gesture zoom is validated and clamped`() {
        assertTrue(requireNotNull(pdfZoomFromGesture(PDF_DEFAULT_ZOOM, 1.1f)) > PDF_DEFAULT_ZOOM)
        assertEquals(PDF_ZOOM_MAX, pdfZoomFromGesture(PDF_ZOOM_MAX, 2f))
        assertNull(pdfZoomFromGesture(PDF_DEFAULT_ZOOM, 1f))
        assertNull(pdfZoomFromGesture(PDF_DEFAULT_ZOOM, Float.NaN))
    }

    @Test
    fun `pointer zoom factors accumulate without using a stale external zoom`() {
        val accumulator = PdfZoomAccumulator(PDF_DEFAULT_ZOOM)

        assertTrue(accumulator.applyFactor(1.1f))
        assertTrue(accumulator.applyFactor(1.1f))

        assertEquals(PDF_DEFAULT_ZOOM * 1.21f, accumulator.targetZoom, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `page width changes continuously around one hundred percent`() {
        val zoomLevels = listOf(0.99f, 1f, 1.01f)

        zoomLevels.forEach { zoom ->
            val effectivePageScale = pdfContentWidthScale(zoom) * pdfPageWidthFraction(zoom)
            assertEquals(zoom, effectivePageScale, absoluteTolerance = 0.0001f)
        }
    }

    @Test
    fun `registry dispatches only to the selected tab controller`() {
        var firstTabZooms = 0
        var secondTabZooms = 0
        register("first", PdfZoomController(zoomIn = { firstTabZooms++ }, zoomOut = { firstTabZooms-- }))
        register("second", PdfZoomController(zoomIn = { secondTabZooms++ }, zoomOut = { secondTabZooms-- }))

        assertTrue(PdfZoomControllerRegistry.dispatch("second", PdfZoomCommand.ZoomIn))
        assertEquals(0, firstTabZooms)
        assertEquals(1, secondTabZooms)
        assertFalse(PdfZoomControllerRegistry.dispatch("missing", PdfZoomCommand.ZoomIn))
    }

    private fun register(
        tabId: String,
        controller: PdfZoomController,
    ) {
        PdfZoomControllerRegistry.register(tabId, controller)
        registeredControllers += tabId to controller
    }
}
