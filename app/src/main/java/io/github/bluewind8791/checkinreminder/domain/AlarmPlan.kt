package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CalendarDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

enum class AlarmKind { Start, End }

/**
 * One check alarm, which rings [minutesBefore] minutes before [start] on [date].
 * For an end alarm, [start] is the end time, and a negative [minutesBefore] rings after it.
 */
data class CheckAlarm(
    val date: LocalDate,
    val start: LocalTime,
    val minutesBefore: Long,
    val kind: AlarmKind = AlarmKind.Start,
) {
    val at: LocalDateTime get() = date.atTime(start).minusMinutes(minutesBefore)
}

/** A notification to send. */
sealed interface Notice {
    data class CheckIn(val start: LocalTime, val minutesBefore: Long) : Notice

    /** A negative [minutesBefore] is after the end time. */
    data class CheckOut(val end: LocalTime, val minutesBefore: Long) : Notice
    data object Login : Notice
    data class Failed(val network: Boolean, val kind: AlarmKind = AlarmKind.Start) : Notice
}

/** What to do with the alarms after a check. */
sealed interface Then {
    /** Set this alarm. */
    data class Stage(val alarm: CheckAlarm) : Then

    /** No more alarms on the checked date. Plan the next work day. */
    data object DayDone : Then

    /** The start time changed. Plan again from the fresh schedule. */
    data object Replan : Then

    /** The session expired. Cancel every alarm. */
    data object Stop : Then
}

data class StageOutcome(val notice: Notice?, val then: Then)

/** Pure alarm planning. */
object AlarmPlan {
    /** What the user can set for one alert, in minutes before the start time. */
    val MINUTES_RANGE = 1L..60L
    const val MAX_ALERTS = 5
    val DEFAULT_ALERTS = listOf(10L, 5L, 1L)

    /** For an end alert: up to 60 minutes before the end time, or up to 120 minutes after it (negative). */
    val END_MINUTES_RANGE = -120L..60L
    val DEFAULT_END_ALERTS = listOf(0L)

    /**
     * The first check alarm after [now]: the earliest of [alerts] that is still ahead, on the first day
     * after [doneDate] that has a start time. Null when the cached days have none.
     */
    fun next(
        days: List<CalendarDay>,
        manualStart: LocalTime?,
        now: LocalDateTime,
        doneDate: LocalDate?,
        alerts: List<Long> = DEFAULT_ALERTS,
    ): CheckAlarm? = first(days, now, doneDate, alerts, AlarmKind.Start) { Decider.expectedStart(it, manualStart) }

    /** Like [next], for the end alarms. */
    fun nextEnd(
        days: List<CalendarDay>,
        manualEnd: LocalTime?,
        now: LocalDateTime,
        doneDate: LocalDate?,
        alerts: List<Long>,
    ): CheckAlarm? = first(days, now, doneDate, alerts, AlarmKind.End) { Decider.expectedEnd(it, manualEnd) }

    private fun first(
        days: List<CalendarDay>,
        now: LocalDateTime,
        doneDate: LocalDate?,
        alerts: List<Long>,
        kind: AlarmKind,
        expected: (CalendarDay) -> ExpectedTime,
    ): CheckAlarm? {
        for (day in days.sortedBy { it.workDate }) {
            val date = parseDate(day.workDate) ?: continue
            if (date < now.toLocalDate() || (doneDate != null && date <= doneDate)) continue
            val time = expected(day) as? ExpectedTime.At ?: continue
            nextStage(date, time.time, alerts, now, kind)?.let { return it }
        }
        return null
    }

    /** The first of [alerts] on [date] for [time] that rings after [now]. Null when every alert is past. */
    fun nextStage(
        date: LocalDate,
        time: LocalTime,
        alerts: List<Long>,
        now: LocalDateTime,
        kind: AlarmKind = AlarmKind.Start,
    ): CheckAlarm? = alerts.sortedDescending().map { CheckAlarm(date, time, it, kind) }.firstOrNull { it.at > now }

    /** What to do after the check of [alarm] gave [result]. */
    fun afterCheck(alarm: CheckAlarm, alerts: List<Long>, result: ApiResult<Decision>, now: LocalDateTime): StageOutcome {
        // Only the alerts after this one. A late alarm skips the alerts it missed.
        val later = nextStage(alarm.date, alarm.start, alerts.filter { it < alarm.minutesBefore }, now, alarm.kind)
        val goOn = later?.let { Then.Stage(it) } ?: Then.DayDone
        return when (result) {
            ApiResult.SessionExpired -> StageOutcome(Notice.Login, Then.Stop)
            is ApiResult.Failed -> StageOutcome(Notice.Failed(result.reason.startsWith("network"), alarm.kind), goOn)
            is ApiResult.Success -> when (result.value) {
                Decision.Done, Decision.NoWork -> StageOutcome(null, Then.DayDone)
                is Decision.Reschedule -> StageOutcome(null, Then.Replan)
                Decision.NotDone -> StageOutcome(notDone(alarm), goOn)
                is Decision.Undetermined -> StageOutcome(Notice.Failed(false, alarm.kind), goOn)
            }
        }
    }

    /** "T-10" before the time, "T+0" at it, "T+30" after it. */
    fun label(minutesBefore: Long): String = if (minutesBefore > 0) "T-$minutesBefore" else "T+${-minutesBefore}"

    private fun notDone(alarm: CheckAlarm): Notice = when (alarm.kind) {
        AlarmKind.Start -> Notice.CheckIn(alarm.start, alarm.minutesBefore)
        AlarmKind.End -> Notice.CheckOut(alarm.start, alarm.minutesBefore)
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}
