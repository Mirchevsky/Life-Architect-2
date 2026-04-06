package com.mirchevsky.lifearchitect2.data

import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import kotlin.math.pow

object UserProgressEngine {

    fun getXpNeededForLevel(level: Int): Int {
        if (level <= 0) return 0
        return (100 * level.toDouble().pow(1.5)).toInt()
    }

    fun applyXp(user: UserEntity, xpDelta: Int): UserEntity {
        if (xpDelta <= 0) return user

        var current = user.copy(
            xp = user.xp + xpDelta,
            totalXp = user.totalXp + xpDelta
        )

        while (current.xp >= getXpNeededForLevel(current.level)) {
            current = current.copy(
                xp = current.xp - getXpNeededForLevel(current.level),
                level = current.level + 1
            )
        }

        return current
    }
}