package io.github.kdroidfilter.seforimapp.features.home.widgets.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.window.Dialog
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.SettingCodec
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.home.widgets.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
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
    override val maxSpan = CellSpan(8, 8)
    override val settings get() = ClockSettings

    @Composable
    override fun menuItems(state: HomeWidgetsState): List<WidgetMenuItem> =
        listOf(
            WidgetMenuItem(
                stringResource(Res.string.clock_style),
                children =
                    clockStyles.mapIndexed { index, label ->
                        WidgetMenuItem(stringResource(label), checked = state[ClockSettings.style] == index) {
                            state[ClockSettings.style] =
                                index
                        }
                    },
            ),
        )

    @Composable
    override fun Options(state: HomeWidgetsState) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.clock_style), fontWeight = FontWeight.SemiBold)
            clockStyles.forEachIndexed { index, label ->
                RadioButtonRow(
                    stringResource(label),
                    selected = state[ClockSettings.style] == index,
                    onClick = { state[ClockSettings.style] = index },
                )
            }
        }
    }

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
        var pickingTime by remember { mutableStateOf(false) }
        val entries =
            listOf(
                Res.string.home_widget_card_sunrise_title to times?.sunrise,
                Res.string.clock_shema to times?.sofZmanShmaGra,
                Res.string.clock_chatzot to times?.chatzosHayom,
                Res.string.clock_mincha_gedola to times?.minchaGedola,
                Res.string.home_widget_card_sunset_title to times?.sunset,
                Res.string.clock_tzais to times?.tzais,
                Res.string.clock_dawn to times?.alosHashachar,
                Res.string.clock_prayer to times?.sofZmanTfilaGra,
                Res.string.clock_mincha_ketana to times?.minchaKetana,
                Res.string.clock_plag to times?.plagHamincha,
            )
        val reference = state.skyTimeMillis ?: now
        val next = entries.mapNotNull { it.second }.filter { it.time > reference }.minByOrNull(Date::getTime)
        PanelCard(modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val rows = ((maxHeight.value - 180f) / 40f).toInt().coerceIn(2, 5)
                val sorted = entries.sortedBy { it.second?.time ?: Long.MAX_VALUE }
                val visible =
                    (
                        sorted.filter { (it.second?.time ?: 0) >= reference } +
                            sorted.filter { (it.second?.time ?: 0) < reference }
                    ).take(rows * 2)
                        .sortedBy { it.second?.time ?: Long.MAX_VALUE }
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { pickingTime = true }) { Text(time.format(formatter), color = accent) }
                    }
                    state.cityLabel?.let { Text(it, fontSize = 12.sp, color = JewelTheme.globalColors.text.info) }
                    AnalogDial(
                        time,
                        state[ClockSettings.style],
                        entries.mapNotNull {
                            it.second
                                ?.toInstant()
                                ?.atZone(zone)
                                ?.toLocalTime()
                        },
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                    visible.chunked(2).forEach { row ->
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
        if (pickingTime) {
            val input = rememberTextFieldState(time.format(formatter))
            val chosen = parseClockTime(input.text.toString())
            Dialog(onDismissRequest = { pickingTime = false }) {
                Column(
                    Modifier
                        .width(300.dp)
                        .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
                        .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.clock_choose_time), fontWeight = FontWeight.SemiBold)
                    TextField(
                        input,
                        placeholder = { Text(stringResource(Res.string.clock_time_input_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DefaultButton(enabled = chosen != null, onClick = {
                            chosen?.let {
                                state.selectDate(date)
                                state.targetTime = Date.from(date.atTime(it).atZone(zone).toInstant())
                            }
                            pickingTime = false
                        }) { Text(stringResource(Res.string.clock_apply_time)) }
                        OutlinedButton(onClick = {
                            state.selectDate(java.time.LocalDate.now(zone))
                            pickingTime = false
                        }) { Text(stringResource(Res.string.clock_live_time)) }
                    }
                }
            }
        }
    }
}

internal object ClockSettings : WidgetSettings(ClockWidget.id) {
    val style by setting(0, SettingCodec(Int::toString, { it.toIntOrNull()?.takeIf { value -> value in 0..2 } }))
}

private val clockStyles = listOf(Res.string.clock_style_classic, Res.string.clock_style_minimal, Res.string.clock_style_roman)

internal fun parseClockTime(value: String): LocalTime? =
    runCatching {
        LocalTime.parse(
            value.trim(),
            DateTimeFormatter.ofPattern("H:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT),
        )
    }.getOrNull()

internal fun clockHandDegrees(time: LocalTime): Pair<Float, Float> =
    ((time.hour % 12 + time.minute / 60f) * 30f) to ((time.minute + time.second / 60f) * 6f)

@Composable
private fun AnalogDial(
    time: LocalTime,
    style: Int,
    markers: List<LocalTime>,
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
        if (style != 1) {
            (1..12).forEach { number ->
                val label = if (style == 2) ROMAN_NUMBERS[number - 1] else number.toString()
                val text = measurer.measure(label, TextStyle(color = foreground, fontSize = 12.sp))
                val at = point(number * 30f, radius * 0.73f)
                drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
            }
        }
        markers.forEachIndexed { index, marker ->
            val angle = clockHandDegrees(marker).first
            drawCircle(accent.copy(alpha = 0.8f), 3.dp.toPx(), point(angle, radius * (if (index % 2 == 0) 1.04f else 0.98f)))
        }
        val (hour, minute) = clockHandDegrees(time)
        drawLine(foreground, centre, point(hour, radius * 0.46f), 5.dp.toPx(), StrokeCap.Round)
        drawLine(accent, centre, point(minute, radius * 0.64f), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(accent, 5.dp.toPx(), centre)
    }
}

private val ROMAN_NUMBERS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
