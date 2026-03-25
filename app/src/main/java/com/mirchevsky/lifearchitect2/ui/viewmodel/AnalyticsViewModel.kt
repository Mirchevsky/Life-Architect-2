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
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsCalendarCounterEngine
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsCalendarEventFilterEngine
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsCalendarTaskEngine
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsDeviceCalendarEngine
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsDeviceCalendarEngine.UpdateRequest
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsTaskCounterEngine
import com.mirchevsky.lifearchitect2.data.analytics.AnalyticsUserSummaryEngine
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import com.mirchevsky.lifearchitect2.domain.TaskDifficulty
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    private val analyticsTaskEngine = AnalyticsCalendarTaskEngine()
    private val analyticsCalendarFilterEngine = AnalyticsCalendarEventFilterEngine()
    private val analyticsTaskCounterEngine = AnalyticsTaskCounterEngine()
    private val analyticsUserSummaryEngine = AnalyticsUserSummaryEngine()
    private val analyticsCalendarCounterEngine = AnalyticsCalendarCounterEngine()
    private val analyticsDeviceCalendarEngine =
        AnalyticsDeviceCalendarEngine(DeviceCalendarRepository(appContext))
    private val dailyQuoteEngine = DailyQuoteEngine(appContext)
    private val globalEventsEngine = GlobalEventsEngine()

    init {
        refreshDailyQuote()
        refreshGlobalEvent(LocalDate.now())

        // ── Task-based state ───────────────────────────────────────────────
        viewModelScope.launch {
            combine(
                repository.observeUser("local_user"),
                repository.getCompletedTasks(),
                repository.observePendingTasksForUser("local_user"),
                _selectedDay
            ) { user, completedTasks, pendingTasks, selectedDay ->
                if (user == null) return@combine AnalyticsUiState(isLoading = false)

                val snapshot = analyticsTaskEngine.buildSnapshot(
                    completedTasks = completedTasks,
                    pendingTasks = pendingTasks,
                    selectedDay = selectedDay
                )
                val userSummary = analyticsUserSummaryEngine.buildUserSummary(user)
                val totalTasks = analyticsTaskCounterEngine.deriveTotalTasks(
                    completedTasks = completedTasks,
                    pendingTasks = pendingTasks
                )

                // Preserve existing calendar events, permissions, daily quote,
                // and global event state while task state updates.
                val current = _uiState.value
                val filteredCalendarEvents =
                    analyticsCalendarFilterEngine.filterTaskMirroredCalendarEvents(
                        events = current.calendarEventsForSelectedDay,
                        tasksForDay = snapshot.tasksForSelectedDay
                    )

                AnalyticsUiState(
                    userName = userSummary.userName,
                    level = userSummary.level,
                    xp = userSummary.xp,
                    dailyStreak = userSummary.dailyStreak,
                    totalTasksCompleted = totalTasks,
                    totalCalendarEvents = current.totalDeviceCalendarEvents,
                    dailyCompletions = snapshot.dailyCompletions,
                    monthlyTaskStatus = snapshot.monthlyTaskStatus,
                    onTimeCompletions = snapshot.onTimeCompletions,
                    overdueCompletions = snapshot.overdueCompletions,
                    bestDay = snapshot.bestDay,
                    bestWeek = snapshot.bestWeek,
                    selectedDay = snapshot.selectedDay,
                    tasksForSelectedDay = snapshot.tasksForSelectedDay,
                    calendarEventsForSelectedDay = filteredCalendarEvents,
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
            analyticsDeviceCalendarEngine
                .observeEventsForSelectedDay(_selectedDay, _hasCalendarPermission)
                .collect { events ->
                    val current = _uiState.value
                    _uiState.value = _uiState.value.copy(
                        calendarEventsForSelectedDay =
                            analyticsCalendarFilterEngine.filterTaskMirroredCalendarEvents(
                                events = events,
                                tasksForDay = current.tasksForSelectedDay
                            ),
                        hasCalendarPermission = _hasCalendarPermission.value,
                        hasCalendarWritePermission = _hasCalendarWritePermission.value
                    )
                }
        }

        // ── Calendar event days for month grid dots ───────────────────────────
        viewModelScope.launch {
            analyticsDeviceCalendarEngine
                .observeEventDaysForMonth(YearMonth.now(), _hasCalendarPermission)
                .collect { days ->
                    _uiState.value = _uiState.value.copy(calendarEventDays = days)
                }
        }

        // ── Total device calendar event count ────────────────────────────────
        viewModelScope.launch {
            analyticsDeviceCalendarEngine
                .observeTotalEventCount(_hasCalendarPermission)
                .collect { count ->
                    val totalCalendarEvents =
                        analyticsCalendarCounterEngine.deriveTotalCalendarEvents(
                            totalDeviceCalendarEvents = count
                        )
                    _uiState.value = _uiState.value.copy(
                        totalDeviceCalendarEvents = count,
                        totalCalendarEvents = totalCalendarEvents
                    )
                }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Called when the user taps a day in the calendar. */
    fun selectDay(date: LocalDate) {
        _selectedDay.value = date
        refreshGlobalEvent(date)
    }

    /** Called by AnalyticsScreen whenever the screen is entered/re-entered. */
    fun onAnalyticsScreenOpened() {
        val today = LocalDate.now()
        _selectedDay.value = today
        refreshGlobalEvent(today)
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

    fun refreshGlobalEvent(date: LocalDate = _selectedDay.value) {
        _uiState.value = _uiState.value.copy(
            todayGlobalEvent = globalEventsEngine.getEventForDate(date),
            tomorrowEventTitle = globalEventsEngine.getEventForDate(date.plusDays(1)).title
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

        analyticsDeviceCalendarEngine.updateEvent(
            UpdateRequest(
                eventId = eventId,
                title = title,
                startMillis = startMillis,
                endMillis = endMillis,
                isAllDay = isAllDay
            )
        )
    }

    fun deleteCalendarEvent(eventId: Long) = viewModelScope.launch {
        if (!_hasCalendarPermission.value || !_hasCalendarWritePermission.value) return@launch
        analyticsDeviceCalendarEngine.deleteEvent(eventId)
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

        val difficulty = TaskDifficulty.fromStorageValue(task.difficulty)
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