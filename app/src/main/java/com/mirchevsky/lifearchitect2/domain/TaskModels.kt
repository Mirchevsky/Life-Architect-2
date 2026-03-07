package com.mirchevsky.lifearchitect2.domain

/**
 * Defines the difficulty tiers for a task.
 * Each tier has a corresponding base XP value.
 * This enum is used to calculate XP gains and losses.
 */
enum class TaskDifficulty(val xpValue: Int) {
    EASY(100),
    MEDIUM(250),
    HARD(500),
    EPIC(1000)
}
