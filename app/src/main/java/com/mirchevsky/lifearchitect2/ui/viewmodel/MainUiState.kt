package com.mirchevsky.lifearchitect2.ui.viewmodel

import com.mirchevsky.lifearchitect2.data.AppLanguage
import com.mirchevsky.lifearchitect2.data.Theme
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity

data class MainUiState(
    val user: UserEntity? = null,
    val pendingTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val level: Int = 1,
    val rankTitle: String = "Initializing...",
    val xpToNextLevel: Int = 1,
    val currentLevelProgress: Float = 0f,
    val themePreference: Theme = Theme.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,

    // XP Pop-up State
    val xpPopupVisible: Boolean = false,
    val xpPopupAmount: Int = 0,
    val xpPopupIsCritical: Boolean = false
)