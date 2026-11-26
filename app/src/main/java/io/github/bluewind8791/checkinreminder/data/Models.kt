package io.github.bluewind8791.checkinreminder.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET hr-work-api.office.hiworks.com/v4/my-work-data-calendar`. */
@Serializable
data class CalendarResponse(val data: CalendarData)

@Serializable
data class CalendarData(
    @SerialName("user_work_data") val userWorkData: List<CalendarDay> = emptyList(),
)

@Serializable
data class CalendarDay(
    @SerialName("work_date") val workDate: String,
    /** 0 on a working day. Not 0 on a weekend or a holiday. */
    @SerialName("day_off_type") val dayOffType: Int = 0,
    /** Scheduled start time, such as "09:00". Missing when the company has no work type set. */
    @SerialName("plan_start_at") val planStartAt: String? = null,
    /** Scheduled end time, such as "18:00". */
    @SerialName("plan_end_at") val planEndAt: String? = null,
    /** Field names come from the reference implementation. Not yet seen in a real response. */
    @SerialName("vacation_data") val vacationData: List<Vacation> = emptyList(),
)

@Serializable
data class Vacation(
    /** "D" for a full day. "H" for a half day. */
    @SerialName("time_type") val timeType: String,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
)

/** Response of `GET hr-timecheck-api.office.hiworks.com/v4/web/user-work-info`. */
@Serializable
data class WorkInfoResponse(val data: WorkInfo)

@Serializable
data class WorkInfo(
    val date: String,
    /** "0000-00-00 00:00:00" before the check-in (measured 2026-11-11). */
    @SerialName("start_at") val startAt: String? = null,
    /** "0000-00-00 00:00:00" before the check-out. */
    @SerialName("end_at") val endAt: String? = null,
    /** "N" when the company does not use the check-out. */
    @SerialName("use_end_check") val useEndCheck: String? = null,
    val details: List<WorkDetail> = emptyList(),
)

@Serializable
data class WorkDetail(
    /** "start" for a check-in record. */
    val type: String? = null,
)
