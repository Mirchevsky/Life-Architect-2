package com.mirchevsky.lifearchitect2.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirchevsky.lifearchitect2.data.AppRepository
import com.mirchevsky.lifearchitect2.data.CalendarEvent
import com.mirchevsky.lifearchitect2.data.DailyQuote
import com.mirchevsky.lifearchitect2.data.DailyQuoteEngine
import com.mirchevsky.lifearchitect2.data.DeviceCalendarRepository
import com.mirchevsky.lifearchitect2.data.GlobalEvent
import com.mirchevsky.lifearchitect2.data.GlobalEventsEngine
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import com.mirchevsky.lifearchitect2.domain.TaskDifficulty
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class DayStatus {
    PENDING,
    COMPLETED,
    BOTH
}

data class AnalyticsUiState(
    // User profile
    val userName: String = "Adventurer",
    val level: Int = 1,
    val xp: Int = 0,
    val dailyStreak: Int = 0,

    // Stats
    val totalTasksCompleted: Int = 0,
    val totalCalendarEvents: Int = 0,
    val dailyCompletions: Map<LocalDate, Int> = emptyMap(),
    val monthlyTaskStatus: Map<LocalDate, DayStatus> = emptyMap(),
    val onTimeCompletions: Int = 0,
    val overdueCompletions: Int = 0,
    val bestDay: Pair<LocalDate, Int>? = null,
    val bestWeek: Pair<LocalDate, Int>? = null,

    // Day-detail panel
    val selectedDay: LocalDate = LocalDate.now(),
    val tasksForSelectedDay: List<TaskEntity> = emptyList(),

    // Device calendar
    val calendarEventsForSelectedDay: List<CalendarEvent> = emptyList(),
    val calendarEventDays: Set<LocalDate> = emptySet(),
    val totalDeviceCalendarEvents: Int = 0,

    val dailyQuote: DailyQuote = DailyQuote(person = "", quote = ""),
    val todayGlobalEvent: GlobalEvent = GlobalEvent(title = "", description = ""),
    val tomorrowEventTitle: String = "",
    val hasCalendarPermission: Boolean = false,
    val hasCalendarWritePermission: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repository: AppRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState

    // Separate flow for the selected day so any change triggers a reactive recompute
    private val _selectedDay = MutableStateFlow(LocalDate.now())

    // Tracks whether READ_CALENDAR permission is currently granted.
    // Refreshed by the UI on resume and after any permission result.
    private val _hasCalendarPermission = MutableStateFlow(checkCalendarReadPermission())
    private val _hasCalendarWritePermission = MutableStateFlow(checkCalendarWritePermission())

    private val deviceCalendarRepo = DeviceCalendarRepository(appContext)
    private val dailyQuoteEngine = DailyQuoteEngine(appContext)
    private val globalEventsEngine = GlobalEventsEngine()

    init {
        refreshDailyQuote()
        refreshGlobalEvent()

        // ── Task-based state ────────────────────────────────────────────────
        viewModelScope.launch {
            combine(
                repository.observeUser("local_user"),
                repository.getCompletedTasks(),
                repository.observePendingTasksForUser("local_user"),
                _selectedDay
            ) { user, completedTasks, pendingTasks, selectedDay ->
                if (user == null) return@combine AnalyticsUiState(isLoading = false)

                val zone = ZoneId.systemDefault()
                val now = LocalDate.now()
                val weekFields = WeekFields.of(Locale.getDefault())

                val tasksByDate = completedTasks
                    .filter { it.completedAt != null }
                    .groupBy { task ->
                        Instant.ofEpochMilli(task.completedAt!!)
                            .atZone(zone)
                            .toLocalDate()
                    }

                val dailyCompletions = (0..29).associate { offset ->
                    val day = now.minusDays(offset.toLong())
                    day to (tasksByDate[day]?.size ?: 0)
                }

                val bestDay = tasksByDate
                    .maxByOrNull { it.value.size }
                    ?.let { it.key to it.value.size }

                val weeklyCompletions = completedTasks
                    .filter { it.completedAt != null }
                    .groupBy { task ->
                        val date = Instant.ofEpochMilli(task.completedAt!!)
                            .atZone(zone)
                            .toLocalDate()
                        date.with(weekFields.dayOfWeek(), 1)
                    }

                val bestWeek = weeklyCompletions
                    .maxByOrNull { it.value.size }
                    ?.let { it.key to it.value.size }

                val onTime = completedTasks.count {
                    it.dueDate != null &&
                            it.completedAt != null &&
                            it.completedAt <= it.dueDate
                }

                val overdue = completedTasks.count {
                    it.dueDate != null &&
                            it.completedAt != null &&
                            it.completedAt > it.dueDate
                }

                val completedDays = completedTasks
                    .filter { it.dueDate != null }
                    .map { Instant.ofEpochMilli(it.dueDate!!).atZone(zone).toLocalDate() }
                    .toSet()

                val pendingDays = pendingTasks
                    .filter { it.dueDate != null }
                    .map { Instant.ofEpochMilli(it.dueDate!!).atZone(zone).toLocalDate() }
                    .toSet()

                val totalCalendarEvents =
                    completedTasks.count { it.dueDate != null } +
                            pendingTasks.count { it.dueDate != null }

                val allDays = completedDays + pendingDays

                val monthlyStatus = allDays.associateWith { date ->
                    val hasCompleted = completedDays.contains(date)
                    val hasPending = pendingDays.contains(date)
                    when {
                        hasCompleted && hasPending -> DayStatus.BOTH
                        hasCompleted -> DayStatus.COMPLETED
                        else -> DayStatus.PENDING
                    }
                }

                val resolvedDay = if (allDays.contains(selectedDay)) {
                    selectedDay
                } else if (selectedDay == now) {
                    allDays.filter { it >= now }.minOrNull()
                        ?: allDays.maxOrNull()
                        ?: selectedDay
                } else {
                    selectedDay
                }

                val pendingForDay = pendingTasks.filter { task ->
                    task.dueDate != null &&
                            Instant.ofEpochMilli(task.dueDate)
                                .atZone(zone)
                                .toLocalDate() == resolvedDay
                }

                val completedForDay = completedTasks.filter { task ->
                    task.dueDate != null &&
                            Instant.ofEpochMilli(task.dueDate!!)
                                .atZone(zone)
                                .toLocalDate() == resolvedDay
                }

                val tasksForDay = pendingForDay + completedForDay

                // Preserve existing calendar events, permissions, daily quote,
                // and global event state while task state updates.
                val current = _uiState.value

                AnalyticsUiState(
                    userName = user.name,
                    level = user.level,
                    xp = user.xp,
                    dailyStreak = user.dailyStreak,
                    totalTasksCompleted = completedTasks.size + pendingTasks.size,
                    totalCalendarEvents = current.totalDeviceCalendarEvents,
                    dailyCompletions = dailyCompletions,
                    monthlyTaskStatus = monthlyStatus,
                    onTimeCompletions = onTime,
                    overdueCompletions = overdue,
                    bestDay = bestDay,
                    bestWeek = bestWeek,
                    selectedDay = resolvedDay,
                    tasksForSelectedDay = tasksForDay,
                    calendarEventsForSelectedDay = current.calendarEventsForSelectedDay,
                    calendarEventDays = current.calendarEventDays,
                    totalDeviceCalendarEvents = current.totalDeviceCalendarEvents,
                    dailyQuote = current.dailyQuote,
                    todayGlobalEvent = current.todayGlobalEvent,
                    tomorrowEventTitle = current.tomorrowEventTitle,
                    hasCalendarPermission = current.hasCalendarPermission,
                    hasCalendarWritePermission = current.hasCalendarWritePermission,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // ── Device calendar events for selected day ──────────────────────────
        viewModelScope.launch {
            combine(_selectedDay, _hasCalendarPermission) { day, hasPerm ->
                day to hasPerm
            }.flatMapLatest { (day, hasPerm) ->
                if (hasPerm) deviceCalendarRepo.observeEventsForDate(day) else flowOf(emptyList())
            }.collect { events ->
                _uiState.value = _uiState.value.copy(
                    calendarEventsForSelectedDay = events,
                    hasCalendarPermission = _hasCalendarPermission.value,
                    hasCalendarWritePermission = _hasCalendarWritePermission.value
                )
            }
        }

        // ── Calendar event days for month grid dots ───────────────────────────
        viewModelScope.launch {
            _hasCalendarPermission.flatMapLatest { hasPerm ->
                if (hasPerm) {
                    deviceCalendarRepo.observeEventDaysForMonth(YearMonth.now())
                } else {
                    flowOf(emptySet())
                }
            }.collect { days ->
                _uiState.value = _uiState.value.copy(calendarEventDays = days)
            }
        }

        // ── Total device calendar event count ────────────────────────────────
        viewModelScope.launch {
            _hasCalendarPermission.flatMapLatest { hasPerm ->
                if (hasPerm) deviceCalendarRepo.observeTotalEventCount() else flowOf(0)
            }.collect { count ->
                _uiState.value = _uiState.value.copy(
                    totalDeviceCalendarEvents = count,
                    totalCalendarEvents = count
                )
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Called when the user taps a day in the calendar. */
    fun selectDay(date: LocalDate) {
        _selectedDay.value = date
    }

    /**
     * Called by the UI after a permission result or on screen resume.
     * Re-evaluates READ_CALENDAR permission and triggers a calendar reload if needed.
     */
    fun refreshCalendarPermission() {
        val readGranted = checkCalendarReadPermission()
        if (_hasCalendarPermission.value != readGranted) {
            _hasCalendarPermission.value = readGranted
        }

        val writeGranted = checkCalendarWritePermission()
        if (_hasCalendarWritePermission.value != writeGranted) {
            _hasCalendarWritePermission.value = writeGranted
        }

        // Always sync the UI state flag so the locked overlay reacts immediately
        _uiState.value = _uiState.value.copy(
            hasCalendarPermission = readGranted,
            hasCalendarWritePermission = writeGranted
        )
    }

    fun refreshDailyQuote() {
        _uiState.value = _uiState.value.copy(
            dailyQuote = dailyQuoteEngine.getDailyQuote()
        )
    }

    fun refreshGlobalEvent() {
        _uiState.value = _uiState.value.copy(
            todayGlobalEvent = globalEventsEngine.getTodayEvent(),
            tomorrowEventTitle = globalEventsEngine.getTomorrowTitle()
        )
    }

    fun updateCalendarEvent(
        eventId: Long,
        title: String,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean
    ) = viewModelScope.launch {
        if (!_hasCalendarPermission.value || !_hasCalendarWritePermission.value) return@launch

        deviceCalendarRepo.updateEvent(
            eventId,
            DeviceCalendarRepository.EventUpdate(
                title = title,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay
            )
        )
    }

    fun deleteCalendarEvent(eventId: Long) = viewModelScope.launch {
        if (!_hasCalendarPermission.value || !_hasCalendarWritePermission.value) return@launch
        deviceCalendarRepo.deleteEvent(eventId)
    }

    /** Complete a task from the Analytics screen — full XP logic identical to MainViewModel. */
    fun completeTask(task: TaskEntity) = viewModelScope.launch {
        val user = repository.getUserOnce() ?: UserEntity(googleId = "local_user").also {
            repository.upsertUser(it)
        }

        val now = System.currentTimeMillis()
        val todayEpochDay = now / 86_400_000L

        val resetUser = if (user.todayResetDay < todayEpochDay) {
            user.copy(tasksCompletedToday = 0, todayResetDay = todayEpochDay)
        } else {
            user
        }

        val userWithStreak = updateStreak(resetUser, todayEpochDay)

        val difficulty = TaskDifficulty.valueOf(task.difficulty)
        val baseXp = difficulty.xpValue

        val completedToday = userWithStreak.tasksCompletedToday
        val tieredXp = when {
            completedToday < 7 -> baseXp
            completedToday < 15 -> (baseXp * 0.50f).toInt()
            else -> (baseXp * 0.10f).toInt()
        }

        val recentCompleted = repository.getCompletedTasks().first()
        val recentTitles = recentCompleted
            .filter { it.completedAt != null && (now - it.completedAt) < 86_400_000L }
            .map { it.title.trim().lowercase() }

        val isRepeat = task.title.trim().lowercase() in recentTitles
        val afterRepeatXp = if (isRepeat) (tieredXp * 0.25f).toInt() else tieredXp

        val streakBonusEligible = userWithStreak.dailyStreak >= 2 && completedToday < 3
        val afterStreakXp = if (streakBonusEligible) {
            (afterRepeatXp * 1.25f).toInt()
        } else {
            afterRepeatXp
        }

        val isXpCritical = !isRepeat && Random.nextFloat() < 0.20f
        val finalXp = if (isXpCritical) {
            val multiplier = 1.5f + Random.nextFloat() * 1.5f
            (afterStreakXp * multiplier).toInt()
        } else {
            afterStreakXp
        }

        var finalUser = checkLevelUp(
            userWithStreak.copy(
                xp = userWithStreak.xp + finalXp,
                totalXp = userWithStreak.totalXp + finalXp,
                tasksCompletedToday = userWithStreak.tasksCompletedToday + 1
            )
        )

        val weeklyBonus = if (
            finalUser.dailyStreak > 0 &&
            finalUser.dailyStreak % 7 == 0 &&
            !finalUser.weeklyStreakClaimed
        ) {
            500
        } else {
            0
        }

        if (weeklyBonus > 0) {
            finalUser = checkLevelUp(
                finalUser.copy(
                    xp = finalUser.xp + weeklyBonus,
                    totalXp = finalUser.totalXp + weeklyBonus,
                    weeklyStreakClaimed = true
                )
            )
        }

        val monthlyBonus = if (
            finalUser.dailyStreak >= 30 &&
            !finalUser.monthlyMilestoneClaimed
        ) {
            2500
        } else {
            0
        }

        if (monthlyBonus > 0) {
            finalUser = checkLevelUp(
                finalUser.copy(
                    xp = finalUser.xp + monthlyBonus,
                    totalXp = finalUser.totalXp + monthlyBonus,
                    monthlyMilestoneClaimed = true
                )
            )
        }

        repository.updateTask(
            task.copy(
                isCompleted = true,
                status = "completed",
                completedAt = now
            )
        )
        repository.updateUser(finalUser)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun checkCalendarReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    private fun checkCalendarWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    private fun updateStreak(user: UserEntity, todayEpochDay: Long): UserEntity {
        val yesterday = todayEpochDay - 1
        return when (user.lastCompletionDay) {
            todayEpochDay -> user
            yesterday -> user.copy(
                dailyStreak = user.dailyStreak + 1,
                lastCompletionDay = todayEpochDay,
                weeklyStreakClaimed = if (user.dailyStreak + 1 < 7) {
                    false
                } else {
                    user.weeklyStreakClaimed
                }
            )
            else -> user.copy(
                dailyStreak = 1,
                lastCompletionDay = todayEpochDay,
                weeklyStreakClaimed = false,
                monthlyMilestoneClaimed = false
            )
        }
    }

    private fun checkLevelUp(user: UserEntity): UserEntity {
        var current = user
        while (current.xp >= getXpNeededForLevel(current.level)) {
            current = current.copy(
                xp = current.xp - getXpNeededForLevel(current.level),
                level = current.level + 1
            )
        }
        return current
    }

    private fun getXpNeededForLevel(level: Int): Int {
        if (level <= 0) return 0
        return (100 * level.toDouble().pow(1.5)).toInt()
    }
}