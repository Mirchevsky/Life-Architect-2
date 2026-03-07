package com.mirchevsky.lifearchitect2.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Parses free-form human text to detect date and time intent.
 *
 * Supports a wide range of formats including numeric dates (D.M.Y, D/M/Y, D-M-Y),
 * ISO 8601 (YYYY-MM-DD), named months in multiple languages, relative dates
 * (today, tomorrow, next Monday), times (14:30, 2pm), and recurrence patterns
 * (every week, daily).
 *
 * Returns a [ParseResult] with the best-guess [LocalDateTime] and a confidence flag.
 * A confident result means a full date was found; a non-confident result means only
 * a time was found (the date is assumed to be today or tomorrow).
 */
object DateIntentParser {

    /**
     * The result of a parse operation.
     *
     * @property bestGuess The best-guess [LocalDateTime], or null if no date/time was found.
     * @property isConfident True when a full date (not just a time) was detected.
     * @property isRecurring True when a recurrence pattern (e.g., "every week") was detected.
     */
    data class ParseResult(
        val bestGuess: LocalDateTime?,
        val isConfident: Boolean,
        val isRecurring: Boolean = false
    )

    // --- Regex Patterns ---

    private val numericDateRegex =
        """(?i)\b(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2}|\d{4}))?\b""".toRegex()

    private val monthNameDateRegex =
        """(?i)\b(?:(\d{1,2})(?:st|nd|rd|th)?\s+)?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|may|june|july|august|september|october|november|december)(?:\s+(\d{1,2})(?:st|nd|rd|th)?)?(?:,?\s+(\d{4}))?\b""".toRegex()

    private val isoDateRegex =
        """\b(\d{4})[-/.](\d{2})[-/.](\d{2})\b""".toRegex()

    private val relativeDateRegex =
        """(?i)\b(today|tomorrow|yesterday|tonight)\b""".toRegex()

    private val weekdayRegex =
        """(?i)\b(next|this|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\b""".toRegex()

    private val timeColonRegex =
        """(?i)\b(\d{1,2}):(\d{2})\s*(am|pm)?\b""".toRegex()

    private val timeSimpleRegex =
        """(?i)\b(\d{1,2})\s*(am|pm)\b""".toRegex()

    private val recurrenceRegex =
        """(?i)\b(every|daily|weekly|monthly|yearly|quarterly|every\s+\d+\s+(?:day|week|month)s?)\b""".toRegex()

    // --- Multilingual Month Name Map ---

    private val monthMap: Map<String, Int> = mapOf(
        // English
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
        // Spanish
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5,
        "junio" to 6, "julio" to 7, "agosto" to 8, "septiembre" to 9,
        "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
        // Portuguese
        "janeiro" to 1, "fevereiro" to 2, "março" to 3, "abril" to 4, "maio" to 5,
        "junho" to 6, "julho" to 7, "agosto" to 8, "setembro" to 9,
        "outubro" to 10, "novembro" to 11, "dezembro" to 12,
        // French
        "janvier" to 1, "février" to 2, "mars" to 3, "avril" to 4, "mai" to 5,
        "juin" to 6, "juillet" to 7, "août" to 8, "septembre" to 9,
        "octobre" to 10, "novembre" to 11, "décembre" to 12,
        // German
        "januar" to 1, "februar" to 2, "märz" to 3, "april" to 4,
        "juni" to 6, "juli" to 7, "august" to 8,
        "oktober" to 10, "dezember" to 12,
        // Indonesian
        "januari" to 1, "februari" to 2, "maret" to 3, "mei" to 5,
        "agustus" to 8, "agustus" to 8,
        // Arabic (transliterated)
        "يناير" to 1, "فبراير" to 2, "مارس" to 3, "أبريل" to 4, "مايو" to 5,
        "يونيو" to 6, "يوليو" to 7, "أغسطس" to 8, "سبتمبر" to 9,
        "أكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12,
        // Hindi (transliterated)
        "जनवरी" to 1, "फ़रवरी" to 2, "मार्च" to 3, "अप्रैल" to 4, "मई" to 5,
        "जून" to 6, "जुलाई" to 7, "अगस्त" to 8, "सितंबर" to 9,
        "अक्टूबर" to 10, "नवंबर" to 11, "दिसंबर" to 12
    )

    // --- Public API ---

