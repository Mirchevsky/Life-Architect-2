package com.mirchevsky.lifearchitect2.data.analytics

import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity

/**
 * Engine for deriving Analytics user summary card content.
 *
 * Keeps profile-level display derivation out of the ViewModel while preserving
 * current level / XP / streak behavior.
 */
class AnalyticsUserSummaryEngine {

    data class UserSummary(
        val userName: String,
        val level: Int,
        val xp: Int,
        val dailyStreak: Int
    )

    fun buildUserSummary(user: UserEntity): UserSummary {
        return UserSummary(
            userName = user.name,
            level = user.level,
            xp = user.xp,
            dailyStreak = user.dailyStreak
        )
    }
}
