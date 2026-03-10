package com.mirchevsky.lifearchitect2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.mirchevsky.lifearchitect2.ui.theme.BrandGreen
import com.mirchevsky.lifearchitect2.ui.theme.BrandOrange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.ui.viewmodel.AnalyticsViewModel
import com.mirchevsky.lifearchitect2.ui.viewmodel.DayStatus
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    Box(modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    UserProfileCard(
                        level = uiState.level,
                        xp = uiState.xp,
                        dailyStreak = uiState.dailyStreak
                    )
                }
                item {
                    StatRow(
                        totalTasks = uiState.totalTasksCompleted,
                        totalCalendarEvents = uiState.totalCalendarEvents
                    )
                }
                item {
                    YearlyTaskCalendar(
                        monthlyTaskStatus = uiState.monthlyTaskStatus,
                        selectedDay = uiState.selectedDay,
                        onDaySelected = { viewModel.selectDay(it) }
                    )
                }
                if (!uiState.isLoading) {
                    item {
                        DayDetailPanel(
                            selectedDay = uiState.selectedDay,
                            tasks = uiState.tasksForSelectedDay,
                            onCompleteTask = { viewModel.completeTask(it) }
                        )
                    }
                }
                item { CompletionChart(data = uiState.dailyCompletions, anchorDay = uiState.selectedDay) }
                item { TipOfTheVisit() }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// User Profile Card
// ---------------------------------------------------------------------------

private fun xpForNextLevel(level: Int): Int = 100 * level

