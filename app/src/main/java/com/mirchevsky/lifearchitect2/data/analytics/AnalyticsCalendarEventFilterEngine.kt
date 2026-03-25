package com.mirchevsky.lifearchitect2.data.analytics

import com.mirchevsky.lifearchitect2.data.CalendarEvent
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity

/**
 * Engine that preserves current Analytics behavior of hiding device calendar
 * events that mirror task titles for the selected day.
 */
class AnalyticsCalendarEventFilterEngine {

    fun filterTaskMirroredCalendarEvents(
        events: List<CalendarEvent>,
        tasksForDay: List<TaskEntity>
    ): List<CalendarEvent> {
        if (events.isEmpty() || tasksForDay.isEmpty()) return events

        val normalizedTaskTitles = tasksForDay
            .map { it.title.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        if (normalizedTaskTitles.isEmpty()) return events

        return events.filterNot { event ->
            event.title.trim().lowercase() in normalizedTaskTitles
        }
    }
}
