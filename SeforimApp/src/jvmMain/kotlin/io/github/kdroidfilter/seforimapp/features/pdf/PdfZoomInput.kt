package io.github.kdroidfilter.seforimapp.features.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.math.abs
import kotlin.math.exp

internal enum class PdfZoomCommand {
    ZoomIn,
    ZoomOut,
}

internal fun pdfZoomCommand(
    key: Key,
    type: KeyEventType,
    isCtrlPressed: Boolean,
    isMetaPressed: Boolean,
): PdfZoomCommand? {
    if (type != KeyEventType.KeyDown || (!isCtrlPressed && !isMetaPressed)) return null
    return when (key) {
        Key.Plus, Key.NumPadAdd, Key.Equals -> PdfZoomCommand.ZoomIn
        Key.Minus, Key.NumPadSubtract -> PdfZoomCommand.ZoomOut
        else -> null
    }
}

internal fun applyPdfZoomCommand(
    zoom: Float,
    command: PdfZoomCommand,
): Float =
    when (command) {
        PdfZoomCommand.ZoomIn -> zoom + PDF_ZOOM_STEP
        PdfZoomCommand.ZoomOut -> zoom - PDF_ZOOM_STEP
    }.coerceIn(PDF_ZOOM_MIN, PDF_ZOOM_MAX)

internal fun pdfZoomFromScroll(
    zoom: Float,
    scrollDelta: Offset,
): Float? = pdfZoomFactorFromScroll(scrollDelta)?.let { factor -> pdfZoomFromGesture(zoom, factor) }

internal fun pdfZoomFactorFromScroll(scrollDelta: Offset): Float? {
    val dominantDelta =
        if (abs(scrollDelta.y) >= abs(scrollDelta.x)) {
            scrollDelta.y
        } else {
            scrollDelta.x
        }
    if (dominantDelta == 0f) return null
    val exponent =
        (-dominantDelta * PDF_TRACKPAD_ZOOM_SENSITIVITY)
            .coerceIn(-PDF_TRACKPAD_MAX_EXPONENT, PDF_TRACKPAD_MAX_EXPONENT)
    return exp(exponent.toDouble()).toFloat()
}

internal fun pdfZoomFromGesture(
    zoom: Float,
    gestureZoom: Float,
): Float? {
    if (!gestureZoom.isFinite() || gestureZoom <= 0f || gestureZoom == 1f) return null
    return (zoom * gestureZoom).coerceIn(PDF_ZOOM_MIN, PDF_ZOOM_MAX)
}

internal class PdfZoomAccumulator(
    initialZoom: Float,
) {
    var targetZoom: Float = initialZoom

    fun applyFactor(factor: Float): Boolean {
        val newZoom = pdfZoomFromGesture(targetZoom, factor) ?: return false
        if (abs(newZoom - targetZoom) < PDF_ZOOM_CHANGE_EPSILON) return false
        targetZoom = newZoom
        return true
    }
}

internal fun pdfContentWidthScale(zoom: Float): Float = zoom.coerceAtLeast(1f)

internal fun pdfPageWidthFraction(zoom: Float): Float = (zoom / pdfContentWidthScale(zoom)).coerceIn(0f, 1f)

internal data class PdfZoomController(
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit,
)

/** Routes window-level shortcuts to the saveable zoom state owned by each composed PDF tab. */
internal object PdfZoomControllerRegistry {
    private val controllers = mutableMapOf<String, PdfZoomController>()

    fun register(
        tabId: String,
        controller: PdfZoomController,
    ) {
        controllers[tabId] = controller
    }

    fun unregister(
        tabId: String,
        controller: PdfZoomController,
    ) {
        controllers.remove(tabId, controller)
    }

    fun dispatch(
        tabId: String,
        command: PdfZoomCommand,
    ): Boolean {
        val controller = controllers[tabId] ?: return false
        when (command) {
            PdfZoomCommand.ZoomIn -> controller.zoomIn()
            PdfZoomCommand.ZoomOut -> controller.zoomOut()
        }
        return true
    }
}

private const val PDF_TRACKPAD_ZOOM_SENSITIVITY = 0.08f
private const val PDF_TRACKPAD_MAX_EXPONENT = 0.25f
private const val PDF_ZOOM_CHANGE_EPSILON = 0.0001f
