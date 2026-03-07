package com.mirchevsky.lifearchitect2.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "google_id")
    val googleId: String = "local_user",
    val email: String = "",
    val name: String = "Adventurer",
    @ColumnInfo(name = "picture_url")
    val pictureUrl: String? = null,
    val level: Int = 1,
    val xp: Int = 0,
    @ColumnInfo(name = "total_xp")
    val totalXp: Int = 0,
    /**
     * Serialized [AvatarConfig] stored as a comma-separated string of part IDs.
     */
    @ColumnInfo(name = "avatar_config")
    val avatarConfig: String? = null,
    @ColumnInfo(name = "theme_preference")
    val themePreference: String = "SYSTEM",

    // --- Streak Fields ---

    /** Number of consecutive days the user has completed at least one task. */
    @ColumnInfo(name = "daily_streak")
    val dailyStreak: Int = 0,

    /** The date (epoch day: millis / 86_400_000) of the last task completion. */
    @ColumnInfo(name = "last_completion_day")
    val lastCompletionDay: Long = 0L,

    /** Number of tasks completed today, used for diminishing-returns XP. */
    @ColumnInfo(name = "tasks_completed_today")
    val tasksCompletedToday: Int = 0,

    /** The epoch day when [tasksCompletedToday] was last reset. */
    @ColumnInfo(name = "today_reset_day")
    val todayResetDay: Long = 0L,

    /**
     * Whether the 7-day streak weekly payout has been claimed for the current
     * 7-day cycle. Resets to false every time dailyStreak crosses a new multiple of 7.
     */
    @ColumnInfo(name = "weekly_streak_claimed")
    val weeklyStreakClaimed: Boolean = false,

    /** Whether the 30-day streak milestone XP has been awarded this streak cycle. */
    @ColumnInfo(name = "monthly_milestone_claimed")
    val monthlyMilestoneClaimed: Boolean = false
)
