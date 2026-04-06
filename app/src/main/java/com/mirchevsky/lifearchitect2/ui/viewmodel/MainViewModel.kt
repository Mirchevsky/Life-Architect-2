package com.mirchevsky.lifearchitect2.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.AppRepository
import com.mirchevsky.lifearchitect2.data.Theme
import com.mirchevsky.lifearchitect2.data.UserProgressEngine
import com.mirchevsky.lifearchitect2.data.TrendsRepository
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import com.mirchevsky.lifearchitect2.domain.TaskDifficulty
import com.mirchevsky.lifearchitect2.domain.TrendItem
import com.mirchevsky.lifearchitect2.widget.TaskWidgetProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// XP System Constants
// ---------------------------------------------------------------------------

/** Full XP for the first N tasks per day. */
private const val FULL_XP_THRESHOLD = 7

/** Reduced XP tier for tasks 8–15 per day. */
private const val HALF_XP_THRESHOLD = 15

private const val HALF_XP_MULTIPLIER = 0.50f
private const val GRIND_XP_MULTIPLIER = 0.10f

/** XP fraction kept when repeating the same task title within 24 hours. */
private const val REPETITION_PENALTY = 0.25f

/** Streak bonus multiplier applied to the first few tasks of the day when streak >= 2. */
private const val STREAK_BONUS_MULTIPLIER = 1.25f
private const val STREAK_BONUS_TASK_COUNT = 3

/** XP awarded for completing a full 7-day streak cycle. */
private const val WEEKLY_STREAK_XP = 500

/** XP awarded for achieving a 30-day continuous streak. */
private const val MONTHLY_MILESTONE_XP = 2500
private const val MONTHLY_MILESTONE_STREAK = 30

data class TrendsUiState(
    val trends: List<TrendItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCountry: String? = null
)

/**
 * The primary ViewModel for the Life Architect app.
 *
 * Manages task completion, XP calculation, streak tracking, theme preferences,
 * and the Trending feed. The anti-gaming XP system applies diminishing returns,
 * repetition penalties, velocity damping, streak bonuses, weekly payouts, and
 * monthly milestones.
 *
 * Task completion is always safe to call on any device because it fetches the
 * user record directly from the database rather than relying on the UI state flow.
 *
 * @param repository The [AppRepository] for all database operations.
 * @param trendsRepository The [TrendsRepository] for fetching Google Trends data.
 * @param appContext The application [Context], used for launching calendar intents.
 */
