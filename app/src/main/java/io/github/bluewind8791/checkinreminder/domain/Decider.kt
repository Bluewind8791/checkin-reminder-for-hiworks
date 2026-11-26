package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.CalendarDay
import io.github.bluewind8791.checkinreminder.data.Vacation
import io.github.bluewind8791.checkinreminder.data.WorkInfo
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The start or end time a day expects. */
sealed interface ExpectedTime {
    /** Weekend, holiday, or full-day vacation. */
    data object NoWork : ExpectedTime
    data class At(val time: LocalTime) : ExpectedTime
    data class Unknown(val reason: String) : ExpectedTime
}

/** What a check alarm found. */
sealed interface Decision {
    /** The check-in, or for an end alarm the check-out, is recorded. */
    data object Done : Decision

    /** Nothing to check today: no work, or for an end alarm no check-in or no check-out at the company. */
    data object NoWork : Decision

    /** The expected time is not the one the alarm was set for. */
    data class Reschedule(val time: LocalTime) : Decision
    data object NotDone : Decision

    /** The data is not enough to decide. Handled like a failed lookup. */
    data class Undetermined(val reason: String) : Decision
}

/** Pure decision logic. */
object Decider {
    private const val FULL_DAY = "D"
    private const val CHECK_IN = "start"
    private const val NO_TIME = "0000-00-00 00:00:00"
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun expectedStart(day: CalendarDay, manualStart: LocalTime?): ExpectedTime {
        if (day.dayOffType != 0) return ExpectedTime.NoWork
        if (day.vacationData.any { it.timeType == FULL_DAY }) return ExpectedTime.NoWork

        var time = manualStart ?: parseTime(day.planStartAt)
            ?: return ExpectedTime.Unknown("no start time")

        val partial = day.vacationData.filter { it.timeType != FULL_DAY }
        if (partial.any { parseTime(it.startTime) == null }) {
            return ExpectedTime.Unknown("vacation without start time")
        }
        // A vacation that covers the start time moves it to the vacation's end.
        // Repeat for back-to-back vacations. Each vacation can move it at most once.
        repeat(partial.size + 1) {
            val covering = partial.firstOrNull { covers(it, time) } ?: return ExpectedTime.At(time)
            time = parseTime(covering.endTime) ?: return ExpectedTime.Unknown("vacation without end time")
        }
        return ExpectedTime.Unknown("vacations do not end")
    }

    /**
     * The end time: [manualEnd], or the work schedule's `plan_end_at`. A half-day vacation needs no change,
     * because the schedule's end time follows it.
     */
    fun expectedEnd(day: CalendarDay, manualEnd: LocalTime?): ExpectedTime {
        if (day.dayOffType != 0) return ExpectedTime.NoWork
        if (day.vacationData.any { it.timeType == FULL_DAY }) return ExpectedTime.NoWork
        val time = manualEnd ?: parseTime(day.planEndAt) ?: return ExpectedTime.Unknown("no end time")
        return ExpectedTime.At(time)
    }

    fun isCheckedOut(info: WorkInfo): Boolean = recordTime(info.endAt) != null

    /** The check-out time from `end_at`, or null when there is none. */
    fun checkOutTime(info: WorkInfo): LocalTime? = recordTime(info.endAt)?.toLocalTime()

    fun usesEndCheck(info: WorkInfo): Boolean = info.useEndCheck != "N"

    fun isCheckedIn(info: WorkInfo): Boolean =
        info.details.any { it.type == CHECK_IN } ||
            (!info.startAt.isNullOrBlank() && info.startAt != NO_TIME)

    /** The check-in time from `start_at`, or null when there is none or it cannot be parsed. */
    fun checkInTime(info: WorkInfo): LocalTime? = recordTime(info.startAt)?.toLocalTime()

    /**
     * @param alarmStart the expected start time that the current alarm was set for.
     */
    fun decide(day: CalendarDay, info: WorkInfo, alarmStart: LocalTime, manualStart: LocalTime?): Decision {
        if (isCheckedIn(info)) return Decision.Done
        return when (val expected = expectedStart(day, manualStart)) {
            ExpectedTime.NoWork -> Decision.NoWork
            is ExpectedTime.Unknown -> Decision.Undetermined(expected.reason)
            is ExpectedTime.At ->
                if (expected.time != alarmStart) Decision.Reschedule(expected.time) else Decision.NotDone
        }
    }

    /**
     * For an end alarm. No alert on a day without a check-in, or at a company without the check-out.
     * @param alarmEnd the expected end time that the current alarm was set for.
     */
    fun decideEnd(day: CalendarDay, info: WorkInfo, alarmEnd: LocalTime, manualEnd: LocalTime?): Decision {
        if (isCheckedOut(info)) return Decision.Done
        if (!usesEndCheck(info) || !isCheckedIn(info)) return Decision.NoWork
        return when (val expected = expectedEnd(day, manualEnd)) {
            ExpectedTime.NoWork -> Decision.NoWork
            is ExpectedTime.Unknown -> Decision.Undetermined(expected.reason)
            is ExpectedTime.At ->
                if (expected.time != alarmEnd) Decision.Reschedule(expected.time) else Decision.NotDone
        }
    }

    /** A "yyyy-MM-dd HH:mm:ss" record time. Null for none ("0000-00-00 00:00:00"), a missing or a malformed value. */
    private fun recordTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank() || value == NO_TIME) return null
        return try {
            LocalDateTime.parse(value, DATE_TIME)
        } catch (e: DateTimeParseException) {
            null
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
