package io.github.kdroidfilter.seforimapp.hebrewcalendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.OutlinedSplitButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.jewel.ui.theme.segmentedControlButtonStyle
import seforimapp.hebrewcalendar.generated.resources.Res
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_mode_gregorian
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_mode_hebrew
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_next_month
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_prev_month
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_search_date_invalid
import seforimapp.hebrewcalendar.generated.resources.hebrewcalendar_search_date_placeholder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CALENDAR_MENU_WIDTH = 320.dp
private val CALENDAR_GRID_SPACING = 4.dp
private val TODAY_INDICATOR_SIZE = 4.dp

/**
 * A Hebrew/Gregorian calendar picker component.
 *
 * This composable displays a calendar with the ability to switch between
 * Hebrew and Gregorian calendar modes. It supports month navigation and
 * date selection.
 *
 * @param initialDate The initially selected date
 * @param onDateSelect Callback invoked when a date is selected
 * @param initialMode The initial calendar mode (Hebrew or Gregorian)
 * @param modifier Modifier for styling
 */
@Composable
fun HebrewCalendarPicker(
    initialDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: CalendarMode = CalendarMode.HEBREW,
) {
    var calendarMode by remember { mutableStateOf(initialMode) }
    var displayedMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    var displayedHebrewMonth by remember(initialDate) {
        mutableStateOf(hebrewYearMonthFromLocalDate(initialDate))
    }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var dateSearchValue by remember { mutableStateOf(TextFieldValue()) }
    var dateSearchError by remember { mutableStateOf(false) }
    var isDateSearchEditing by remember { mutableStateOf(false) }
    val dateSearchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isDateSearchEditing) {
        if (isDateSearchEditing) dateSearchFocusRequester.requestFocus()
    }

    val hebrewDateFormatter =
        remember {
            HebrewDateFormatter().apply {
                setHebrewFormat(true)
                setUseGershGershayim(false)
            }
        }
    val hebrewLocale = remember { Locale.forLanguageTag("he") }
    val monthTitleFormatter =
        remember {
            DateTimeFormatter.ofPattern("LLLL yyyy", hebrewLocale)
        }
    val weekDays =
        remember {
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )
        }

    val density = LocalDensity.current
    val cellSize =
        remember(density) {
            with(density) {
                val widthPx = CALENDAR_MENU_WIDTH.toPx()
                val spacingPx = CALENDAR_GRID_SPACING.toPx()
                val minCellPx = 28.dp.toPx()
                val cellPx = ((widthPx - (spacingPx * 6f)) / 7f).coerceAtLeast(minCellPx)
                cellPx.toDp()
            }
        }

    fun submitDateSearch() {
        val parsedDate = parseFlexibleDate(dateSearchValue.text, calendarMode)
        if (parsedDate == null) {
            dateSearchError = true
            dateSearchValue = TextFieldValue()
            return
        }
        dateSearchError = false
        isDateSearchEditing = false
        selectedDate = parsedDate
        displayedMonth = YearMonth.from(parsedDate)
        displayedHebrewMonth = hebrewYearMonthFromLocalDate(parsedDate)
        onDateSelect(parsedDate)
    }

    fun startDateSearch() {
        dateSearchValue = TextFieldValue()
        dateSearchError = false
        isDateSearchEditing = true
    }

    fun cancelDateSearch() {
        dateSearchError = false
        isDateSearchEditing = false
    }

    Column(
        modifier = modifier.width(CALENDAR_MENU_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedControl(
            buttons =
                listOf(
                    SegmentedControlButtonData(
                        selected = calendarMode == CalendarMode.HEBREW,
                        content = { Text(text = stringResource(Res.string.hebrewcalendar_mode_hebrew)) },
                        onSelect = {
                            calendarMode = CalendarMode.HEBREW
                            displayedHebrewMonth = hebrewYearMonthFromLocalDate(selectedDate)
                            cancelDateSearch()
                        },
                    ),
                    SegmentedControlButtonData(
                        selected = calendarMode == CalendarMode.GREGORIAN,
                        content = { Text(text = stringResource(Res.string.hebrewcalendar_mode_gregorian)) },
                        onSelect = {
                            calendarMode = CalendarMode.GREGORIAN
                            displayedMonth = YearMonth.from(selectedDate)
                            cancelDateSearch()
                        },
                    ),
                ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )


        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = {
                    cancelDateSearch()
                    when (calendarMode) {
                        CalendarMode.GREGORIAN -> displayedMonth = displayedMonth.minusMonths(1)
                        CalendarMode.HEBREW -> displayedHebrewMonth = previousHebrewYearMonth(displayedHebrewMonth)
                    }
                },
            ) {
                Icon(
                    key = AllIconsKeys.General.ChevronRight,
                    contentDescription = stringResource(Res.string.hebrewcalendar_prev_month),
                )
            }
            val monthShape = RoundedCornerShape(8.dp)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(JewelTheme.globalColors.toolwindowBackground, monthShape)
                        .border(
                            1.dp,
                            if (dateSearchError) {
                                JewelTheme.globalColors.borders.focused
                            } else {
                                JewelTheme.globalColors.borders.disabled
                            },
                            monthShape,
                        )
                        .clickable(enabled = !isDateSearchEditing, onClick = ::startDateSearch)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isDateSearchEditing) {
                    BasicTextField(
                        value = dateSearchValue,
                        onValueChange = {
                            dateSearchValue = it
                            dateSearchError = false
                        },
                        singleLine = true,
                        textStyle =
                            JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.normal,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            ),
                        cursorBrush = SolidColor(JewelTheme.globalColors.outlines.focused),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(dateSearchFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.Enter, Key.NumPadEnter -> {
                                            submitDateSearch()
                                            true
                                        }

                                        Key.Escape -> {
                                            cancelDateSearch()
                                            true
                                        }

                                        else -> false
                                    }
                                },
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (dateSearchValue.text.isEmpty()) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (dateSearchError) {
                                                    Res.string.hebrewcalendar_search_date_invalid
                                                } else {
                                                    Res.string.hebrewcalendar_search_date_placeholder
                                                },
                                            ),
                                        color = JewelTheme.globalColors.text.disabled,
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                } else {
                    Text(
                        text =
                            when (calendarMode) {
                                CalendarMode.GREGORIAN -> displayedMonth.format(monthTitleFormatter)
                                CalendarMode.HEBREW -> formatHebrewMonthTitle(displayedHebrewMonth, hebrewDateFormatter)
                            },
                        textAlign = TextAlign.Center,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            IconButton(
                onClick = {
                    cancelDateSearch()
                    when (calendarMode) {
                        CalendarMode.GREGORIAN -> displayedMonth = displayedMonth.plusMonths(1)
                        CalendarMode.HEBREW -> displayedHebrewMonth = nextHebrewYearMonth(displayedHebrewMonth)
                    }
                },
            ) {
                Icon(
                    key = AllIconsKeys.General.ChevronLeft,
                    contentDescription = stringResource(Res.string.hebrewcalendar_next_month),
                )
            }
        }

        val weekHeaderShape = RoundedCornerShape(8.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(JewelTheme.globalColors.toolwindowBackground, weekHeaderShape)
                        .border(1.dp, JewelTheme.globalColors.borders.disabled, weekHeaderShape)
                        .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CALENDAR_GRID_SPACING),
                ) {
                    for (dayOfWeek in weekDays) {
                        Text(
                            text = dayOfWeek.getDisplayName(TextStyle.NARROW, hebrewLocale),
                            modifier = Modifier.width(cellSize),
                            textAlign = TextAlign.Center,
                            style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }

            val onSelectDate: (LocalDate) -> Unit = { date ->
                selectedDate = date
                onDateSelect(date)
            }

            when (calendarMode) {
                CalendarMode.GREGORIAN ->
                    MonthGrid(
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        onDateSelect = onSelectDate,
                        cellSize = cellSize,
                        spacing = CALENDAR_GRID_SPACING,
                    )
                CalendarMode.HEBREW ->
                    HebrewMonthGrid(
                        displayedMonth = displayedHebrewMonth,
                        selectedDate = selectedDate,
                        onDateSelect = onSelectDate,
                        formatter = hebrewDateFormatter,
                        cellSize = cellSize,
                        spacing = CALENDAR_GRID_SPACING,
                    )
            }
        }
    }
}

