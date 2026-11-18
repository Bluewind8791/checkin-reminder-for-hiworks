package io.github.bluewind8791.checkinreminder.ui

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** What the alarms need from the system. Read again each time the screen resumes. */
data class Permissions(
    val notify: Boolean,
    /** Null where the user cannot turn it off: below API 31, and from API 33 (USE_EXACT_ALARM). */
    val exactAlarm: Boolean?,
    val batteryOpt: Boolean,
) {
    companion object {
        fun read(context: Context): Permissions {
            val exactAlarm = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            } else {
                null
            }
            return Permissions(
                // Covers both the API 33 permission and the app's notification switch.
                notify = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarm = exactAlarm,
                batteryOpt = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

fun Context.openNotificationSettings() =
    openOrAppDetails(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

// The app is not on the Play store, whose policy limits this request to some kinds of apps.
@SuppressLint("BatteryLife")
fun Context.requestBatteryExemption() =
    openOrAppDetails(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))

fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    openOrAppDetails(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
}

/** Some devices lack a settings screen. The app's own settings page is the fallback. */
private fun Context.openOrAppDetails(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
