package com.veritas.reader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.VeritasPackStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class DayData(
    val dayOfMonth: Int,
    val dateKey: String,
    val timeMillis: Long
)

internal data class MonthData(
    val name: String,
    val days: List<DayData?>
)

@Composable
internal fun CalendarHeatMap(
    activeDateKeys: Set<String>,
    openedDateKeys: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val currentCal = remember { Calendar.getInstance() }
    val currentYear = currentCal.get(Calendar.YEAR)
    val currentMonth = currentCal.get(Calendar.MONTH) // 0..11

    var selectedMonthIndex by remember { mutableIntStateOf(currentMonth) }
    var isYearView by remember { mutableStateOf(false) }
    var selectedDayData by remember { mutableStateOf<DayData?>(null) }

    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }

    val months = remember {
        val list = mutableListOf<MonthData>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        for (m in 0..11) {
            cal.set(currentYear, m, 1)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 7 = Sat
            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val days = mutableListOf<DayData?>()
            repeat(firstDayOfWeek - 1) {
                days.add(null)
            }
            for (d in 1..maxDays) {
                cal.set(currentYear, m, d)
                val dateStr = sdf.format(cal.time)
                days.add(DayData(dayOfMonth = d, dateKey = dateStr, timeMillis = cal.timeInMillis))
            }
            while (days.size % 7 != 0) {
                days.add(null)
            }
            list.add(MonthData(name = monthNames[m], days = days))
        }
        list
    }

    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row with Title & View Mode Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Reading Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Segmented Pill Toggle: Month vs Year
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        Surface(
                            onClick = { isYearView = false },
                            shape = RoundedCornerShape(16.dp),
                            color = if (!isYearView) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                "Month",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!isYearView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            onClick = { isYearView = true },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isYearView) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                "Year",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isYearView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!isYearView) {
                // ==================== MONTH VIEW (DEFAULT) ====================
                val currentMonthData = months.getOrNull(selectedMonthIndex) ?: months[0]
                val validDays = currentMonthData.days.filterNotNull()
                val activeDaysInMonth = validDays.count { activeDateKeys.contains(it.dateKey) }
                val totalDaysInMonth = validDays.size
                val percentage = if (totalDaysInMonth > 0) (activeDaysInMonth * 100 / totalDaysInMonth) else 0

                // Month Navigator Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            selectedMonthIndex = (selectedMonthIndex - 1 + 12) % 12
                            selectedDayData = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Month")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${currentMonthData.name} $currentYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedMonthIndex != currentMonth) {
                            Text(
                                text = "Tap to jump to ${monthNames[currentMonth]}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    selectedMonthIndex = currentMonth
                                    selectedDayData = null
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            selectedMonthIndex = (selectedMonthIndex + 1) % 12
                            selectedDayData = null
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month")
                    }
                }

                // Month Summary Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (activeDaysInMonth > 0) "🔥 $activeDaysInMonth active days in ${currentMonthData.name}" else "No reading days yet in ${currentMonthData.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeDaysInMonth > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$percentage% consistency",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Weekday Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    weekdays.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Month Calendar Grid (Chunked Weeks)
                val chunkedWeeks = currentMonthData.days.chunked(7)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chunkedWeeks.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { day ->
                                if (day == null) {
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    val isActive = activeDateKeys.contains(day.dateKey)
                                    val isOpened = openedDateKeys.contains(day.dateKey)
                                    val isToday = day.dateKey == todayKey
                                    val isFuture = day.dateKey > todayKey
                                    val isSelected = selectedDayData?.dateKey == day.dateKey

                                    val cellBg = when {
                                        isActive -> MaterialTheme.colorScheme.primary
                                        isOpened -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        isFuture -> Color.Transparent
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                                    }

                                    val textColor = when {
                                        isActive -> MaterialTheme.colorScheme.onPrimary
                                        isOpened -> MaterialTheme.colorScheme.primary
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    val borderStroke = when {
                                        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                                        isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        isOpened && !isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                        isFuture -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                        !isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f))
                                        else -> null
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cellBg)
                                            .then(
                                                if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(8.dp))
                                                else Modifier
                                            )
                                            .clickable {
                                                if (!isFuture) {
                                                    selectedDayData = day
                                                    val formattedDate = SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date(day.timeMillis))
                                                    val msg = when {
                                                        isActive -> "Logged reading on $formattedDate! 📖"
                                                        isOpened -> "Vern opened on $formattedDate ⚡"
                                                        else -> "No activity logged on $formattedDate."
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${day.dayOfMonth}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isActive || isToday || isSelected) FontWeight.Black else FontWeight.Normal
                                            ),
                                            color = textColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Day Info Card
                val inspectedDay = selectedDayData ?: validDays.firstOrNull { it.dateKey == todayKey }
                if (inspectedDay != null && inspectedDay.dateKey.isNotBlank()) {
                    val isInspectedActive = activeDateKeys.contains(inspectedDay.dateKey)
                    val isInspectedOpened = openedDateKeys.contains(inspectedDay.dateKey)
                    val dateFormatted = remember(inspectedDay) {
                        SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).format(Date(inspectedDay.timeMillis))
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isInspectedActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (isInspectedActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (isInspectedActive) "🔥" else if (isInspectedOpened) "⚡" else "📅",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Column {
                                Text(
                                    text = dateFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        isInspectedActive -> "Active reading session logged"
                                        isInspectedOpened -> "Vern opened"
                                        else -> "No reading activity recorded"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // ==================== YEAR OVERVIEW GRID ====================
                // Quick Month Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    monthNames.forEachIndexed { mIdx, name ->
                        val activeInMonth = months[mIdx].days.filterNotNull().count { activeDateKeys.contains(it.dateKey) }
                        Surface(
                            onClick = {
                                selectedMonthIndex = mIdx
                                isYearView = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedMonthIndex == mIdx) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    name.take(3),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedMonthIndex == mIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (activeInMonth > 0) {
                                    Text(
                                        "$activeInMonth",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // Responsive 2-Column Year Matrix Grid
                val monthPairs = months.chunked(2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    monthPairs.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { month ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedMonthIndex = monthNames.indexOf(month.name).coerceAtLeast(0)
                                            isYearView = false
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = month.name.take(3),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        val miniWeeks = month.days.chunked(7)
                                        miniWeeks.forEach { week ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                week.forEach { day ->
                                                    if (day == null) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    } else {
                                                        val isActive = activeDateKeys.contains(day.dateKey)
                                                        val isOpened = openedDateKeys.contains(day.dateKey)
                                                        val isFuture = day.dateKey > todayKey
                                                        val miniBg = when {
                                                            isActive -> MaterialTheme.colorScheme.primary
                                                            isOpened -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                                            isFuture -> Color.Transparent
                                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .aspectRatio(1f)
                                                                .clip(RoundedCornerShape(3.dp))
                                                                .background(miniBg)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
