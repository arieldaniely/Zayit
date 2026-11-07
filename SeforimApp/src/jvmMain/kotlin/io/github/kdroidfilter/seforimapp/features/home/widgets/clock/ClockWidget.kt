package io.github.kdroidfilter.seforimapp.features.home.widgets.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.settings.SettingCodec
import io.github.kdroidfilter.seforimapp.earthwidget.computeZmanimTimes
import io.github.kdroidfilter.seforimapp.features.home.widgets.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object ClockWidget : HomeWidget {
    override val id = "analog_clock"
    override val title = Res.string.home_widget_clock
    override val defaultSpan = CellSpan(6, 6)
    override val minSpan = CellSpan(5, 5)
    override val maxSpan = CellSpan(8, 8)
    override val settings get() = ClockSettings

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

    @OptIn(ExperimentalFoundationApi::class)
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

        fun localTime(moment: Date): LocalTime = moment.toInstant().atZone(zone).toLocalTime()

        fun selectTime(selected: LocalTime) {
            state.selectDate(date)
            state.targetTime = Date.from(date.atTime(selected).atZone(zone).toInstant())
        }
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
        val markers = entries.mapNotNull { (_, moment) -> moment?.let(::localTime) }
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
                        val halfDayLabel = stringResource(Res.string.clock_switch_half_day)
                        Tooltip({ Text(halfDayLabel) }) {
                            Text(
                                time.format(formatter),
                                color = accent,
                                modifier =
                                    Modifier
                                        .clickable(onClickLabel = halfDayLabel) {
                                            selectTime(time.plusHours(12))
                                        }.padding(4.dp),
                            )
                        }
                        val liveLabel = stringResource(Res.string.clock_live_time)
                        Tooltip({ Text(liveLabel) }) {
                            IconButton(onClick = { state.selectDate(LocalDate.now(zone)) }, modifier = Modifier.size(24.dp)) {
                                Icon(AllIconsKeys.Actions.Refresh, contentDescription = liveLabel, modifier = Modifier.size(14.dp))
                            }
                        }
                        val settingsLabel = stringResource(Res.string.home_widgets_options)
                        Tooltip({ Text(settingsLabel) }) {
                            IconButton(onClick = { state.optionsOpen = ClockWidget }, modifier = Modifier.size(24.dp)) {
                                Icon(AllIconsKeys.General.Settings, contentDescription = settingsLabel, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    state.cityLabel?.let { Text(it, fontSize = 12.sp, color = JewelTheme.globalColors.text.info) }
                    AnalogDial(
                        time = time,
                        style = state[ClockSettings.style],
                        markers = markers,
                        onSelect = ::selectTime,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    visible.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, moment) ->
                                Column(
                                    Modifier.weight(1f).clickable(enabled = moment != null) {
                                        moment?.let { selectTime(localTime(it)) }
                                    },
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
}

internal object ClockSettings : WidgetSettings(ClockWidget.id) {
    val style by setting(0, SettingCodec(Int::toString, { it.toIntOrNull()?.takeIf { value -> value in 0..2 } }))
}

private val clockStyles = listOf(Res.string.clock_style_classic, Res.string.clock_style_minimal, Res.string.clock_style_roman)

internal fun clockHandDegrees(time: LocalTime): Pair<Float, Float> =
    ((time.hour % 12 + time.minute / 60f) * 30f) to ((time.minute + time.second / 60f) * 6f)

private const val DIAL_RADIUS = 0.46f
private const val FACE_RADIUS = 0.66f

private fun clockAngle(offset: Offset): Float = Math.toDegrees(atan2(offset.x, -offset.y).toDouble()).toFloat().mod(360f)

private fun angleDifference(from: Float, to: Float): Float = (to - from + 180f).mod(360f) - 180f

private fun dialPoint(degrees: Float, distance: Float): Offset {
    val radians = Math.toRadians((degrees - 90f).toDouble())
    return Offset(cos(radians).toFloat(), sin(radians).toFloat()) * distance
}

private fun distanceToHand(offset: Offset, degrees: Float, length: Float): Float {
    val direction = dialPoint(degrees, 1f)
    val along = (offset.x * direction.x + offset.y * direction.y).coerceIn(0f, length)
    return (offset - direction * along).getDistance()
}

internal fun timeFromDialOffset(
    offset: Offset,
    current: LocalTime,
): LocalTime {
    val minutes = (clockAngle(offset) * 2).roundToInt() % 720 + (current.hour / 12) * 720
    return LocalTime.of(minutes / 60, minutes % 60)
}

@Composable
private fun AnalogDial(
    time: LocalTime,
    style: Int,
    markers: List<LocalTime>,
    onSelect: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = JewelTheme.globalColors.text.normal
    val muted = JewelTheme.globalColors.text.info
    val accent = JewelTheme.globalColors.outlines.focused
    val measurer = rememberTextMeasurer()
    val currentTime by rememberUpdatedState(time)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val label = stringResource(Res.string.clock_time_description, time.format(DateTimeFormatter.ofPattern("HH:mm")))
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown()
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) * DIAL_RADIUS
                        val offset = first.position - centre
                        if (offset.getDistance() > radius || offset.getDistance() < 2.dp.toPx()) return@awaitEachGesture
                        val startTime = currentTime
                        val (hour, minute) = clockHandDegrees(startTime)
                        val face = radius * FACE_RADIUS
                        val hourDistance = distanceToHand(offset, hour, face * 0.47f)
                        val minuteDistance = distanceToHand(offset, minute, face * 0.69f)
                        val minutesPerTurn =
                            when {
                                minOf(hourDistance, minuteDistance) > 5.dp.toPx() -> null
                                minuteDistance < hourDistance -> 60
                                else -> 720
                            }
                        first.consume()
                        if (minutesPerTurn == null) currentOnSelect(timeFromDialOffset(offset, startTime))

                        var lastAngle = clockAngle(offset)
                        var turn = 0f
                        var moved = false
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                            val position = change.position - centre
                            if (change.pressed && change.position != change.previousPosition && position.getDistance() >= 2.dp.toPx()) {
                                moved = true
                                val angle = clockAngle(position)
                                turn += angleDifference(lastAngle, angle)
                                lastAngle = angle
                                val picked = if (minutesPerTurn == null) {
                                    timeFromDialOffset(position, startTime)
                                } else {
                                    startTime.plusMinutes((turn * minutesPerTurn / 360f).roundToInt().toLong())
                                }
                                currentOnSelect(picked)
                                change.consume()
                            }
                        } while (change.pressed)
                        if (!moved && minutesPerTurn != null) currentOnSelect(timeFromDialOffset(offset, startTime))
                    }
                },
        ) {
            val radius = minOf(size.width, size.height) * DIAL_RADIUS
            val face = radius * FACE_RADIUS
            val centre = center

            fun point(degrees: Float, distance: Float): Offset = centre + dialPoint(degrees, distance)

            markers.forEachIndexed { index, marker ->
                val angle = clockHandDegrees(marker).first
                drawCircle(accent.copy(alpha = 0.8f), 3.dp.toPx(), point(angle, radius * (if (index % 2 == 0) 1.04f else 0.98f)))
            }
            when (style) {
                1 -> drawCircle(accent.copy(alpha = 0.10f), face, centre)
                2 -> {
                    drawCircle(accent.copy(alpha = 0.13f), face, centre)
                    drawCircle(accent.copy(alpha = 0.75f), face, centre, style = Stroke(2.dp.toPx()))
                    drawCircle(accent.copy(alpha = 0.30f), face * 0.92f, centre, style = Stroke(1.dp.toPx()))
                }
                else -> drawCircle(muted.copy(alpha = 0.28f), face, centre, style = Stroke(1.dp.toPx()))
            }
            if (style == 0) {
                repeat(60) { tick ->
                    val major = tick % 5 == 0
                    drawLine(
                        if (major) foreground else muted.copy(alpha = 0.4f),
                        point(tick * 6f, face * if (major) 0.85f else 0.94f),
                        point(tick * 6f, face * 0.98f),
                        strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            val numberSize = (face.toDp().value / 5f).coerceIn(7f, 16f).sp
            (1..12).forEach { number ->
                val degrees = number * 30f
                if (style == 1) {
                    drawCircle(foreground, (if (number % 3 == 0) 2.5.dp else 1.dp).toPx(), point(degrees, face * 0.83f))
                } else {
                    val roman = style == 2
                    val text =
                        measurer.measure(
                            if (roman) ROMAN_NUMBERS[number - 1] else number.toString(),
                            TextStyle(
                                color = foreground,
                                fontSize = numberSize,
                                fontFamily = if (roman) FontFamily.Serif else FontFamily.Default,
                                fontWeight = if (roman) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                    val at = point(degrees, face * if (roman) 0.75f else 0.69f)
                    drawText(text, topLeft = at - Offset(text.size.width / 2f, text.size.height / 2f))
                }
            }
            val (hour, minute) = clockHandDegrees(time)
            val handWidth = if (style == 1) 4.dp else 3.dp
            drawLine(foreground, centre, point(hour, face * 0.47f), (handWidth + 1.dp).toPx(), StrokeCap.Round)
            drawLine(accent, centre, point(minute, face * 0.69f), handWidth.toPx(), StrokeCap.Round)
            drawCircle(accent, 3.5.dp.toPx(), centre)
        }
    }
}

private val ROMAN_NUMBERS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
