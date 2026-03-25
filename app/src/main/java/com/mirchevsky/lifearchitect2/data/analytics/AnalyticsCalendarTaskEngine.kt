package com.mirchevsky.lifearchitect2.data.analytics

import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.ui.viewmodel.DayStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Engine that derives Analytics task-calendar state from repository task streams.
 *
 * Keeps all date/task derivation logic out of the UI layer while preserving
 * existing Analytics calendar behavior.
 */
class AnalyticsCalendarTaskEngine {

    data class Snapshot(
        val dailyCompletions: Map<LocalDate, Int>,
        val monthlyTaskStatus: Map<LocalDate, DayStatus>,
        val onTimeCompletions: Int,
        val overdueCompletions: Int,
        val bestDay: Pair<LocalDate, Int>?,
        val bestWeek: Pair<LocalDate, Int>?,
        val selectedDay: LocalDate,
        val tasksForSelectedDay: List<TaskEntity>
    )

    fun buildSnapshot(
        completedTasks: List<TaskEntity>,
        pendingTasks: List<TaskEntity>,
        selectedDay: LocalDate,
        now: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        weekFields: WeekFields = WeekFields.of(Locale.getDefault())
    ): Snapshot {
        val tasksByDate = completedTasks
            .filter { it.completedAt != null }
            .groupBy { task ->
                Instant.ofEpochMilli(task.completedAt!!)
                    .atZone(zone)
                    .toLocalDate()
            }

        val dailyCompletions = (0..29).associate { offset ->
            val day = now.minusDays(offset.toLong())
            day to (tasksByDate[day]?.size ?: 0)
        }

        val bestDay = tasksByDate
            .maxByOrNull { it.value.size }
            ?.let { it.key to it.value.size }

        val weeklyCompletions = completedTasks
            .filter { it.completedAt != null }
            .groupBy { task ->
                val date = Instant.ofEpochMilli(task.completedAt!!)
                    .atZone(zone)
                    .toLocalDate()
                date.with(weekFields.dayOfWeek(), 1)
            }

        val bestWeek = weeklyCompletions
            .maxByOrNull { it.value.size }
            ?.let { it.key to it.value.size }

        val onTime = completedTasks.count {
            it.dueDate != null &&
                    it.completedAt != null &&
                    it.completedAt <= it.dueDate
        }

        val overdue = completedTasks.count {
            it.dueDate != null &&
                    it.completedAt != null &&
                    it.completedAt > it.dueDate
        }

        val completedDays = completedTasks
            .filter { it.dueDate != null }
            .map { Instant.ofEpochMilli(it.dueDate!!).atZone(zone).toLocalDate() }
            .toSet()

        val pendingDays = pendingTasks
            .filter { it.dueDate != null }
            .map { Instant.ofEpochMilli(it.dueDate!!).atZone(zone).toLocalDate() }
            .toSet()

        val allDays = completedDays + pendingDays

        val monthlyStatus = allDays.associateWith { date ->
            val hasCompleted = completedDays.contains(date)
            val hasPending = pendingDays.contains(date)
            when {
                hasCompleted && hasPending -> DayStatus.BOTH
                hasCompleted -> DayStatus.COMPLETED
                else -> DayStatus.PENDING
            }
        }

        val resolvedDay = resolveSelectedDay(
            selectedDay = selectedDay,
            today = now,
            allDays = allDays
        )

        val pendingForDay = pendingTasks.filter { task ->
            task.dueDate != null &&
                    Instant.ofEpochMilli(task.dueDate!!)
                        .atZone(zone)
                        .toLocalDate() == resolvedDay
        }

        val completedForDay = completedTasks.filter { task ->
            task.dueDate != null &&
                    Instant.ofEpochMilli(task.dueDate!!)
                        .atZone(zone)
                        .toLocalDate() == resolvedDay
        }

        val tasksForDay = pendingForDay + completedForDay

        return Snapshot(
            dailyCompletions = dailyCompletions,
            monthlyTaskStatus = monthlyStatus,
            onTimeCompletions = onTime,
            overdueCompletions = overdue,
            bestDay = bestDay,
            bestWeek = bestWeek,
            selectedDay = resolvedDay,
            tasksForSelectedDay = tasksForDay
        )
    }

    private fun resolveSelectedDay(
        selectedDay: LocalDate,
        today: LocalDate,
        allDays: Set<LocalDate>
    ): LocalDate {
        return if (allDays.contains(selectedDay)) {
            selectedDay
        } else if (selectedDay == today) {
            allDays.filter { it >= today }.minOrNull()
                ?: allDays.maxOrNull()
                ?: selectedDay
        } else {
            selectedDay
        }
    }
}