@Composable
private fun UserProfileCard(
    level: Int,
    xp: Int,
    dailyStreak: Int
) {
    val xpNeeded = xpForNextLevel(level)
    val progress = (xp.toFloat() / xpNeeded).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$xp / $xpNeeded XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Streak badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = if (dailyStreak > 0) BrandOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "$dailyStreak Days",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stat Row
// ---------------------------------------------------------------------------
@Composable
private fun StatRow(totalTasks: Int, totalCalendarEvents: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(title = "Total Tasks", value = totalTasks.toString(), modifier = Modifier.weight(1f))
        StatCard(title = "Calendar Events", value = totalCalendarEvents.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Completion Chart — custom scrollable bar chart, no external library
// ---------------------------------------------------------------------------
@Composable
private fun CompletionChart(data: Map<LocalDate, Int>, anchorDay: LocalDate = LocalDate.now()) {
    // Build a 30-day window ending on anchorDay (anchorDay is the last/rightmost bar)
    val windowDays = (29 downTo 0).map { anchorDay.minusDays(it.toLong()) }
    val sortedEntries = windowDays.map { day -> day to (data[day] ?: 0) }
    val maxCount = sortedEntries.maxOfOrNull { it.second } ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tasks Completed in the Last 30 Days",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (maxCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No completed tasks yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val barColor = MaterialTheme.colorScheme.primary
                val chartHeight = 120.dp
                val barWidth = 18.dp
                val barGap = 8.dp
                val scrollState = rememberScrollState()
                val scope = rememberCoroutineScope()

                // Scroll to the rightmost bar (anchorDay) whenever anchorDay changes
                LaunchedEffect(anchorDay) {
                    scrollState.scrollTo(scrollState.maxValue)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(barGap)
                ) {
                    sortedEntries.forEach { (date, count) ->
                        val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                        val filledHeight = (chartHeight * fraction).coerceAtLeast(if (count > 0) 4.dp else 0.dp)

                        val badgeSize = 20.dp
                        val badgeColor = MaterialTheme.colorScheme.inverseSurface
                        val badgeTextColor = MaterialTheme.colorScheme.inverseOnSurface

                        // Layout (top-to-bottom inside fixed chartHeight):
                        //   [empty space]  [badge (20dp)]  [bar]  [4dp gap]  [day label]
                        // Badge is always in the layout flow, so it never needs to overflow.
                        val spaceAboveBadge = (chartHeight - filledHeight - badgeSize).coerceAtLeast(0.dp)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(barWidth)
                        ) {
                            // Push badge+bar to the bottom of the chart area
                            Spacer(modifier = Modifier.height(spaceAboveBadge))

                            // Badge — shown only when count > 0, same height reserved always
                            Box(
                                modifier = Modifier.size(badgeSize),
                                contentAlignment = Alignment.Center
                            ) {
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(badgeSize)
                                            .clip(CircleShape)
                                            .background(badgeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeTextColor,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Bar sits directly below the badge
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height(filledHeight)
                                    .clip(CircleShape)
                                    .background(barColor)
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("d")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.width(barWidth)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Draggable scrollbar track + thumb
                val thumbColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(trackColor)
                        .drawBehind {
                            val maxScroll = scrollState.maxValue.toFloat()
                            val current = scrollState.value.toFloat()
                            if (maxScroll > 0f) {
                                val thumbFraction = size.width / (size.width + maxScroll)
                                val thumbWidth = size.width * thumbFraction
                                val thumbX = (current / maxScroll) * (size.width - thumbWidth)
                                drawRoundRect(
                                    color = thumbColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(thumbX, 0f),
                                    size = androidx.compose.ui.geometry.Size(thumbWidth, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                                )
                            }
                        }
                        .pointerInput(scrollState.maxValue) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val maxScroll = scrollState.maxValue.toFloat()
                                if (maxScroll > 0f) {
                                    val thumbFraction = size.width / (size.width + maxScroll)
                                    val scrollDelta = (dragAmount / (size.width * thumbFraction)) * maxScroll
                                    scope.launch {
                                        scrollState.scrollTo(
                                            (scrollState.value + scrollDelta.toInt()).coerceIn(0, scrollState.maxValue)
                                        )
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Monthly Task Calendar — one card, navigates through all months of the year
// ---------------------------------------------------------------------------
@Composable
private fun YearlyTaskCalendar(
    monthlyTaskStatus: Map<LocalDate, DayStatus>,
    selectedDay: LocalDate?,
    onDaySelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val currentYear = today.year

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Month navigation header — constrained to current year, wraps around
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        currentMonth = if (currentMonth.monthValue == 1)
                            YearMonth.of(currentYear, 12)
                        else
                            currentMonth.minusMonths(1)
                    }
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = {
                        currentMonth = if (currentMonth.monthValue == 12)
                            YearMonth.of(currentYear, 1)
                        else
                            currentMonth.plusMonths(1)
                    }
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            MonthBlock(
                month = currentMonth,
                today = today,
                monthlyTaskStatus = monthlyTaskStatus,
                selectedDay = selectedDay,
                onDaySelected = onDaySelected
            )
        }
    }
}

@Composable
private fun MonthBlock(
    month: YearMonth,
    today: LocalDate,
    monthlyTaskStatus: Map<LocalDate, DayStatus>,
    selectedDay: LocalDate?,
    onDaySelected: (LocalDate) -> Unit
) {
    Column {

        // Day-of-week headers (Mon–Sun)
        val dayHeaders = DayOfWeek.values().map {
            it.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val firstDayOfMonth = month.atDay(1)
        val startOffset = firstDayOfMonth.dayOfWeek.value - 1 // Monday = 0
        val daysInMonth = month.lengthOfMonth()
        val totalCells = startOffset + daysInMonth
        val gridSize = if (totalCells % 7 == 0) totalCells else totalCells + (7 - totalCells % 7)

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(((gridSize / 7) * 48).dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(gridSize) { index ->
                if (index < startOffset || index >= startOffset + daysInMonth) {
                    Box(modifier = Modifier.size(36.dp))
                } else {
                    val dayNumber = index - startOffset + 1
                    val date = month.atDay(dayNumber)
                    val isToday = date == today
                    val isSelected = date == selectedDay
                    val status = monthlyTaskStatus[date]

                    Column(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDaySelected(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .then(
                                    when {
                                        isSelected -> Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                        isToday -> Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        if (status != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                if (status == DayStatus.COMPLETED || status == DayStatus.BOTH) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(BrandGreen)
                                    )
                                }
                                if (status == DayStatus.PENDING || status == DayStatus.BOTH) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
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

// ---------------------------------------------------------------------------
// Day Detail Panel — replaces Due Date Performance
// Shows pending tasks for the selected calendar day, completable in-place.
// ---------------------------------------------------------------------------
@Composable
private fun DayDetailPanel(
    selectedDay: LocalDate,
    tasks: List<TaskEntity>,
    onCompleteTask: (TaskEntity) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    val zone = ZoneId.systemDefault()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = selectedDay.format(formatter),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks scheduled for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasks.forEach { task ->
                        val isCompleted = task.isCompleted
                        val dueDate: LocalDate? = task.dueDate?.let {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        }
                        val isOverdue = !isCompleted && dueDate != null && dueDate.isBefore(LocalDate.now())

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!isCompleted) Modifier.clickable { onCompleteTask(task) }
                                        else Modifier
                                    )
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isCompleted,
                                    onCheckedChange = if (!isCompleted) { _ -> onCompleteTask(task) } else null,
                                    colors = CheckboxDefaults.colors(
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (isCompleted)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    if (dueDate != null) {
                                        val label = dueDate.format(DateTimeFormatter.ofPattern("MMM d"))
                                        Text(
                                            text = when {
                                                isCompleted -> "Completed"
                                                isOverdue -> "Overdue — $label"
                                                else -> "Due $label"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when {
                                                isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                isOverdue -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                if (dueDate != null && !isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = if (isOverdue) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(start = 4.dp)
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

// ---------------------------------------------------------------------------
// Tip of the Visit
// ---------------------------------------------------------------------------
private val APP_TIPS = listOf(
    "Assign a difficulty to every task — Easy, Medium, or Hard tasks award different XP amounts, so harder tasks are worth the extra push.",
    "Your daily streak resets if you skip a full day without completing any task. Even finishing one small Easy task keeps the streak alive.",
    "Tap the calendar icon on any task to open it directly in your device's calendar app and set a reminder.",
    "The Analytics tab shows your last 30 days of completions. Tap any day on the calendar to see exactly which tasks were scheduled for that day.",
    "Completing your first three tasks of the day earns a streak bonus — XP rewards drop after that, so front-load your most important work.",
    "Tasks completed after their due date are marked Overdue in the day-detail panel. Use this to spot recurring scheduling blind spots.",
    "A 7-day streak awards a bonus XP payout. Reaching a 30-day streak unlocks an even larger milestone reward.",
    "Repeating the same task title within 24 hours earns only 25% of the normal XP — vary your tasks to maximise your gains.",
    "Every task has a 20% chance of triggering a Critical Hit, which multiplies your XP reward by up to 3×. You cannot predict it, but you can increase your chances by completing more unique tasks.",
    "The green dot on a calendar day means all tasks for that day are done. A red dot means at least one task is still pending — a quick visual cue for what needs attention."
)

@Composable
private fun TipOfTheVisit() {
    // Pick a new random tip each time this composable enters composition (i.e. each tab visit)
    val tip = remember { APP_TIPS.random() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EmojiObjects,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Tip",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
