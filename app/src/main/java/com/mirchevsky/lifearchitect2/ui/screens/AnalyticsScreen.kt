@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mirchevsky.lifearchitect2.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mirchevsky.lifearchitect2.data.CalendarEvent
import com.mirchevsky.lifearchitect2.permissions.PermissionGateState
import com.mirchevsky.lifearchitect2.permissions.PermissionPrefs
import com.mirchevsky.lifearchitect2.permissions.resolvePermissionGateState
import com.mirchevsky.lifearchitect2.ui.theme.BrandGreen
import com.mirchevsky.lifearchitect2.ui.theme.BrandOrange
import com.mirchevsky.lifearchitect2.ui.viewmodel.AnalyticsViewModel
import com.mirchevsky.lifearchitect2.ui.viewmodel.DayStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Purple = Color(0xFF7B2FBE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionPrefs = remember(context) { PermissionPrefs(context) }

    var showCalRationale by rememberSaveable { mutableStateOf(false) }
    var showCalBlocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onAnalyticsScreenOpened()
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshCalendarPermission()
        val state = resolvePermissionGateState(
            context,
            Manifest.permission.READ_CALENDAR,
            permissionPrefs
        )
        when (state) {
            PermissionGateState.RequestableWithRationale -> showCalRationale = true
            PermissionGateState.PermanentlyDenied -> showCalBlocked = true
            else -> {}
        }
    }

    fun requestCalendarPermission() {
        val state = resolvePermissionGateState(
            context,
            Manifest.permission.READ_CALENDAR,
            permissionPrefs
        )
        when (state) {
            PermissionGateState.Granted -> viewModel.refreshCalendarPermission()
            PermissionGateState.RequestableFirstTime,
            PermissionGateState.RequestableWithRationale -> {
                permissionPrefs.markRequested(Manifest.permission.READ_CALENDAR)
                calendarPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            }
            PermissionGateState.PermanentlyDenied -> showCalBlocked = true
        }
    }

    if (showCalRationale) {
        AlertDialog(
            onDismissRequest = { showCalRationale = false },
            title = { Text("Calendar access needed") },
            text = {
                Text("Life Architect needs calendar access to show your device calendar events in the Analytics view.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalRationale = false
                        permissionPrefs.markRequested(Manifest.permission.READ_CALENDAR)
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalRationale = false }) {
                    Text("Not now")
                }
            }
        )
    }

    if (showCalBlocked) {
        AlertDialog(
            onDismissRequest = { showCalBlocked = false },
            title = { Text("Calendar access blocked") },
            text = {
                Text("You have permanently denied calendar access. To view device events, open Settings → Permissions → Calendar and enable it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalBlocked = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalBlocked = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCalendarPermission()
                viewModel.refreshDailyQuote()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
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
                        totalCalendarEvents = uiState.totalCalendarEvents,
                        hasCalendarPermission = uiState.hasCalendarPermission,
                        onGrantCalendarAccess = { requestCalendarPermission() }
                    )
                }
                item {
                    YearlyTaskCalendar(
                        monthlyTaskStatus = uiState.monthlyTaskStatus,
                        calendarEventDays = uiState.calendarEventDays,
                        selectedDay = uiState.selectedDay,
                        onDaySelected = { viewModel.selectDay(it) }
                    )
                }
                item {
                    DayDetailPanel(
                        selectedDay = uiState.selectedDay,
                        calendarEvents = uiState.calendarEventsForSelectedDay,
                        hasCalendarPermission = uiState.hasCalendarPermission,
                        hasCalendarWritePermission = uiState.hasCalendarWritePermission,
                        onEditCalendarEvent = { eventId, title, startMillis, endMillis, isAllDay ->
                            viewModel.updateCalendarEvent(
                                eventId = eventId,
                                title = title,
                                startMillis = startMillis,
                                endMillis = endMillis,
                                isAllDay = isAllDay
                            )
                        },
                        onDeleteCalendarEvent = { viewModel.deleteCalendarEvent(it) },
                        onRequestCalendarPermission = { requestCalendarPermission() }
                    )
                }
                item {
                    TodaysGlobalEventCard(
                        event = uiState.todayGlobalEvent,
                        tomorrowTitle = uiState.tomorrowEventTitle
                    )
                }
                item {
                    DailyQuoteCard(
                        quoteText = uiState.dailyQuote.quote,
                        personName = uiState.dailyQuote.person
                    )
                }
            }
        }
    }
}

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

