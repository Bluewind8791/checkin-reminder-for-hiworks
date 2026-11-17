package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CalendarDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** One check alarm, which rings [minutesBefore] minutes before [start] on [date]. */
data class CheckAlarm(
    val date: LocalDate,
    val start: LocalTime,
    val minutesBefore: Long,
) {
    val at: LocalDateTime get() = date.atTime(start).minusMinutes(minutesBefore)
}

/** A notification to send. */
sealed interface Notice {
    data class CheckIn(val start: LocalTime, val minutesBefore: Long) : Notice
    data object Login : Notice
    data class Failed(val network: Boolean) : Notice
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
    ): CheckAlarm? {
        for (day in days.sortedBy { it.workDate }) {
            val date = parseDate(day.workDate) ?: continue
            if (date < now.toLocalDate() || (doneDate != null && date <= doneDate)) continue
            val expected = Decider.expectedStart(day, manualStart) as? ExpectedStart.At ?: continue
            nextStage(date, expected.time, alerts, now)?.let { return it }
        }
        return null
    }

    /** The first of [alerts] on [date] for [start] that rings after [now]. Null when every alert is past. */
    fun nextStage(date: LocalDate, start: LocalTime, alerts: List<Long>, now: LocalDateTime): CheckAlarm? =
        alerts.sortedDescending().map { CheckAlarm(date, start, it) }.firstOrNull { it.at > now }

    /** What to do after the check of [alarm] gave [result]. */
    fun afterCheck(alarm: CheckAlarm, alerts: List<Long>, result: ApiResult<Decision>, now: LocalDateTime): StageOutcome {
        // Only the alerts after this one. A late alarm skips the alerts it missed.
        val later = nextStage(alarm.date, alarm.start, alerts.filter { it < alarm.minutesBefore }, now)
        val goOn = later?.let { Then.Stage(it) } ?: Then.DayDone
        return when (result) {
            ApiResult.SessionExpired -> StageOutcome(Notice.Login, Then.Stop)
            is ApiResult.Failed -> StageOutcome(Notice.Failed(network = result.reason.startsWith("network")), goOn)
            is ApiResult.Success -> when (result.value) {
                Decision.CheckedIn, Decision.NoWork -> StageOutcome(null, Then.DayDone)
                is Decision.Reschedule -> StageOutcome(null, Then.Replan)
                Decision.NotCheckedIn -> StageOutcome(Notice.CheckIn(alarm.start, alarm.minutesBefore), goOn)
                is Decision.Undetermined -> StageOutcome(Notice.Failed(network = false), goOn)
            }
        }
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}
