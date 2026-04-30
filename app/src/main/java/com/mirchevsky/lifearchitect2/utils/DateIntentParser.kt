package com.mirchevsky.lifearchitect2.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

object DateIntentParser {
    data class ParseResult(
        val bestGuess: LocalDateTime?,
        val isConfident: Boolean,
        val isRecurring: Boolean = false
    )

    fun parse(
        input: String,
        now: LocalDateTime = LocalDateTime.now(),
        localeTag: String? = null
    ): ParseResult {
        val lower = input.lowercase()
        val lang = localeTag?.lowercase() ?: "en"
        val isRecurring = listOf("every", "daily", "weekly", "monthly").any { it in lower }
        var date: LocalDate? = null
        val time: LocalTime? = parseTime(lower)
        var confident = false

        val relative = relativeMap(lang)
        relative.entries.firstOrNull { lower.contains(it.key) }?.let {
            date = now.toLocalDate().plusDays(it.value.toLong())
            confident = true
        }

        if (date == null) {
            val wd = weekdayMatch(lower, lang)
            if (wd != null) {
                val next = wd.first
                val day = wd.second
                val base = if (next) now.toLocalDate().plusWeeks(1) else now.toLocalDate()
                date = base.with(TemporalAdjusters.nextOrSame(day))
                confident = true
            }
        }

        val finalDt = when {
            date != null -> LocalDateTime.of(date, time ?: LocalTime.of(9, 0))
            time != null -> LocalDateTime.of(
                if (now.toLocalTime().isBefore(time)) {
                    now.toLocalDate()
                } else {
                    now.toLocalDate().plusDays(1)
                },
                time
            )
            else -> null
        }

        return ParseResult(finalDt, confident && finalDt != null, isRecurring)
    }

    private fun parseTime(text: String): LocalTime? {
        Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(text)?.let {
            return LocalTime.of(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt()
            )
        }

        Regex("\\b(\\d{1,2})\\s*(am|pm)\\b").find(text)?.let {
            var h = it.groupValues[1].toInt()
            val ampm = it.groupValues[2]

            if (ampm == "pm" && h != 12) h += 12
            if (ampm == "am" && h == 12) h = 0

            return LocalTime.of(h, 0)
        }

        return null
    }

    private fun relativeMap(lang: String) = when {
        lang.startsWith("he") -> mapOf(
            "היום" to 0,
            "מחר" to 1,
            "אתמול" to -1,
            "הלילה" to 0
        )

        lang.startsWith("ru") -> mapOf(
            "сегодня" to 0,
            "завтра" to 1,
            "вчера" to -1,
            "вечером" to 0
        )

        lang.startsWith("ar") -> mapOf(
            "اليوم" to 0,
            "غدا" to 1,
            "غدًا" to 1,
            "أمس" to -1,
            "الليلة" to 0
        )

        else -> mapOf(
            "today" to 0,
            "tomorrow" to 1,
            "yesterday" to -1,
            "tonight" to 0
        )
    }

    private fun weekdayMatch(text: String, lang: String): Pair<Boolean, DayOfWeek>? {
        val pairs = when {
            lang.startsWith("he") -> listOf(
                "ראשון" to DayOfWeek.SUNDAY,
                "שני" to DayOfWeek.MONDAY,
                "שלישי" to DayOfWeek.TUESDAY,
                "רביעי" to DayOfWeek.WEDNESDAY,
                "חמישי" to DayOfWeek.THURSDAY,
                "שישי" to DayOfWeek.FRIDAY,
                "שבת" to DayOfWeek.SATURDAY
            )

            lang.startsWith("ru") -> listOf(
                "понедельник" to DayOfWeek.MONDAY,
                "вторник" to DayOfWeek.TUESDAY,
                "среда" to DayOfWeek.WEDNESDAY,
                "четверг" to DayOfWeek.THURSDAY,
                "пятница" to DayOfWeek.FRIDAY,
                "суббота" to DayOfWeek.SATURDAY,
                "воскресенье" to DayOfWeek.SUNDAY
            )

            lang.startsWith("ar") -> listOf(
                "الاثنين" to DayOfWeek.MONDAY,
                "الثلاثاء" to DayOfWeek.TUESDAY,
                "الأربعاء" to DayOfWeek.WEDNESDAY,
                "الخميس" to DayOfWeek.THURSDAY,
                "الجمعة" to DayOfWeek.FRIDAY,
                "السبت" to DayOfWeek.SATURDAY,
                "الأحد" to DayOfWeek.SUNDAY
            )

            else -> listOf(
                "monday" to DayOfWeek.MONDAY,
                "tuesday" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY,
                "thursday" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY,
                "saturday" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY
            )
        }

        for ((token, day) in pairs) {
            if (text.contains(token)) {
                val next = text.contains("next") ||
                        text.contains("הבא") ||
                        text.contains("следующ") ||
                        text.contains("القادم")

                return next to day
            }
        }

        return null
    }
}