@Composable
private fun StatRow(
    totalTasks: Int,
    totalCalendarEvents: Int,
    hasCalendarPermission: Boolean,
    onGrantCalendarAccess: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Total Tasks",
            value = totalTasks.toString(),
            modifier = Modifier
                .weight(1f)
                .height(88.dp)
        )
        StatCard(
            title = "Calendar Events",
            value = totalCalendarEvents.toString(),
            modifier = Modifier
                .weight(1f)
                .height(88.dp),
            locked = !hasCalendarPermission,
            onUnlock = onGrantCalendarAccess
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onUnlock: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            RoundedCornerShape(8.dp)
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Grant calendar access",
                        tint = Purple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { onUnlock?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Grant Access",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
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
}

@Composable
private fun TodaysGlobalEventCard(
    event: com.mirchevsky.lifearchitect2.data.GlobalEvent,
    tomorrowTitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = BrandOrange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Today's Global Event",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            if (tomorrowTitle.isNotBlank()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Tomorrow:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tomorrowTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun YearlyTaskCalendar(
    monthlyTaskStatus: Map<LocalDate, DayStatus>,
    calendarEventDays: Set<LocalDate> = emptySet(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        currentMonth = if (currentMonth.monthValue == 1) {
                            YearMonth.of(currentYear, 12)
                        } else {
                            currentMonth.minusMonths(1)
                        }
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
                        currentMonth = if (currentMonth.monthValue == 12) {
                            YearMonth.of(currentYear, 1)
                        } else {
                            currentMonth.plusMonths(1)
                        }
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
                calendarEventDays = calendarEventDays,
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
    calendarEventDays: Set<LocalDate> = emptySet(),
    selectedDay: LocalDate?,
    onDaySelected: (LocalDate) -> Unit
) {
    Column {
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
        val startOffset = firstDayOfMonth.dayOfWeek.value - 1
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

                        val hasCalEvent = date in calendarEventDays
                        if (status != null || hasCalEvent) {
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
                                if (hasCalEvent) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
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

@Composable
private fun DayDetailPanel(
    selectedDay: LocalDate,
    calendarEvents: List<CalendarEvent>,
    hasCalendarPermission: Boolean,
    hasCalendarWritePermission: Boolean,
    onEditCalendarEvent: (Long, String, Long, Long, Boolean) -> Unit,
    onDeleteCalendarEvent: (Long) -> Unit,
    onRequestCalendarPermission: () -> Unit
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
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Purple,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Device Calendar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (!hasCalendarPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                RoundedCornerShape(8.dp)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Calendar locked",
                            tint = Purple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calendar access required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestCalendarPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Grant Access",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (calendarEvents.isEmpty()) {
                Text(
                    text = "No calendar events for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    calendarEvents.forEach { event ->
                        CalendarEventRow(
                            event = event,
                            zone = zone,
                            canModify = hasCalendarWritePermission,
                            onEdit = onEditCalendarEvent,
                            onDelete = onDeleteCalendarEvent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(
    event: CalendarEvent,
    zone: ZoneId,
    canModify: Boolean,
    onEdit: (Long, String, Long, Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val startTime = if (event.isAllDay) {
        "All day"
    } else {
        LocalTime.ofInstant(
            Instant.ofEpochMilli(event.startMillis),
            zone
        ).format(timeFormatter)
    }

    val accentColor = if (event.color != 0) Color(event.color) else Purple
    var showDeleteDialog by remember(event.id) { mutableStateOf(false) }
    var showEditDialog by remember(event.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = startTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = { if (canModify) showEditDialog = true },
            enabled = canModify,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit calendar event",
                tint = if (canModify) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = { if (canModify) showDeleteDialog = true },
            enabled = canModify,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete calendar event",
                tint = if (canModify) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete event?") },
            text = { Text("This will remove the event from your device calendar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(event.id)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        EditCalendarEventDialog(
            event = event,
            onDismiss = { showEditDialog = false },
            onSave = { title, startMillis, endMillis, isAllDay ->
                onEdit(event.id, title, startMillis, endMillis, isAllDay)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCalendarEventDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, Boolean) -> Unit
) {
    var title by remember(event.id) { mutableStateOf(event.title) }
    var isAllDay by remember(event.id) { mutableStateOf(event.isAllDay) }
    var showDatePicker by remember(event.id) { mutableStateOf(false) }
    var showTimePicker by remember(event.id) { mutableStateOf(false) }
    var showInvalidTitle by remember(event.id) { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val startDateTime = remember(event.id) {
        Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDateTime()
    }
    var selectedDate by remember(event.id) { mutableStateOf(startDateTime.toLocalDate()) }
    var selectedHour by remember(event.id) { mutableStateOf(startDateTime.hour) }
    var selectedMinute by remember(event.id) { mutableStateOf(startDateTime.minute) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = selectedHour,
        initialMinute = selectedMinute,
        is24Hour = false
    )

    fun submit() {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            showInvalidTitle = true
            return
        }

        val newStart = if (isAllDay) {
            selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            LocalDateTime.of(selectedDate, LocalTime.of(selectedHour, selectedMinute))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }

        val duration = (event.endMillis - event.startMillis).coerceAtLeast(60_000L)
        val newEnd = if (isAllDay) {
            selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            newStart + duration
        }

        onSave(trimmedTitle, newStart, newEnd, isAllDay)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (showInvalidTitle && it.isNotBlank()) showInvalidTitle = false
                    },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = showInvalidTitle,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it }
                    )
                    Text("All day")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                    }
                    if (!isAllDay) {
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(
                                String.format(
                                    Locale.getDefault(),
                                    "%02d:%02d",
                                    selectedHour,
                                    selectedMinute
                                )
                            )
                        }
                    }
                }

                if (showInvalidTitle) {
                    Text(
                        text = "Title can't be empty.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select time", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    TimeInput(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                selectedHour = timePickerState.hour
                                selectedMinute = timePickerState.minute
                                showTimePicker = false
                            }
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyQuoteCard(
    quoteText: String,
    personName: String
) {
    if (quoteText.isBlank() || personName.isBlank()) return

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
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Daily Quote",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = quoteText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "— $personName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}