    /**
     * Parses the given [input] string and returns a [ParseResult].
     *
     * The parser applies rules in priority order:
     * 1. Relative dates (today, tomorrow, next Monday)
     * 2. ISO 8601 dates (YYYY-MM-DD)
     * 3. Named month dates (1 Feb, Feb 1, 1st of February)
     * 4. Numeric dates (D.M.Y, D/M/Y, D-M-Y)
     * 5. Weekday names (Monday, next Fri)
     * 6. Time expressions (14:30, 2pm)
     *
     * @param input The raw user input string to parse.
     * @param now The reference time for relative date calculations. Defaults to [LocalDateTime.now].
     */
    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParseResult {
        val lower = input.lowercase(Locale.getDefault())

        val isRecurring = recurrenceRegex.containsMatchIn(lower)

        var date: LocalDate? = null
        var time: LocalTime? = null
        var isConfident = false

        // 1. Relative dates
        relativeDateRegex.find(lower)?.let { match ->
            val token = match.groupValues[1]
            date = when (token) {
                "today", "tonight" -> now.toLocalDate()
                "tomorrow" -> now.toLocalDate().plusDays(1)
                "yesterday" -> now.toLocalDate().minusDays(1)
                else -> null
            }
            if (date != null) isConfident = true
        }

        // 2. ISO date
        if (date == null) {
            isoDateRegex.find(lower)?.let { match ->
                try {
                    date = LocalDate.parse(match.value)
                    isConfident = true
                } catch (_: DateTimeParseException) { }
            }
        }

        // 3. Named month
        if (date == null) {
            monthNameDateRegex.find(lower)?.let { match ->
                val day1 = match.groupValues[1]
                val monthStr = match.groupValues[2].lowercase()
                val day2 = match.groupValues[3]
                val yearStr = match.groupValues[4]
                val day = (day1.ifEmpty { day2 }).toIntOrNull()
                val month = monthMap[monthStr]
                val year = yearStr.toIntOrNull() ?: now.year
                if (day != null && month != null) {
                    runCatching { date = LocalDate.of(year, month, day) }
                    if (date != null) isConfident = true
                }
            }
        }

        // 4. Numeric date (D.M.Y)
        if (date == null) {
            numericDateRegex.find(lower)?.let { match ->
                val d = match.groupValues[1].toIntOrNull() ?: return@let
                val m = match.groupValues[2].toIntOrNull() ?: return@let
                var y = match.groupValues[3].toIntOrNull()
                if (y != null && y < 100) {
                    y += if (y > 70) 1900 else 2000
                }
                // Validate ranges before constructing
                if (d in 1..31 && m in 1..12) {
                    runCatching { date = LocalDate.of(y ?: now.year, m, d) }
                    if (date != null) isConfident = true
                }
            }
        }

        // 5. Weekday
        if (date == null) {
            weekdayRegex.find(lower)?.let { match ->
                val modifier = match.groupValues[1].lowercase()
                val dayStr = match.groupValues[2].uppercase()
                val dayOfWeek = DayOfWeek.entries.find { it.name.startsWith(dayStr) }
                if (dayOfWeek != null) {
                    val base = if (modifier == "next") now.toLocalDate().plusWeeks(1)
                    else now.toLocalDate()
                    date = base.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                    isConfident = true
                }
            }
        }

        // 6a. Time with colon (14:30, 2:30pm)
        timeColonRegex.find(lower)?.let { match ->
            val h = match.groupValues[1].toIntOrNull() ?: return@let
            val min = match.groupValues[2].toIntOrNull() ?: return@let
            val ampm = match.groupValues[3].lowercase()
            val hour = resolveHour(h, ampm)
            runCatching { time = LocalTime.of(hour, min) }
        }

        // 6b. Simple time (2pm, 9am)
        if (time == null) {
            timeSimpleRegex.find(lower)?.let { match ->
                val h = match.groupValues[1].toIntOrNull() ?: return@let
                val ampm = match.groupValues[2].lowercase()
                val hour = resolveHour(h, ampm)
                runCatching { time = LocalTime.of(hour, 0) }
            }
        }

        // 7. Combine date + time
        val finalDateTime: LocalDateTime? = when {
            date != null -> LocalDateTime.of(date, time ?: LocalTime.of(9, 0))
            time != null -> {
                isConfident = false
                val today = now.toLocalDate()
                if (now.toLocalTime().isBefore(time)) LocalDateTime.of(today, time)
                else LocalDateTime.of(today.plusDays(1), time)
            }
            else -> null
        }

        return ParseResult(
            bestGuess = finalDateTime,
            isConfident = isConfident && finalDateTime != null,
            isRecurring = isRecurring
        )
    }

    // --- Private Helpers ---

    private fun resolveHour(hour: Int, ampm: String): Int = when {
        ampm == "am" && hour == 12 -> 0
        ampm == "pm" && hour != 12 -> hour + 12
        else -> hour
    }
}
