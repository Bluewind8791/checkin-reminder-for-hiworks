package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.CalendarDay
import io.github.bluewind8791.checkinreminder.data.Vacation
import io.github.bluewind8791.checkinreminder.data.WorkInfo
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

sealed interface ExpectedStart {
    /** Weekend, holiday, or full-day vacation. */
    data object NoWork : ExpectedStart
    data class At(val time: LocalTime) : ExpectedStart
    data class Unknown(val reason: String) : ExpectedStart
}

sealed interface Decision {
    data object CheckedIn : Decision
    data object NoWork : Decision

    /** The expected start time is not the one the alarm was set for. */
    data class Reschedule(val time: LocalTime) : Decision
    data object NotCheckedIn : Decision

    /** The data is not enough to decide. Handled like a failed lookup. */
    data class Undetermined(val reason: String) : Decision
}

/** Pure decision logic. */
object Decider {
    private const val FULL_DAY = "D"
    private const val CHECK_IN = "start"
    private const val NO_TIME = "0000-00-00 00:00:00"
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun expectedStart(day: CalendarDay, manualStart: LocalTime?): ExpectedStart {
        if (day.dayOffType != 0) return ExpectedStart.NoWork
        if (day.vacationData.any { it.timeType == FULL_DAY }) return ExpectedStart.NoWork

        var time = manualStart ?: parseTime(day.planStartAt)
            ?: return ExpectedStart.Unknown("no start time")

        val partial = day.vacationData.filter { it.timeType != FULL_DAY }
        if (partial.any { parseTime(it.startTime) == null }) {
            return ExpectedStart.Unknown("vacation without start time")
        }
        // A vacation that covers the start time moves it to the vacation's end.
        // Repeat for back-to-back vacations. Each vacation can move it at most once.
        repeat(partial.size + 1) {
            val covering = partial.firstOrNull { covers(it, time) } ?: return ExpectedStart.At(time)
            time = parseTime(covering.endTime) ?: return ExpectedStart.Unknown("vacation without end time")
        }
        return ExpectedStart.Unknown("vacations do not end")
    }

    fun isCheckedIn(info: WorkInfo): Boolean =
        info.details.any { it.type == CHECK_IN } ||
            (!info.startAt.isNullOrBlank() && info.startAt != NO_TIME)

    /** The check-in time from `start_at`, or null when there is none or it cannot be parsed. */
    fun checkInTime(info: WorkInfo): LocalTime? {
        val value = info.startAt
        if (value.isNullOrBlank() || value == NO_TIME) return null
        return try {
            LocalDateTime.parse(value, DATE_TIME).toLocalTime()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * @param alarmStart the expected start time that the current alarm was set for.
     */
    fun decide(day: CalendarDay, info: WorkInfo, alarmStart: LocalTime, manualStart: LocalTime?): Decision {
        if (isCheckedIn(info)) return Decision.CheckedIn
        return when (val expected = expectedStart(day, manualStart)) {
            ExpectedStart.NoWork -> Decision.NoWork
            is ExpectedStart.Unknown -> Decision.Undetermined(expected.reason)
            is ExpectedStart.At ->
                if (expected.time != alarmStart) Decision.Reschedule(expected.time) else Decision.NotCheckedIn
        }
    }

    private fun covers(vacation: Vacation, time: LocalTime): Boolean {
        val start = parseTime(vacation.startTime) ?: return false
        val end = parseTime(vacation.endTime)
        return start <= time && (end == null || time < end)
    }

    /** Parses "HH:mm" or "HH:mm:ss". Returns null for a missing or malformed value. */
    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalTime.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
