package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CalendarDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * One check alarm. Stage 1 rings [firstMinutes] before [start] on [date], stages 2 and 3 ring 5 and 1 minutes
 * before.
 */
data class CheckAlarm(
    val date: LocalDate,
    val stage: Int,
    val start: LocalTime,
    val firstMinutes: Long = AlarmPlan.DEFAULT_FIRST_MINUTES,
) {
    val minutesBefore: Long get() = if (stage == 1) firstMinutes else AlarmPlan.LATER_MINUTES[stage - 2]
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
    const val STAGES = 3

    /** Stages 2 and 3. */
    val LATER_MINUTES = listOf(5L, 1L)

    /** What the user can pick for stage 1. Each is more than stage 2's 5 minutes. */
    val FIRST_MINUTES_CHOICES = listOf(10L, 15L, 20L, 30L, 60L)
    const val DEFAULT_FIRST_MINUTES = 10L

    /**
     * The first check alarm after [now]: the earliest stage that is still ahead, on the first day
     * after [doneDate] that has a start time. Null when the cached days have none.
     */
    fun next(
        days: List<CalendarDay>,
        manualStart: LocalTime?,
        now: LocalDateTime,
        doneDate: LocalDate?,
        firstMinutes: Long = DEFAULT_FIRST_MINUTES,
    ): CheckAlarm? {
        for (day in days.sortedBy { it.workDate }) {
            val date = parseDate(day.workDate) ?: continue
            if (date < now.toLocalDate() || (doneDate != null && date <= doneDate)) continue
            val expected = Decider.expectedStart(day, manualStart) as? ExpectedStart.At ?: continue
            firstStageAfter(CheckAlarm(date, 1, expected.time, firstMinutes), now)?.let { return it }
        }
        return null
    }

    /** What to do after the check of [alarm] gave [result]. */
    fun afterCheck(alarm: CheckAlarm, result: ApiResult<Decision>, now: LocalDateTime): StageOutcome {
        val later = if (alarm.stage < STAGES) alarm.copy(stage = alarm.stage + 1) else null
        val goOn = later?.let { firstStageAfter(it, now) }?.let { Then.Stage(it) } ?: Then.DayDone
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

    /** [from] or a later stage of the same day, whichever is first after [now]. Skips stages a late alarm missed. */
    private fun firstStageAfter(from: CheckAlarm, now: LocalDateTime): CheckAlarm? =
        (from.stage..STAGES).map { from.copy(stage = it) }.firstOrNull { it.at > now }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}
