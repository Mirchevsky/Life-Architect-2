package com.mirchevsky.lifearchitect2.data.analytics

/**
 * Engine for deriving the Analytics "Calendar Events" summary card value.
 *
 * Preserves the existing behavior by directly exposing the permission-gated
 * device calendar total as the visible counter value.
 */
class AnalyticsCalendarCounterEngine {

    fun deriveTotalCalendarEvents(totalDeviceCalendarEvents: Int): Int {
        return totalDeviceCalendarEvents
    }
}