/**
 * A split button that displays a date label and opens a calendar picker popup.
 *
 * @param label The label to display on the button
 * @param selectedDate The currently selected date
 * @param onDateSelect Callback invoked when a date is selected
 * @param initialMode The initial calendar mode when popup opens
 * @param menuStyle The menu styling used for the popup content
 * @param modifier Modifier for styling
 */
@Composable
fun DateSelectionSplitButton(
    label: String,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialMode: CalendarMode = CalendarMode.HEBREW,
    menuStyle: MenuStyle = JewelTheme.menuStyle,
) {
    Box(modifier = modifier) {
        OutlinedSplitButton(
            onClick = {},
            secondaryOnClick = {},
            popupModifier = Modifier.width(CALENDAR_MENU_WIDTH),
            maxPopupWidth = CALENDAR_MENU_WIDTH,
            menuStyle = menuStyle,
            content = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            },
            menuContent = {
                passiveItem {
                    // Access menu controller inside menu content where it's provided
                    val menuController = LocalMenuController.current
                    val inputModeManager = LocalInputModeManager.current
                    IntUiTheme(isDark = menuStyle.isDark) {
                        HebrewCalendarPicker(
                            initialDate = selectedDate,
                            onDateSelect = { date ->
                                onDateSelect(date)
                                menuController.closeAll(inputModeManager.inputMode, true)
                            },
                            initialMode = initialMode,
                        )
                    }
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun MonthGrid(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    cellSize: Dp,
    spacing: Dp,
) {
    val weeks = remember(displayedMonth) { buildMonthGrid(displayedMonth) }
    var hoveredDate by remember(displayedMonth) { mutableStateOf<LocalDate?>(null) }
    val cellShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal
    val todayDate = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (week in weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (day in week) {
                    if (day == null) {
                        Spacer(modifier = Modifier.size(cellSize))
                        continue
                    }

                    val isSelected = day == selectedDate
                    val isHovered = hoveredDate == day
                    val isToday = day == todayDate
                    val bgBrush: Brush =
                        when {
                            isSelected -> selectedBg
                            isHovered -> hoverBg
                            else -> SolidColor(Color.Transparent)
                        }
                    val textColor = if (isSelected) selectedTextColor else normalTextColor
                    val todayIndicatorColor = if (isSelected) selectedTextColor else JewelTheme.globalColors.text.info
                    val border =
                        when {
                            isSelected -> JewelTheme.globalColors.borders.focused
                            isHovered -> JewelTheme.globalColors.borders.normal
                            else -> Color.Transparent
                        }

                    Box(
                        modifier =
                            Modifier
                                .size(cellSize)
                                .border(1.dp, border, cellShape)
                                .background(bgBrush, cellShape)
                                .onPointerEvent(PointerEventType.Enter) { hoveredDate = day }
                                .onPointerEvent(PointerEventType.Exit) { if (hoveredDate == day) hoveredDate = null }
                                .clickable { onDateSelect(day) }
                                .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = day.dayOfMonth.toString(), color = textColor)
                        if (isToday) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 1.dp)
                                        .size(TODAY_INDICATOR_SIZE)
                                        .background(todayIndicatorColor, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HebrewMonthGrid(
    displayedMonth: HebrewYearMonth,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    formatter: HebrewDateFormatter,
    cellSize: Dp,
    spacing: Dp,
) {
    val weeks = remember(displayedMonth) { buildHebrewMonthGrid(displayedMonth, formatter) }
    var hoveredDate by remember(displayedMonth) { mutableStateOf<LocalDate?>(null) }
    val cellShape = RoundedCornerShape(6.dp)
    val selectedBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundSelected
    val hoverBg = JewelTheme.segmentedControlButtonStyle.colors.backgroundHovered
    val selectedTextColor = JewelTheme.globalColors.text.selected
    val normalTextColor = JewelTheme.globalColors.text.normal
    val todayDate = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (week in weeks) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (day in week) {
                    if (day == null) {
                        Spacer(modifier = Modifier.size(cellSize))
                        continue
                    }

                    val isSelected = day.localDate == selectedDate
                    val isHovered = hoveredDate == day.localDate
                    val isToday = day.localDate == todayDate
                    val bgBrush: Brush =
                        when {
                            isSelected -> selectedBg
                            isHovered -> hoverBg
                            else -> SolidColor(Color.Transparent)
                        }
                    val textColor = if (isSelected) selectedTextColor else normalTextColor
                    val todayIndicatorColor = if (isSelected) selectedTextColor else JewelTheme.globalColors.text.info
                    val border =
                        when {
                            isSelected -> JewelTheme.globalColors.borders.focused
                            isHovered -> JewelTheme.globalColors.borders.normal
                            else -> Color.Transparent
                        }

                    Box(
                        modifier =
                            Modifier
                                .size(cellSize)
                                .border(1.dp, border, cellShape)
                                .background(bgBrush, cellShape)
                                .onPointerEvent(PointerEventType.Enter) { hoveredDate = day.localDate }
                                .onPointerEvent(PointerEventType.Exit) { if (hoveredDate == day.localDate) hoveredDate = null }
                                .clickable { onDateSelect(day.localDate) }
                                .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = day.label, color = textColor)
                        if (isToday) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 1.dp)
                                        .size(TODAY_INDICATOR_SIZE)
                                        .background(todayIndicatorColor, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
