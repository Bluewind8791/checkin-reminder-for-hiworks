package io.github.bluewind8791.checkinreminder.domain

import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import java.time.LocalDate
import java.time.LocalTime

sealed interface CheckResult {
    data class Ok(
        val date: LocalDate,
        val expected: ExpectedStart,
        val checkedIn: Boolean,
        /** Null when not checked in, or when the record has no time. */
        val checkedInAt: LocalTime?,
    ) : CheckResult

    data object SessionExpired : CheckResult
    data class Failed(val reason: String) : CheckResult
}

/** Looks up today's schedule and check-in state once. Used by the 「지금 확인」 button. */
suspend fun checkNow(client: HiworksClient, today: LocalDate, manualStart: LocalTime? = null): CheckResult {
    val days = when (val result = client.calendar(today, today)) {
        is ApiResult.Success -> result.value
        ApiResult.SessionExpired -> return CheckResult.SessionExpired
        is ApiResult.Failed -> return CheckResult.Failed(result.reason)
    }
    val day = days.firstOrNull { it.workDate == today.toString() }
        ?: return CheckResult.Failed("no calendar entry for $today")
    val info = when (val result = client.workInfo()) {
        is ApiResult.Success -> result.value
        ApiResult.SessionExpired -> return CheckResult.SessionExpired
        is ApiResult.Failed -> return CheckResult.Failed(result.reason)
    }
    return CheckResult.Ok(
        date = today,
        expected = Decider.expectedStart(day, manualStart),
        checkedIn = Decider.isCheckedIn(info),
        checkedInAt = Decider.checkInTime(info),
    )
}
