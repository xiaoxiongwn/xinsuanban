package com.example.shiftchecker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shiftchecker.ui.theme.ShiftCheckerTheme
import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiftCheckerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShiftCheckerScreen()
                }
            }
        }
    }
}

data class ShiftType(val name: String, val workDays: Int, val restDays: Int)

val shiftTypes = listOf(
    ShiftType("上一休二", 1, 2),
    ShiftType("上一休三", 1, 3),
    ShiftType("上一休四", 1, 4)
)

private const val PREFS_NAME = "shift_prefs"
private const val KEY_BASE_DATE = "base_date"
private const val KEY_SHIFT_TYPE = "shift_type"

fun saveBaseDate(context: Context, date: LocalDate) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_BASE_DATE, date.toString()).apply()
}

fun loadBaseDate(context: Context): LocalDate {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_BASE_DATE, null)
    return saved?.let { LocalDate.parse(it) } ?: LocalDate.now()
}

fun saveShiftType(context: Context, shiftName: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_SHIFT_TYPE, shiftName).apply()
}

fun loadShiftType(context: Context): ShiftType {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_SHIFT_TYPE, null)
    return shiftTypes.find { it.name == saved } ?: shiftTypes[0]
}

// 上班/休息计算：以基准日期为第 1 个上班日循环。
// 用统一取模公式，保证基准日期前后都是连续不间断的同一循环。
fun isWorkingDay(shift: ShiftType, base: LocalDate, check: LocalDate): Boolean {
    val cycle = shift.workDays + shift.restDays
    val offset = ChronoUnit.DAYS.between(base, check).toInt()
    val dayInCycle = ((offset % cycle) + cycle) % cycle
    return dayInCycle < shift.workDays
}

fun findNextWorkDay(shift: ShiftType, base: LocalDate, from: LocalDate): LocalDate {
    var d = from.plusDays(1)
    while (!isWorkingDay(shift, base, d)) d = d.plusDays(1)
    return d
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun weekdayCn(date: LocalDate): String = weekdayNames[date.dayOfWeek.value - 1]

// 农历简短文案（节日 > 节气 > 初一显示月份 > 农历日）
fun lunarShort(date: LocalDate): String {
    val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
    val lunar = solar.lunar
    return when {
        lunar.festivals.isNotEmpty() -> lunar.festivals[0]
        solar.festivals.isNotEmpty() -> solar.festivals[0]
        lunar.jieQi.isNotEmpty() -> lunar.jieQi
        lunar.day == 1 -> lunar.monthInChinese + "月"
        else -> lunar.dayInChinese
    }
}

// 农历完整文案，用于详情卡片
fun lunarFull(date: LocalDate): String {
    val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
    val lunar = solar.lunar
    val base = "农历${lunar.monthInChinese}月${lunar.dayInChinese}"
    val extras = buildList {
        if (lunar.jieQi.isNotEmpty()) add(lunar.jieQi)
        addAll(lunar.festivals)
        addAll(solar.festivals)
    }
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" ")}"
}

