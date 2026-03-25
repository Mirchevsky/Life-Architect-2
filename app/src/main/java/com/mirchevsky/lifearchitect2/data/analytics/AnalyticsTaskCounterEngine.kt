package com.mirchevsky.lifearchitect2.data.analytics

import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity

/**
 * Engine for deriving the Analytics "Total Tasks" summary card value.
 *
 * Preserves the existing behavior where total tasks reflects the sum of
 * completed and pending tasks.
 */
class AnalyticsTaskCounterEngine {

    fun deriveTotalTasks(
        completedTasks: List<TaskEntity>,
        pendingTasks: List<TaskEntity>
    ): Int {
        return completedTasks.size + pendingTasks.size
    }
}
