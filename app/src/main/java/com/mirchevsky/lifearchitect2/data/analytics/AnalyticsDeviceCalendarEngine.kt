package com.mirchevsky.lifearchitect2.data.analytics

import com.mirchevsky.lifearchitect2.data.CalendarEvent
import com.mirchevsky.lifearchitect2.data.DeviceCalendarRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Engine-style wrapper around [DeviceCalendarRepository] for the Analytics tab.
 *
 * Preserves the current permission-gated flow behavior while keeping the
 * ViewModel thinner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsDeviceCalendarEngine(
    private val repository: DeviceCalendarRepository
) {

    fun observeEventsForSelectedDay(
        selectedDayFlow: Flow<LocalDate>,
        hasCalendarPermissionFlow: Flow<Boolean>
    ): Flow<List<CalendarEvent>> {
        return combine(selectedDayFlow, hasCalendarPermissionFlow) { day, hasPerm ->
            day to hasPerm
        }.flatMapLatest { (day, hasPerm) ->
            if (hasPerm) {
                repository.observeEventsForDate(day)
            } else {
                flowOf(emptyList())
            }
        }
    }

    fun observeEventDaysForMonth(
        month: YearMonth,
        hasCalendarPermissionFlow: Flow<Boolean>
    ): Flow<Set<LocalDate>> {
        return hasCalendarPermissionFlow.flatMapLatest { hasPerm ->
            if (hasPerm) {
                repository.observeEventDaysForMonth(month)
            } else {
                flowOf(emptySet())
            }
        }
    }

    fun observeTotalEventCount(
        hasCalendarPermissionFlow: Flow<Boolean>
    ): Flow<Int> {
        return hasCalendarPermissionFlow.flatMapLatest { hasPerm ->
            if (hasPerm) {
                repository.observeTotalEventCount()
            } else {
                flowOf(0)
            }
        }
    }

    fun updateEvent(updateRequest: UpdateRequest): Boolean {
        return repository.updateEvent(
            eventId = updateRequest.eventId,
            update = DeviceCalendarRepository.EventUpdate(
                title = updateRequest.title,
                startMillis = updateRequest.startMillis,
                endMillis = updateRequest.endMillis,
                isAllDay = updateRequest.isAllDay
            )
        )
    }

    fun deleteEvent(eventId: Long): Boolean {
        return repository.deleteEvent(eventId)
    }

    data class UpdateRequest(
        val eventId: Long,
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
        val isAllDay: Boolean
    )
}