@Composable
fun LunarDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var displayYear by remember { mutableStateOf(initialDate.year) }
    var displayMonth by remember { mutableStateOf(initialDate.monthValue) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 年月导航
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (displayMonth == 1) {
                            displayYear--; displayMonth = 12
                        } else displayMonth--
                    }) { Text("◀") }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${displayYear}年",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showYearPicker = true }
                        )
                        Text(
                            text = "${displayMonth}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showMonthPicker = true }
                        )
                    }

                    TextButton(onClick = {
                        if (displayMonth == 12) {
                            displayYear++; displayMonth = 1
                        } else displayMonth++
                    }) { Text("▶") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 年选择器
                if (showYearPicker) {
                    val years = (1970..2050).toList()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(years) { year ->
                            Text(
                                text = "${year}年",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        displayYear = year
                                        showYearPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                color = if (year == displayYear)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 月选择器
                if (showMonthPicker) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(12) { index ->
                            val month = index + 1
                            Box(
                                modifier = Modifier
                                    .clickable {
                                        displayMonth = month
                                        showMonthPicker = false
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${month}月",
                                    color = if (month == displayMonth)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (!showYearPicker && !showMonthPicker) {
                    // 星期标题
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == 0 || index == 6)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 日期网格
                    val firstDay = LocalDate.of(displayYear, displayMonth, 1)
                    val dayOfWeek = firstDay.dayOfWeek.value % 7
                    val daysInMonth = firstDay.lengthOfMonth()

                    Column {
                        var dayCounter = 1 - dayOfWeek
                        repeat(6) {
                            if (dayCounter > daysInMonth) return@repeat
                            Row(modifier = Modifier.fillMaxWidth()) {
                                repeat(7) { colIndex ->
                                    val currentDay = dayCounter
                                    dayCounter++
                                    if (currentDay in 1..daysInMonth) {
                                        val date = LocalDate.of(displayYear, displayMonth, currentDay)
                                        val isSelected = date == selectedDate

                                        val solar = Solar.fromYmd(displayYear, displayMonth, currentDay)
                                        val lunar = solar.lunar
                                        val lunarText = when {
                                            lunar.festivals.isNotEmpty() -> lunar.festivals[0]
                                            solar.festivals.isNotEmpty() -> solar.festivals[0]
                                            lunar.jieQi.isNotEmpty() -> lunar.jieQi
                                            lunar.day == 1 -> lunar.monthInChinese + "月"
                                            else -> lunar.dayInChinese
                                        }
                                        val isFestive = lunar.festivals.isNotEmpty()
                                                || solar.festivals.isNotEmpty()
                                                || lunar.jieQi.isNotEmpty()
                                        val isWeekend = colIndex == 0 || colIndex == 6

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(58.dp)
                                                .padding(2.dp)
                                                .background(
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable { selectedDate = date },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = currentDay.toString(),
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        isFestive -> MaterialTheme.colorScheme.error
                                                        isWeekend -> MaterialTheme.colorScheme.error
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = lunarText,
                                                    fontSize = 10.sp,
                                                    color = if (isFestive)
                                                        MaterialTheme.colorScheme.error
                                                    else
                                                        MaterialTheme.colorScheme.outline,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(selectedDate) }) { Text("确定") }
                }
            }
        }
    }
}

// 滑动日期条中的单个日期格
@Composable
fun DayCell(
    date: LocalDate,
    isWork: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val workColor = MaterialTheme.colorScheme.error
    val restColor = Color(0xFF2E7D32)
    Column(
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = weekdayCn(date),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${date.monthValue}/${date.dayOfMonth}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = lunarShort(date),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background((if (isWork) workColor else restColor).copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isWork) "上班" else "休息",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWork) workColor else restColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftCheckerScreen() {
    val context = LocalContext.current
    var selectedShift by remember { mutableStateOf(loadShiftType(context)) }
    var baseDate by remember { mutableStateOf(loadBaseDate(context)) }
    var checkDate by remember { mutableStateOf(LocalDate.now()) }
    var showShiftDropdown by remember { mutableStateOf(false) }
    var showBaseDatePicker by remember { mutableStateOf(false) }
    var showCheckDatePicker by remember { mutableStateOf(false) }

    val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")

    val result = isWorkingDay(selectedShift, baseDate, checkDate)
    val nextWorkDay = findNextWorkDay(selectedShift, baseDate, checkDate)

    // 滑动日期条范围：以今天为中心，前后各约两年
    val today = remember { LocalDate.now() }
    val rangeDays = 730L
    val startDate = remember { today.minusDays(rangeDays) }
    val totalDays = (rangeDays * 2 + 1).toInt()
    fun indexOfDate(d: LocalDate): Int = ChronoUnit.DAYS.between(startDate, d).toInt()

    val listState = rememberLazyListState()
    // 选中日期变化时，把它滚动到日期条中靠前位置（露出前面 2 天作上下文）
    LaunchedEffect(checkDate) {
        val idx = indexOfDate(checkDate)
        if (idx in 0 until totalDays) {
            listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    if (showBaseDatePicker) {
        LunarDatePickerDialog(
            initialDate = baseDate,
            onConfirm = {
                baseDate = it
                saveBaseDate(context, it)
                showBaseDatePicker = false
            },
            onDismiss = { showBaseDatePicker = false }
        )
    }

    if (showCheckDatePicker) {
        LunarDatePickerDialog(
            initialDate = checkDate,
            onConfirm = {
                checkDate = it
                showCheckDatePicker = false
            },
            onDismiss = { showCheckDatePicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "值班查询",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        ExposedDropdownMenuBox(
            expanded = showShiftDropdown,
            onExpandedChange = { showShiftDropdown = it }
        ) {
            OutlinedTextField(
                value = selectedShift.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("值班类型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showShiftDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = showShiftDropdown,
                onDismissRequest = { showShiftDropdown = false }
            ) {
                shiftTypes.forEach { shift ->
                    DropdownMenuItem(
                        text = { Text(shift.name) },
                        onClick = {
                            selectedShift = shift
                            saveShiftType(context, shift.name)
                            showShiftDropdown = false
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showBaseDatePicker = true }
        ) {
            OutlinedTextField(
                value = baseDate.format(formatter),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("基准日期（起始上班日）") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCheckDatePicker = true }
        ) {
            OutlinedTextField(
                value = checkDate.format(formatter),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("选择日期") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 左右滑动日期条
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← 左右滑动查看前后几天 →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                TextButton(
                    onClick = { checkDate = LocalDate.now() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("回到今天") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTextButton(label = "◀") { checkDate = checkDate.minusDays(1) }
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(totalDays) { index ->
                        val d = startDate.plusDays(index.toLong())
                        DayCell(
                            date = d,
                            isWork = isWorkingDay(selectedShift, baseDate, d),
                            isSelected = d == checkDate,
                            onClick = { checkDate = d }
                        )
                    }
                }
                IconTextButton(label = "▶") { checkDate = checkDate.plusDays(1) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (result)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${checkDate.format(formatter)} ${weekdayCn(checkDate)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = lunarFull(checkDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (result) "🔴 上班" else "🟢 休息",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (result)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!result) {
                    Text(
                        text = "下一个值班日期为${nextWorkDay.format(formatter)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Text(
            text = "计算规则：${selectedShift.name} = 上${selectedShift.workDays}天，休${selectedShift.restDays}天\n以基准日期为第1个上班日进行循环",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun IconTextButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(text = label, fontSize = 14.sp)
    }
}
