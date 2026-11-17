package io.github.bluewind8791.checkinreminder.alarm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.bluewind8791.checkinreminder.data.ApiResult
import io.github.bluewind8791.checkinreminder.data.CallLog
import io.github.bluewind8791.checkinreminder.data.CookieStore
import io.github.bluewind8791.checkinreminder.data.HiworksClient
import io.github.bluewind8791.checkinreminder.data.SettingsStore
import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import io.github.bluewind8791.checkinreminder.domain.CheckAlarm
import io.github.bluewind8791.checkinreminder.domain.Decider
import io.github.bluewind8791.checkinreminder.domain.Decision
import io.github.bluewind8791.checkinreminder.domain.Notice
import io.github.bluewind8791.checkinreminder.domain.Then
import io.github.bluewind8791.checkinreminder.notify.Notifier
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The work behind each alarm. One run at a time, so two alarms never
 * interleave their reads and writes of the alarm state.
 * Logs only the API result kind, never a cookie or a body.
 */
object AlarmWork {
    private const val TAG = "AlarmWork"

    private const val KEEPALIVE_HOURS = 3L
    private const val RETRY_HOURS = 1L

    /** Two calls of at most 4 seconds each fit in the 9-second limit of an alarm. */
    private const val CALL_TIMEOUT_SECONDS = 4L

    private val mutex = Mutex()

    /** The keepalive call. Also runs after a login and on reboot. */
    suspend fun keepAlive(context: Context) = mutex.withLock { keepAlive(context, SettingsStore(context)) }

    /**
     * Runs the keepalive call when no keepalive alarm is set, as after a force stop.
     * Not after a 401: the user must log in first.
     */
    suspend fun ensureScheduled(context: Context) = mutex.withLock {
        val settings = SettingsStore(context)
        if (!AlarmScheduler.hasKeepAlive(context) && settings.read().lastCallResult != "401") {
            keepAlive(context, settings)
        }
    }

    private suspend fun keepAlive(context: Context, settings: SettingsStore) {
        val client = client(context) ?: return stop(context, settings)
        val today = LocalDate.now()
        val result = client.calendar(today, today.plusDays(1))
        val now = LocalDateTime.now()
        Log.i(TAG, "keepalive: ${label(result)}")
        settings.setLastCall(now, label(result))
        CallLog.append(context, now, "keepalive", label(result))
        when (result) {
            is ApiResult.Success -> {
                settings.setDays(result.value)
                AlarmScheduler.setKeepAlive(context, now.plusHours(KEEPALIVE_HOURS))
                replan(context, settings)
            }
            ApiResult.SessionExpired -> {
                Notifier.show(context, Notice.Login)
                stop(context, settings)
            }
            is ApiResult.Failed -> {
                // Try again before the session's idle timeout (4 hours or more).
                AlarmScheduler.setKeepAlive(context, now.plusHours(RETRY_HOURS))
                replan(context, settings)
            }
        }
    }

    suspend fun check(context: Context, alarm: CheckAlarm) = mutex.withLock {
        val settings = SettingsStore(context)
        val client = client(context) ?: return@withLock stop(context, settings)
        val result = decide(client, settings, alarm)
        val now = LocalDateTime.now()
        Log.i(TAG, "check stage ${alarm.stage}: ${label(result)}")
        settings.setLastCall(now, label(result))
        CallLog.append(context, now, "check${alarm.stage}", label(result))
        val outcome = AlarmPlan.afterCheck(alarm, result, now)
        outcome.notice?.let { Notifier.show(context, it) }
        when (val then = outcome.then) {
            is Then.Stage -> {
                AlarmScheduler.setCheck(context, then.alarm)
                settings.setNextCheck(then.alarm)
            }
            Then.DayDone -> {
                settings.setDoneDate(alarm.date)
                replan(context, settings)
            }
            Then.Replan -> replan(context, settings)
            Then.Stop -> stop(context, settings)
        }
    }

    /** Plans the check alarm again from the cached schedule. Used after the start time setting changes. */
    suspend fun replan(context: Context) = mutex.withLock { replan(context, SettingsStore(context)) }

    /** Clears the cookie, the alarms, and the alarm state. */
    suspend fun logout(context: Context) = mutex.withLock {
        AlarmScheduler.cancelAll(context)
        CookieStore(context).clear()
        SettingsStore(context).clearSession()
    }

    private suspend fun decide(client: HiworksClient, settings: SettingsStore, alarm: CheckAlarm): ApiResult<Decision> {
        val days = when (val result = client.calendar(alarm.date, alarm.date.plusDays(1))) {
            is ApiResult.Success -> result.value
            ApiResult.SessionExpired -> return ApiResult.SessionExpired
            is ApiResult.Failed -> return ApiResult.Failed(result.reason)
        }
        settings.setDays(days)
        val day = days.firstOrNull { it.workDate == alarm.date.toString() }
            ?: return ApiResult.Failed("no calendar entry")
        val info = when (val result = client.workInfo()) {
            is ApiResult.Success -> result.value
            ApiResult.SessionExpired -> return ApiResult.SessionExpired
            is ApiResult.Failed -> return ApiResult.Failed(result.reason)
        }
        return ApiResult.Success(Decider.decide(day, info, alarm.start, settings.read().manualStart))
    }

    private suspend fun replan(context: Context, settings: SettingsStore) {
        val state = settings.read()
        val next = AlarmPlan.next(state.days, state.manualStart, LocalDateTime.now(), state.doneDate, state.firstMinutes)
        if (next != null && AlarmScheduler.setCheck(context, next)) {
            settings.setNextCheck(next)
        } else {
            AlarmScheduler.cancelCheck(context)
            settings.setNextCheck(null)
        }
    }

    private suspend fun stop(context: Context, settings: SettingsStore) {
        AlarmScheduler.cancelAll(context)
        settings.setNextCheck(null)
    }

    private suspend fun client(context: Context): HiworksClient? =
        CookieStore(context).load()?.let { HiworksClient.create(it, CALL_TIMEOUT_SECONDS) }

    private fun label(result: ApiResult<*>): String = when (result) {
        is ApiResult.Success -> "200"
        ApiResult.SessionExpired -> "401"
        is ApiResult.Failed -> result.reason
    }
}
