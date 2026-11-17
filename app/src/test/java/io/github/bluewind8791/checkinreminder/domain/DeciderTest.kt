package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.CalendarDay
import io.github.bluewind8791.checkinreminder.data.Vacation
import io.github.bluewind8791.checkinreminder.data.WorkDetail
import io.github.bluewind8791.checkinreminder.data.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class DeciderTest {
    private val nine = LocalTime.of(9, 0)

    private fun day(
        dayOffType: Int = 0,
        planStartAt: String? = "09:00",
        vacations: List<Vacation> = emptyList(),
    ) = CalendarDay(
        workDate = "2026-11-10",
        dayOffType = dayOffType,
        planStartAt = planStartAt,
        vacationData = vacations,
    )

    private val notCheckedIn = WorkInfo(date = "2026-11-10", startAt = "0000-00-00 00:00:00")
    private val checkedIn = WorkInfo(
        date = "2026-11-10",
        startAt = "2026-11-10 08:48:24",
        details = listOf(WorkDetail(type = "start")),
    )

    private fun half(start: String, end: String?) = Vacation(timeType = "H", startTime = start, endTime = end)

    // expectedStart

    @Test
    fun `a normal day uses the scheduled start time`() {
        assertEquals(ExpectedStart.At(nine), Decider.expectedStart(day(), null))
    }

    @Test
    fun `the scheduled start time may include seconds`() {
        assertEquals(ExpectedStart.At(nine), Decider.expectedStart(day(planStartAt = "09:00:00"), null))
    }

    @Test
    fun `a manual start time wins over the scheduled one`() {
        val manual = LocalTime.of(10, 30)
        assertEquals(ExpectedStart.At(manual), Decider.expectedStart(day(), manual))
    }

    @Test
    fun `a manual start time works without a scheduled one`() {
        val manual = LocalTime.of(8, 0)
        assertEquals(ExpectedStart.At(manual), Decider.expectedStart(day(planStartAt = null), manual))
    }

    @Test
    fun `no start time at all is unknown`() {
        assertTrue(Decider.expectedStart(day(planStartAt = null), null) is ExpectedStart.Unknown)
    }

    @Test
    fun `a weekend or holiday has no work`() {
        assertEquals(ExpectedStart.NoWork, Decider.expectedStart(day(dayOffType = 1), null))
    }

    @Test
    fun `a full-day vacation has no work`() {
        val vacation = Vacation(timeType = "D")
        assertEquals(ExpectedStart.NoWork, Decider.expectedStart(day(vacations = listOf(vacation)), null))
    }

    @Test
    fun `a morning half day moves the start to the vacation end`() {
        val result = Decider.expectedStart(day(vacations = listOf(half("09:00:00", "14:00:00"))), null)
        assertEquals(ExpectedStart.At(LocalTime.of(14, 0)), result)
    }

    @Test
    fun `an afternoon half day keeps the start time`() {
        val result = Decider.expectedStart(day(vacations = listOf(half("14:00:00", "18:00:00"))), null)
        assertEquals(ExpectedStart.At(nine), result)
    }

    @Test
    fun `back-to-back vacations move the start to the last end`() {
        val result = Decider.expectedStart(
            day(vacations = listOf(half("11:00", "13:00"), half("09:00", "11:00"))),
            null,
        )
        assertEquals(ExpectedStart.At(LocalTime.of(13, 0)), result)
    }

    @Test
    fun `a vacation that ends exactly at the start time does not move it`() {
        val result = Decider.expectedStart(day(vacations = listOf(half("07:00", "09:00"))), null)
        assertEquals(ExpectedStart.At(nine), result)
    }

    @Test
    fun `a covering vacation without an end time is unknown`() {
        val result = Decider.expectedStart(day(vacations = listOf(half("09:00", null))), null)
        assertTrue(result is ExpectedStart.Unknown)
    }

    @Test
    fun `a partial vacation without a start time is unknown`() {
        val result = Decider.expectedStart(day(vacations = listOf(Vacation(timeType = "H"))), null)
        assertTrue(result is ExpectedStart.Unknown)
    }

    // isCheckedIn

    @Test
    fun `a start record means checked in`() {
        assertTrue(Decider.isCheckedIn(WorkInfo(date = "2026-11-10", details = listOf(WorkDetail("start")))))
    }

    @Test
    fun `a real start time means checked in`() {
        assertTrue(Decider.isCheckedIn(WorkInfo(date = "2026-11-10", startAt = "2026-11-10 08:48:24")))
    }

    @Test
    fun `the zero start time means not checked in`() {
        assertFalse(Decider.isCheckedIn(notCheckedIn))
    }

    @Test
    fun `a missing start time means not checked in`() {
        assertFalse(Decider.isCheckedIn(WorkInfo(date = "2026-11-10")))
    }

    // checkInTime

    @Test
    fun `the check-in time comes from start_at`() {
        assertEquals(LocalTime.of(8, 48, 24), Decider.checkInTime(checkedIn))
    }

    @Test
    fun `no check-in time before the check-in`() {
        assertNull(Decider.checkInTime(notCheckedIn))
        assertNull(Decider.checkInTime(WorkInfo(date = "2026-11-10")))
    }

    @Test
    fun `a malformed start_at has no check-in time`() {
        assertNull(Decider.checkInTime(WorkInfo(date = "2026-11-10", startAt = "08:48")))
    }

    // decide

    @Test
    fun `not checked in at the alarm time`() {
        assertEquals(Decision.NotCheckedIn, Decider.decide(day(), notCheckedIn, nine, null))
    }

    @Test
    fun `checked in stops the alarms`() {
        assertEquals(Decision.CheckedIn, Decider.decide(day(), checkedIn, nine, null))
    }

    @Test
    fun `checked in wins over a holiday`() {
        assertEquals(Decision.CheckedIn, Decider.decide(day(dayOffType = 1), checkedIn, nine, null))
    }

    @Test
    fun `a holiday stops the alarms`() {
        assertEquals(Decision.NoWork, Decider.decide(day(dayOffType = 1), notCheckedIn, nine, null))
    }

    @Test
    fun `a new morning half day reschedules the alarm`() {
        val morningOff = day(vacations = listOf(half("09:00", "14:00")))
        assertEquals(Decision.Reschedule(LocalTime.of(14, 0)), Decider.decide(morningOff, notCheckedIn, nine, null))
    }

    @Test
    fun `the rescheduled alarm then checks normally`() {
        val morningOff = day(vacations = listOf(half("09:00", "14:00")))
        assertEquals(Decision.NotCheckedIn, Decider.decide(morningOff, notCheckedIn, LocalTime.of(14, 0), null))
    }

    @Test
    fun `missing data is undetermined`() {
        assertTrue(Decider.decide(day(planStartAt = null), notCheckedIn, nine, null) is Decision.Undetermined)
    }
}