class MainViewModel(
    val repository: AppRepository,
    private val trendsRepository: TrendsRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private val _trendsUiState = MutableStateFlow(TrendsUiState())
    val trendsUiState: StateFlow<TrendsUiState> = _trendsUiState

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("life_architect_prefs", Context.MODE_PRIVATE)

    private val recentCompletionTimestamps = ArrayDeque<Long>(10)

    init {
        viewModelScope.launch {
            combine(
                repository.observeUser("local_user"),
                repository.observePendingTasksForUser("local_user"),
                repository.getCompletedTasks()
            ) { user, pending, completed ->
                val level = user?.level ?: 1
                val xp = user?.xp ?: 0
                val xpNeededForNextLevel = UserProgressEngine.getXpNeededForLevel(level)
                val xpNeededForCurrentLevel = UserProgressEngine.getXpNeededForLevel(level - 1)
                val xpInCurrentLevel = xp - xpNeededForCurrentLevel
                val totalXpForThisLevel = xpNeededForNextLevel - xpNeededForCurrentLevel

                MainUiState(
                    user = user,
                    pendingTasks = pending,
                    completedTasks = completed,
                    level = level,
                    rankTitle = getRankTitle(level),
                    xpToNextLevel = xpNeededForNextLevel,
                    currentLevelProgress = if (totalXpForThisLevel > 0) {
                        (xpInCurrentLevel.toFloat() / totalXpForThisLevel.toFloat())
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    themePreference = Theme.valueOf(user?.themePreference ?: Theme.SYSTEM.name),
                    xpPopupVisible = _uiState.value.xpPopupVisible,
                    xpPopupAmount = _uiState.value.xpPopupAmount,
                    xpPopupIsCritical = _uiState.value.xpPopupIsCritical
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        val savedCountry = prefs.getString("trends_country", null)
        _trendsUiState.update { it.copy(selectedCountry = savedCountry) }
        loadTrends(savedCountry)
    }

    // -----------------------------------------------------------------------
    // Task Actions
    // -----------------------------------------------------------------------

    /**
     * Marks a task as completed and awards XP to the user.
     *
     * Uses [AppRepository.getUserOnce] — a direct suspend read — to guarantee the
     * user row is fetched before any subsequent database write. This avoids the
     * race condition where [kotlinx.coroutines.flow.Flow.first] may return null on
     * a cold Flow before Room has emitted the first value.
     */
    fun onTaskCompleted(task: TaskEntity) = viewModelScope.launch {
        val user = repository.getUserOnce()
            ?: UserEntity(googleId = "local_user").also { repository.upsertUser(it) }

        val now = System.currentTimeMillis()
        val todayEpochDay = now / 86_400_000L

        // 1. Reset daily counter if it's a new day
        val resetUser = if (user.todayResetDay < todayEpochDay) {
            user.copy(tasksCompletedToday = 0, todayResetDay = todayEpochDay)
        } else {
            user
        }

        // 2. Update streak
        val userWithStreak = updateStreak(resetUser, todayEpochDay)

        // 3. Base XP from difficulty
        val difficulty = TaskDifficulty.fromStorageValue(task.difficulty)
        val baseXp = difficulty.xpValue

        // 4. Diminishing returns tier
        val completedToday = userWithStreak.tasksCompletedToday
        val tieredXp = when {
            completedToday < FULL_XP_THRESHOLD -> baseXp
            completedToday < HALF_XP_THRESHOLD -> (baseXp * HALF_XP_MULTIPLIER).toInt()
            else -> (baseXp * GRIND_XP_MULTIPLIER).toInt()
        }

        // 5. Repetition penalty
        val recentCompleted = repository.getCompletedTasks().first()
        val recentTitles = recentCompleted
            .filter { it.completedAt != null && (now - it.completedAt) < 86_400_000L }
            .map { it.title.trim().lowercase() }
        val isRepeat = task.title.trim().lowercase() in recentTitles
        val afterRepeatXp = if (isRepeat) {
            (tieredXp * REPETITION_PENALTY).toInt()
        } else {
            tieredXp
        }

        // 6. Velocity damping
        recentCompletionTimestamps.addLast(now)
        if (recentCompletionTimestamps.size > 5) recentCompletionTimestamps.removeFirst()
        val batchCount = recentCompletionTimestamps.count { (now - it) < 10_000L }
        val velocityMultiplier = when ((batchCount - 1).coerceAtLeast(0)) {
            0 -> 1.00f
            1 -> 0.90f
            2 -> 0.80f
            3 -> 0.70f
            else -> 0.60f
        }
        val afterVelocityXp = (afterRepeatXp * velocityMultiplier).toInt()

        // 7. Streak bonus on first STREAK_BONUS_TASK_COUNT tasks of the day
        val streakBonusEligible =
            userWithStreak.dailyStreak >= 2 && completedToday < STREAK_BONUS_TASK_COUNT
        val afterStreakXp = if (streakBonusEligible) {
            (afterVelocityXp * STREAK_BONUS_MULTIPLIER).toInt()
        } else {
            afterVelocityXp
        }

        // 8. Critical hit (disabled on repeat tasks)
        val isXpCritical = !isRepeat && Random.nextFloat() < 0.20f
        val finalXp = if (isXpCritical) {
            val multiplier = 1.5f + Random.nextFloat() * 1.5f
            (afterStreakXp * multiplier).toInt()
        } else {
            afterStreakXp
        }

        // 9. Increment counters
        var finalUser = UserProgressEngine.applyXp(userWithStreak, finalXp).copy(
            tasksCompletedToday = userWithStreak.tasksCompletedToday + 1
        )

        // 10. Weekly streak payout
        val weeklyBonus = checkWeeklyStreakPayout(finalUser)
        if (weeklyBonus > 0) {
            finalUser = UserProgressEngine.applyXp(finalUser, weeklyBonus).copy(
                weeklyStreakClaimed = true
            )
        }

        // 11. Monthly milestone payout
        val monthlyBonus = checkMonthlyMilestone(finalUser)
        if (monthlyBonus > 0) {
            finalUser = UserProgressEngine.applyXp(finalUser, monthlyBonus).copy(
                monthlyMilestoneClaimed = true
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
        notifyWidget()

        val totalBonus = weeklyBonus + monthlyBonus
        showXpPopup(
            amount = finalXp + totalBonus,
            isCritical = isXpCritical || totalBonus > 0
        )
        delay(1800)
        onDismissXpPopup()
    }

    /**
     * Reverts a completed task back to pending and deducts the base XP.
     */
    fun onTaskReverted(task: TaskEntity) = viewModelScope.launch {
        val user = repository.getUserOnce()
            ?: UserEntity(googleId = "local_user").also { repository.upsertUser(it) }

        val difficulty = TaskDifficulty.fromStorageValue(task.difficulty)
        val xpLost = difficulty.xpValue
        val updatedUser = user.copy(
            xp = (user.xp - xpLost).coerceAtLeast(0),
            totalXp = (user.totalXp - xpLost).coerceAtLeast(0)
        )
        repository.updateUser(updatedUser)
        repository.updateTask(
            task.copy(
                isCompleted = false,
                status = "pending",
                completedAt = null
            )
        )
        notifyWidget()
        showXpPopup(amount = -xpLost, isCritical = false)
        delay(1800)
        onDismissXpPopup()
    }

    /**
     * Creates and inserts a new task for the local user.
     *
     * Uses [AppRepository.getUserOnce] — a direct suspend read — to guarantee the
     * user row exists in the database before the task insert fires. This prevents
     * the FOREIGN KEY constraint failure that occurs when [Flow.first] returns null
     * on a cold Flow before Room has emitted the first value.
     *
     * @param title The task title, which may contain a date expression.
     * @param difficulty The difficulty string matching a [TaskDifficulty] enum name.
     * @param dueDate The optional due date parsed from the task title or selected via the date picker.
     */
    fun onAddTask(
        title: String,
        difficulty: String,
        dueDate: LocalDateTime? = null
    ) = viewModelScope.launch {
        val user = repository.getUserOnce()
            ?: UserEntity(googleId = "local_user").also { repository.upsertUser(it) }
        val dueDateMillis = dueDate
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()

        val newTask = TaskEntity(
            title = title,
            difficulty = difficulty,
            userId = user.googleId,
            dueDate = dueDateMillis
        )
        repository.insertTask(newTask)
        notifyWidget()
    }

    /**
     * Persists any change to a task (pin, urgent, title edit).
     */
    fun onUpdateTask(task: TaskEntity) = viewModelScope.launch {
        repository.updateTask(task)
        notifyWidget()
    }

    /**
     * Updates the task's due date and syncs the change to the device calendar.
     *
     * Strategy:
     * - If the date (day) changed → delete the old calendar event matched by
     *   title + begin time and insert a brand-new one on the new date.
     * - If only the time changed → update the existing event in-place via
     *   ContentResolver.update with a WHERE clause on title + old begin time.
     *
     * Because we do not store the calendar event ID in the database, both paths
     * use a title + begin-time query to locate the existing event.
     *
     * @param task The task entity before the change.
     * @param oldMillis The previous due-date epoch millis.
     * @param newMillis The new due-date epoch millis chosen by the user.
     */
    fun onUpdateTaskDueDate(task: TaskEntity, oldMillis: Long?, newMillis: Long) =
        viewModelScope.launch {
            // 1. Persist the new due date in the database
            repository.updateTask(task.copy(dueDate = newMillis))

            val cr = appContext.contentResolver

            // Determine whether the calendar day changed
            val oldDay = oldMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            val newDay = Instant.ofEpochMilli(newMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val dayChanged = oldDay != newDay

            if (oldMillis != null) {
                if (dayChanged) {
                    // Delete the old event matched by title + begin time
                    cr.delete(
                        Events.CONTENT_URI,
                        "${Events.TITLE} = ? AND ${Events.DTSTART} = ?",
                        arrayOf(task.title, oldMillis.toString())
                    )

                    // Insert a new event on the new date
                    val values = ContentValues().apply {
                        put(Events.TITLE, task.title)
                        put(Events.DTSTART, newMillis)
                        put(Events.DTEND, newMillis + 3_600_000L)
                        put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                        put(Events.CALENDAR_ID, 1L)
                    }
                    cr.insert(Events.CONTENT_URI, values)
                } else {
                    // Time-only change: update the existing event in-place
                    val values = ContentValues().apply {
                        put(Events.DTSTART, newMillis)
                        put(Events.DTEND, newMillis + 3_600_000L)
                    }
                    cr.update(
                        Events.CONTENT_URI,
                        values,
                        "${Events.TITLE} = ? AND ${Events.DTSTART} = ?",
                        arrayOf(task.title, oldMillis.toString())
                    )
                }
            } else {
                // No previous calendar event — insert a new one
                val values = ContentValues().apply {
                    put(Events.TITLE, task.title)
                    put(Events.DTSTART, newMillis)
                    put(Events.DTEND, newMillis + 3_600_000L)
                    put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                    put(Events.CALENDAR_ID, 1L)
                }
                cr.insert(Events.CONTENT_URI, values)
            }
        }

    /**
     * Triggers a full widget rebuild by sending [TaskWidgetProvider.ACTION_WIDGET_REFRESH]
     * as a broadcast.
     */
    private fun notifyWidget() {
        TaskWidgetProvider.sendRefreshBroadcast(appContext)
    }

    fun onCalendarClick(task: TaskEntity) {
        val dueDate = task.dueDate ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueDate)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun onThemeChange(theme: Theme) = viewModelScope.launch {
        repository.updateUserTheme(theme)
    }

    // -----------------------------------------------------------------------
    // Trends Actions
    // -----------------------------------------------------------------------

    fun loadTrends(countryCode: String?) = viewModelScope.launch {
        _trendsUiState.update { it.copy(isLoading = true, error = null) }
        prefs.edit().putString("trends_country", countryCode).apply()
        val result = trendsRepository.getTrends(countryCode)
        if (result.isEmpty()) {
            _trendsUiState.update {
                it.copy(
                    isLoading = false,
                    error = "Could not load trends. Check your connection and try again."
                )
            }
        } else {
            _trendsUiState.update { it.copy(isLoading = false, trends = result) }
        }
    }

    fun onCountrySelected(countryCode: String?) {
        _trendsUiState.update { it.copy(selectedCountry = countryCode) }
        loadTrends(countryCode)
    }

    // -----------------------------------------------------------------------
    // XP Popup
    // -----------------------------------------------------------------------

    private fun showXpPopup(amount: Int, isCritical: Boolean) {
        _uiState.update {
            it.copy(
                xpPopupVisible = true,
                xpPopupAmount = amount,
                xpPopupIsCritical = isCritical
            )
        }
    }

    fun onDismissXpPopup() {
        _uiState.update { it.copy(xpPopupVisible = false) }
    }

    // -----------------------------------------------------------------------
    // Private XP Helpers
    // -----------------------------------------------------------------------

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

    private fun checkWeeklyStreakPayout(user: UserEntity): Int {
        val isNewWeekMultiple = user.dailyStreak > 0 && user.dailyStreak % 7 == 0
        return if (isNewWeekMultiple && !user.weeklyStreakClaimed) {
            WEEKLY_STREAK_XP
        } else {
            0
        }
    }

    private fun checkMonthlyMilestone(user: UserEntity): Int {
        return if (
            user.dailyStreak >= MONTHLY_MILESTONE_STREAK &&
            !user.monthlyMilestoneClaimed
        ) {
            MONTHLY_MILESTONE_XP
        } else {
            0
        }
    }

    // -----------------------------------------------------------------------
    // Rank & Level Helpers
    // -----------------------------------------------------------------------

    private fun getRankTitle(level: Int): String = when (level) {
        in 1..4 -> "Novice"
        in 5..9 -> "Apprentice"
        in 10..14 -> "Journeyman"
        in 15..19 -> "Adept"
        in 20..24 -> "Expert"
        in 25..29 -> "Master"
        in 30..39 -> "Grandmaster"
        in 40..49 -> "Legend"
        else -> "Mythic"
    }
}