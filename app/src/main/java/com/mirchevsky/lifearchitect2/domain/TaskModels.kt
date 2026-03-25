package com.mirchevsky.lifearchitect2.domain

import java.util.Locale

/**
 * Defines the difficulty tiers for a task.
 *
 * Each tier has a corresponding base XP value.
 *
 * This enum is used to calculate XP gains and losses.
 */
enum class TaskDifficulty(val xpValue: Int) {
    EASY(100),
    MEDIUM(250),
    HARD(500),
    EPIC(1000);

    companion object {
        /**
         * Parses persisted difficulty values safely.
         *
         * Supports existing stored values with different casing (e.g. "medium")
         * and falls back to [MEDIUM] for unexpected values to avoid runtime crashes.
         */
        fun fromStorageValue(value: String?): TaskDifficulty {
            val normalized = value?.trim().orEmpty()
            if (normalized.isEmpty()) return MEDIUM

            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull {
                    it.name == normalized.uppercase(Locale.ROOT)
                }
                ?: MEDIUM
        }
    }
}