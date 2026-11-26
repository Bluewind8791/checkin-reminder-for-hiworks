package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CalendarDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AlarmPlanTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val tomorrow = today.plusDays(1)
    private val nine = LocalTime.of(9, 0)
    private val defaults = AlarmPlan.DEFAULT_ALERTS
    private val five = listOf(60L, 30L, 15L, 3L, 2L)

    private fun day(date: LocalDate, dayOffType: Int = 0, planStartAt: String? = "09:00") =
        CalendarDay(workDate = date.toString(), dayOffType = dayOffType, planStartAt = planStartAt)

    private fun at(hour: Int, minute: Int, date: LocalDate = today) = date.atTime(hour, minute)

    private val days = listOf(day(today), day(tomorrow))

    // next

    @Test
    fun `early in the morning the first alert is next`() {
        assertEquals(CheckAlarm(today, nine, 10), AlarmPlan.next(days, null, at(7, 0), null))
    }

    @Test
    fun `an alarm rings its minutes before the start`() {
        assertEquals(at(8, 50), CheckAlarm(today, nine, 10).at)
        assertEquals(at(8, 59), CheckAlarm(today, nine, 1).at)
    }

    @Test
    fun `an alert already past is skipped`() {
        assertEquals(CheckAlarm(today, nine, 5), AlarmPlan.next(days, null, at(8, 52), null))
    }

    @Test
    fun `an alert exactly now is past`() {
        assertEquals(CheckAlarm(today, nine, 5), AlarmPlan.next(days, null, at(8, 50), null))
    }

    @Test
    fun `after the last alert the next day comes`() {
        assertEquals(CheckAlarm(tomorrow, nine, 10), AlarmPlan.next(days, null, at(8, 59), null))
    }

    @Test
    fun `a done day is skipped`() {
        assertEquals(CheckAlarm(tomorrow, nine, 10), AlarmPlan.next(days, null, at(7, 0), today))
    }

    @Test
    fun `a holiday is skipped`() {
        val withHoliday = listOf(day(today, dayOffType = 1), day(tomorrow))
        assertEquals(CheckAlarm(tomorrow, nine, 10), AlarmPlan.next(withHoliday, null, at(7, 0), null))
    }

    @Test
    fun `a manual start time is used`() {
        val manual = LocalTime.of(10, 30)
        assertEquals(CheckAlarm(today, manual, 10), AlarmPlan.next(days, manual, at(7, 0), null))
    }

    @Test
    fun `a past day in the cache is skipped`() {
        assertEquals(CheckAlarm(tomorrow, nine, 10), AlarmPlan.next(days, null, at(7, 0, tomorrow), null))
    }

    @Test
    fun `days are taken in date order`() {
        assertEquals(CheckAlarm(today, nine, 10), AlarmPlan.next(days.reversed(), null, at(7, 0), null))
    }

    @Test
    fun `no alarm when no day has a start time`() {
        assertNull(AlarmPlan.next(listOf(day(today, planStartAt = null)), null, at(7, 0), null))
        assertNull(AlarmPlan.next(emptyList(), null, at(7, 0), null))
    }

    @Test
    fun `next uses the alert setting`() {
        assertEquals(CheckAlarm(today, nine, 60), AlarmPlan.next(days, null, at(7, 0), null, five))
        assertEquals(CheckAlarm(today, nine, 15), AlarmPlan.next(days, null, at(8, 40), null, five))
        assertEquals(CheckAlarm(today, nine, 2), AlarmPlan.next(days, null, at(8, 57), null, five))
    }

    @Test
    fun `alerts in any order ring largest first`() {
        assertEquals(CheckAlarm(today, nine, 30), AlarmPlan.next(days, null, at(7, 0), null, listOf(1L, 30L, 5L)))
    }

    @Test
    fun `a single alert`() {
        assertEquals(CheckAlarm(today, nine, 15), AlarmPlan.next(days, null, at(7, 0), null, listOf(15L)))
        assertEquals(CheckAlarm(tomorrow, nine, 15), AlarmPlan.next(days, null, at(8, 45), null, listOf(15L)))
    }

    // nextStage

    @Test
    fun `nextStage uses the alert setting`() {
        assertEquals(at(8, 40), AlarmPlan.nextStage(today, nine, listOf(20L, 5L, 1L), at(7, 0))?.at)
    }

    @Test
    fun `nextStage skips an alert already past`() {
        assertEquals(CheckAlarm(today, nine, 5), AlarmPlan.nextStage(today, nine, listOf(20L, 5L, 1L), at(8, 45)))
    }

    @Test
    fun `nextStage is null after the last alert`() {
        assertNull(AlarmPlan.nextStage(today, nine, listOf(20L, 5L, 1L), at(8, 59)))
    }

    // afterCheck

    private val first = CheckAlarm(today, nine, 10)
    private val last = CheckAlarm(today, nine, 1)

    @Test
    fun `not checked in notifies and sets the next alert`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.NotDone), at(8, 50))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 10), Then.Stage(CheckAlarm(today, nine, 5))), outcome)
    }

    @Test
    fun `not checked in at the last alert ends the day`() {
        val outcome = AlarmPlan.afterCheck(last, defaults, ApiResult.Success(Decision.NotDone), at(8, 59))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 1), Then.DayDone), outcome)
    }

    @Test
    fun `a late alarm skips the alerts it missed`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.NotDone), at(8, 56))
        assertEquals(Then.Stage(CheckAlarm(today, nine, 1)), outcome.then)
    }

    @Test
    fun `the next alert comes from the alert setting`() {
        val outcome = AlarmPlan.afterCheck(CheckAlarm(today, nine, 30), five, ApiResult.Success(Decision.NotDone), at(8, 30))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 30), Then.Stage(CheckAlarm(today, nine, 15))), outcome)
    }

    @Test
    fun `checked in ends the day quietly`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.Done), at(8, 50))
        assertEquals(StageOutcome(null, Then.DayDone), outcome)
    }

    @Test
    fun `no work ends the day quietly`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.NoWork), at(8, 50))
        assertEquals(StageOutcome(null, Then.DayDone), outcome)
    }

    @Test
    fun `a new start time replans quietly`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.Reschedule(LocalTime.of(14, 0))), at(8, 50))
        assertEquals(StageOutcome(null, Then.Replan), outcome)
    }

    @Test
    fun `session expired asks for a login and stops`() {
        assertEquals(StageOutcome(Notice.Login, Then.Stop), AlarmPlan.afterCheck(first, defaults, ApiResult.SessionExpired, at(8, 50)))
    }

    @Test
    fun `a network failure notifies and keeps the next alert`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Failed("network: SocketTimeoutException"), at(8, 50))
        assertEquals(StageOutcome(Notice.Failed(network = true), Then.Stage(CheckAlarm(today, nine, 5))), outcome)
    }

    @Test
    fun `a response failure notifies as a response error`() {
        val outcome = AlarmPlan.afterCheck(last, defaults, ApiResult.Failed("HTTP 500"), at(8, 59))
        assertEquals(StageOutcome(Notice.Failed(network = false), Then.DayDone), outcome)
    }

    @Test
    fun `undetermined notifies as a response error`() {
        val outcome = AlarmPlan.afterCheck(first, defaults, ApiResult.Success(Decision.Undetermined("no start time")), at(8, 50))
        assertEquals(StageOutcome(Notice.Failed(network = false), Then.Stage(CheckAlarm(today, nine, 5))), outcome)
    }

    // end alarms

    private val six = LocalTime.of(18, 0)
    private val endAlerts = listOf(10L, 0L, -30L)

    private fun endDay(date: LocalDate, dayOffType: Int = 0) =
        CalendarDay(workDate = date.toString(), dayOffType = dayOffType, planEndAt = "18:00")

    private fun end(minutesBefore: Long, date: LocalDate = today) = CheckAlarm(date, six, minutesBefore, AlarmKind.End)

    @Test
    fun `an end alarm rings before, at, or after the end time`() {
        assertEquals(at(17, 50), end(10).at)
        assertEquals(at(18, 0), end(0).at)
        assertEquals(at(18, 30), end(-30).at)
    }

    @Test
    fun `the first end alert of the day is next`() {
        val days = listOf(endDay(today), endDay(tomorrow))
        assertEquals(end(10), AlarmPlan.nextEnd(days, null, at(12, 0), null, endAlerts))
    }

    @Test
    fun `after the end time the after alert is next`() {
        val days = listOf(endDay(today), endDay(tomorrow))
        assertEquals(end(-30), AlarmPlan.nextEnd(days, null, at(18, 5), null, endAlerts))
    }

    @Test
    fun `a done day or a day off moves the end alarms on`() {
        val days = listOf(endDay(today), endDay(tomorrow, dayOffType = 1), endDay(tomorrow.plusDays(1)))
        assertEquals(end(10, tomorrow.plusDays(1)), AlarmPlan.nextEnd(days, null, at(12, 0), today, endAlerts))
    }

    @Test
    fun `not checked out notifies and goes to the next end alert`() {
        val outcome = AlarmPlan.afterCheck(end(0), endAlerts, ApiResult.Success(Decision.NotDone), at(18, 0))
        assertEquals(StageOutcome(Notice.CheckOut(six, 0), Then.Stage(end(-30))), outcome)
    }

    @Test
    fun `checked out ends the end alarms of the day`() {
        val outcome = AlarmPlan.afterCheck(end(10), endAlerts, ApiResult.Success(Decision.Done), at(17, 50))
        assertEquals(StageOutcome(null, Then.DayDone), outcome)
    }

    @Test
    fun `a failed end check says it was the end check`() {
        val outcome = AlarmPlan.afterCheck(end(-30), endAlerts, ApiResult.Failed("HTTP 500"), at(18, 30))
        assertEquals(StageOutcome(Notice.Failed(false, AlarmKind.End), Then.DayDone), outcome)
    }

    @Test
    fun `labels show before, at and after`() {
        assertEquals("T-10", AlarmPlan.label(10))
        assertEquals("T+0", AlarmPlan.label(0))
        assertEquals("T+30", AlarmPlan.label(-30))
    }
}
