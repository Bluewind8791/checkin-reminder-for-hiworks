package io.github.bluewind8791.checkinreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.bluewind8791.checkinreminder.domain.AlarmKind
import io.github.bluewind8791.checkinreminder.domain.AlarmPlan
import io.github.bluewind8791.checkinreminder.domain.CheckAlarm
import io.github.bluewind8791.checkinreminder.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Sets and cancels the alarms: start checks, end checks, and the keepalive. Each kind has one alarm at a time. */
object AlarmScheduler {
    const val ACTION_CHECK = "io.github.bluewind8791.checkinreminder.CHECK"
    const val ACTION_END_CHECK = "io.github.bluewind8791.checkinreminder.END_CHECK"
    const val ACTION_KEEPALIVE = "io.github.bluewind8791.checkinreminder.KEEPALIVE"
    private const val EXTRA_DATE = "date"
    private const val EXTRA_START = "start"
    private const val EXTRA_MINUTES = "minutes_before"
    private const val TAG = "AlarmScheduler"

    /** `setAlarmClock`: not rate-limited in Doze, and wakes the device. Returns false without the permission. */
    fun setCheck(context: Context, alarm: CheckAlarm): Boolean {
        val manager = manager(context)
        if (!canSchedule(manager)) return false
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val intent = intent(context, action(alarm.kind))
            .putExtra(EXTRA_DATE, alarm.date.toString())
            .putExtra(EXTRA_START, alarm.start.toString())
            .putExtra(EXTRA_MINUTES, alarm.minutesBefore)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(millis(alarm.at), show), pending(context, intent))
        return true
    }

    /** `setExactAndAllowWhileIdle`: runs in Doze. Returns false without the permission. */
    fun setKeepAlive(context: Context, at: LocalDateTime): Boolean {
        val manager = manager(context)
        if (!canSchedule(manager)) return false
        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, millis(at), pending(context, intent(context, ACTION_KEEPALIVE)),
        )
        return true
    }

    fun cancelCheck(context: Context, kind: AlarmKind) = cancel(context, action(kind))

    fun cancelAll(context: Context) {
        cancel(context, ACTION_CHECK)
        cancel(context, ACTION_END_CHECK)
        cancel(context, ACTION_KEEPALIVE)
    }

    /** False after a cancel, and after a force stop, which drops the app's alarms. */
    fun hasKeepAlive(context: Context): Boolean = existing(context, ACTION_KEEPALIVE) != null

    /** The alarm an [ACTION_CHECK] or [ACTION_END_CHECK] intent was set for, or null when its extras are bad. */
    fun checkAlarm(intent: Intent): CheckAlarm? {
        val kind = if (intent.action == ACTION_END_CHECK) AlarmKind.End else AlarmKind.Start
        val range = if (kind == AlarmKind.End) AlarmPlan.END_MINUTES_RANGE else AlarmPlan.MINUTES_RANGE
        val date = intent.getStringExtra(EXTRA_DATE) ?: return null
        val start = intent.getStringExtra(EXTRA_START) ?: return null
        // An alarm set by a version before this extra has none. The app update replans it before it rings.
        val minutes = intent.getLongExtra(EXTRA_MINUTES, Long.MIN_VALUE).takeIf { it in range } ?: return null
        return try {
            CheckAlarm(LocalDate.parse(date), LocalTime.parse(start), minutes, kind)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * API 31-32 need SCHEDULE_EXACT_ALARM, which the user can turn off. API 33 and later have USE_EXACT_ALARM,
     * which is always granted.
     */
    fun canSchedule(manager: AlarmManager): Boolean {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (!allowed) Log.w(TAG, "exact alarms not allowed")
        return allowed
    }

    private fun cancel(context: Context, action: String) {
        val pending = existing(context, action) ?: return
        manager(context).cancel(pending)
        pending.cancel()
    }

    private fun existing(context: Context, action: String): PendingIntent? = PendingIntent.getBroadcast(
        context, 0, intent(context, action), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pending(context: Context, intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(kind: AlarmKind) = if (kind == AlarmKind.End) ACTION_END_CHECK else ACTION_CHECK

    private fun intent(context: Context, action: String) = Intent(context, AlarmReceiver::class.java).setAction(action)

    private fun manager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
