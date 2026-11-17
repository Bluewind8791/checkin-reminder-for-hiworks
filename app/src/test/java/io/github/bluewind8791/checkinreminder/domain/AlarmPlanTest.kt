package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CalendarDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmPlanTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val tomorrow = today.plusDays(1)
    private val nine = LocalTime.of(9, 0)

    private fun day(date: LocalDate, dayOffType: Int = 0, planStartAt: String? = "09:00") =
        CalendarDay(workDate = date.toString(), dayOffType = dayOffType, planStartAt = planStartAt)

    private fun at(hour: Int, minute: Int, date: LocalDate = today) = date.atTime(hour, minute)

    private val days = listOf(day(today), day(tomorrow))

    // next

    @Test
    fun `early in the morning the first stage is next`() {
        assertEquals(CheckAlarm(today, 1, nine), AlarmPlan.next(days, null, at(7, 0), null))
    }

    @Test
    fun `the first stage rings 10 minutes before the start`() {
        assertEquals(at(8, 50), CheckAlarm(today, 1, nine).at)
        assertEquals(at(8, 55), CheckAlarm(today, 2, nine).at)
        assertEquals(at(8, 59), CheckAlarm(today, 3, nine).at)
    }

    @Test
    fun `a stage already past is skipped`() {
        assertEquals(CheckAlarm(today, 2, nine), AlarmPlan.next(days, null, at(8, 52), null))
    }

    @Test
    fun `a stage exactly now is past`() {
        assertEquals(CheckAlarm(today, 2, nine), AlarmPlan.next(days, null, at(8, 50), null))
    }

    @Test
    fun `after the last stage the next day comes`() {
        assertEquals(CheckAlarm(tomorrow, 1, nine), AlarmPlan.next(days, null, at(8, 59), null))
    }

    @Test
    fun `a done day is skipped`() {
        assertEquals(CheckAlarm(tomorrow, 1, nine), AlarmPlan.next(days, null, at(7, 0), today))
    }

    @Test
    fun `a holiday is skipped`() {
        val withHoliday = listOf(day(today, dayOffType = 1), day(tomorrow))
        assertEquals(CheckAlarm(tomorrow, 1, nine), AlarmPlan.next(withHoliday, null, at(7, 0), null))
    }

    @Test
    fun `a manual start time is used`() {
        val manual = LocalTime.of(10, 30)
        assertEquals(CheckAlarm(today, 1, manual), AlarmPlan.next(days, manual, at(7, 0), null))
    }

    @Test
    fun `a past day in the cache is skipped`() {
        assertEquals(CheckAlarm(tomorrow, 1, nine), AlarmPlan.next(days, null, at(7, 0, tomorrow), null))
    }

    @Test
    fun `days are taken in date order`() {
        assertEquals(CheckAlarm(today, 1, nine), AlarmPlan.next(days.reversed(), null, at(7, 0), null))
    }

    @Test
    fun `no alarm when no day has a start time`() {
        assertNull(AlarmPlan.next(listOf(day(today, planStartAt = null)), null, at(7, 0), null))
        assertNull(AlarmPlan.next(emptyList(), null, at(7, 0), null))
    }

    @Test
    fun `a longer first alarm moves only the first stage`() {
        assertEquals(at(8, 30), CheckAlarm(today, 1, nine, 30).at)
        assertEquals(at(8, 55), CheckAlarm(today, 2, nine, 30).at)
        assertEquals(at(8, 59), CheckAlarm(today, 3, nine, 30).at)
    }

    @Test
    fun `next uses the first alarm setting`() {
        assertEquals(CheckAlarm(today, 1, nine, 30), AlarmPlan.next(days, null, at(8, 20), null, 30))
        assertEquals(CheckAlarm(today, 2, nine, 30), AlarmPlan.next(days, null, at(8, 40), null, 30))
    }

    // afterCheck

    private val first = CheckAlarm(today, 1, nine)
    private val last = CheckAlarm(today, 3, nine)

    @Test
    fun `not checked in notifies and sets the next stage`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.NotCheckedIn), at(8, 50))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 10), Then.Stage(CheckAlarm(today, 2, nine))), outcome)
    }

    @Test
    fun `not checked in at the last stage ends the day`() {
        val outcome = AlarmPlan.afterCheck(last, ApiResult.Success(Decision.NotCheckedIn), at(8, 59))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 1), Then.DayDone), outcome)
    }

    @Test
    fun `a late alarm skips the stages it missed`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.NotCheckedIn), at(8, 56))
        assertEquals(Then.Stage(CheckAlarm(today, 3, nine)), outcome.then)
    }

    @Test
    fun `checked in ends the day quietly`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.CheckedIn), at(8, 50))
        assertEquals(StageOutcome(null, Then.DayDone), outcome)
    }

    @Test
    fun `no work ends the day quietly`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.NoWork), at(8, 50))
        assertEquals(StageOutcome(null, Then.DayDone), outcome)
    }

    @Test
    fun `a new start time replans quietly`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.Reschedule(LocalTime.of(14, 0))), at(8, 50))
        assertEquals(StageOutcome(null, Then.Replan), outcome)
    }

    @Test
    fun `session expired asks for a login and stops`() {
        assertEquals(StageOutcome(Notice.Login, Then.Stop), AlarmPlan.afterCheck(first, ApiResult.SessionExpired, at(8, 50)))
    }

    @Test
    fun `a network failure notifies and keeps the next stage`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Failed("network: SocketTimeoutException"), at(8, 50))
        assertEquals(StageOutcome(Notice.Failed(network = true), Then.Stage(CheckAlarm(today, 2, nine))), outcome)
    }

    @Test
    fun `a response failure notifies as a response error`() {
        val outcome = AlarmPlan.afterCheck(last, ApiResult.Failed("HTTP 500"), at(8, 59))
        assertEquals(StageOutcome(Notice.Failed(network = false), Then.DayDone), outcome)
    }

    @Test
    fun `the next stage keeps the first alarm setting`() {
        val outcome = AlarmPlan.afterCheck(CheckAlarm(today, 1, nine, 30), ApiResult.Success(Decision.NotCheckedIn), at(8, 30))
        assertEquals(StageOutcome(Notice.CheckIn(nine, 30), Then.Stage(CheckAlarm(today, 2, nine, 30))), outcome)
    }

    @Test
    fun `undetermined notifies as a response error`() {
        val outcome = AlarmPlan.afterCheck(first, ApiResult.Success(Decision.Undetermined("no start time")), at(8, 50))
        assertEquals(StageOutcome(Notice.Failed(network = false), Then.Stage(CheckAlarm(today, 2, nine))), outcome)
    }
}
