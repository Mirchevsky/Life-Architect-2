package com.mirchevsky.lifearchitect2.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Tasks     : Screen("tasks",     "Tasks",    Icons.Default.Checklist)
    object History   : Screen("history",   "History",  Icons.Default.History)
    object AddTask   : Screen("add_task",  "New Task", Icons.Default.AddCircle)
    object Trending  : Screen("trending",  "Trending", Icons.AutoMirrored.Filled.TrendingUp)
    object Analytics : Screen("analytics", "Analytics",Icons.Default.Analytics)
}
