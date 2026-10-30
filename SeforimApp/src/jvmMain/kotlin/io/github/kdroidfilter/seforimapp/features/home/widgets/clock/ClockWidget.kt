package io.github.kdroidfilter.seforimapp.features.home.widgets.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.home.widgets.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

internal object ClockWidget : HomeWidget {
    override val id = "analog_clock"
    override val title = Res.string.home_widget_clock
    override val defaultSpan = CellSpan(6, 6)
    override val minSpan = CellSpan(5, 5)
    override val maxSpan = CellSpan(12, 10)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        val active = LocalTabSelected.current
        val now by produceState(System.currentTimeMillis(), active) {
            while (active) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        val location = state.location
        val zone = location.timeZone.toZoneId()
        val date = state.skyTimeMillis?.let { state.shownDateHere() } ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val times =
            rememberOffMain(date, location, state.zmanimOpinion, state.inIsrael) {
                computeZmanimTimes(date, location, state.zmanimOpinion, state.inIsrael)
            }
        val time = Instant.ofEpochMilli(state.skyTimeMillis ?: now).atZone(zone).toLocalTime()
        val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        val accent = JewelTheme.globalColors.outlines.focused
        val entries =
            listOf(
                Res.string.home_widget_card_sunrise_title to times?.sunrise,
                Res.string.clock_shema to times?.sofZmanShmaGra,
                Res.string.clock_chatzot to times?.chatzosHayom,
                Res.string.clock_mincha_gedola to times?.minchaGedola,
                Res.string.home_widget_card_sunset_title to times?.sunset,
                Res.string.clock_tzais to times?.tzais,
            )
        val next = entries.mapNotNull { it.second }.filter { it.time > now }.minByOrNull(Date::getTime)
        PanelCard(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(time.format(formatter), color = accent, fontWeight = FontWeight.SemiBold)
                }
                state.cityLabel?.let { Text(it, fontSize = 12.sp, color = JewelTheme.globalColors.text.info) }
                AnalogDial(time, Modifier.weight(1f).fillMaxWidth())
                entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, moment) ->
                            Column(
                                Modifier.weight(1f).clickable(enabled = moment != null) { state.targetTime = moment },
                            ) {
                                Text(stringResource(label), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    moment?.let { Instant.ofEpochMilli(it.time).atZone(zone).format(formatter) } ?: "—",
                                    color = if (moment == next) accent else JewelTheme.globalColors.text.normal,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun clockHandDegrees(time: LocalTime): Pair<Float, Float> =
    ((time.hour % 12 + time.minute / 60f) * 30f) to ((time.minute + time.second / 60f) * 6f)

@Composable
private fun AnalogDial(
    time: LocalTime,
    modifier: Modifier = Modifier,
) {
    val foreground = JewelTheme.globalColors.text.normal
    val muted = JewelTheme.globalColors.text.info
    val accent = JewelTheme.globalColors.outlines.focused
    val measurer = rememberTextMeasurer()
    val label = stringResource(Res.string.clock_time_description, time.format(DateTimeFormatter.ofPattern("HH:mm")))
    Canvas(modifier.semantics { contentDescription = label }) {
        val radius = size.minDimension * 0.46f
        val centre = center

        fun point(
            degrees: Float,
            distance: Float,
        ): Offset {
            val radians = Math.toRadians((degrees - 90f).toDouble())
            return centre + Offset(cos(radians).toFloat(), sin(radians).toFloat()) * distance
        }
        drawCircle(muted.copy(alpha = 0.2f), radius, centre, style = Stroke(1.dp.toPx()))
        repeat(60) { tick ->
            val major = tick % 5 == 0
            drawLine(
                if (major) foreground else muted.copy(alpha = 0.4f),
                point(tick * 6f, radius * if (major) 0.88f else 0.94f),
                point(tick * 6f, radius),
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        (1..12).forEach { number ->
            val text = measurer.measure(number.toString(), TextStyle(color = foreground, fontSize = 12.sp))
            val at = point(number * 30f, radius * 0.73f)
            drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
        }
        val (hour, minute) = clockHandDegrees(time)
        drawLine(foreground, centre, point(hour, radius * 0.46f), 5.dp.toPx(), StrokeCap.Round)
        drawLine(accent, centre, point(minute, radius * 0.64f), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(accent, 5.dp.toPx(), centre)
    }
}
