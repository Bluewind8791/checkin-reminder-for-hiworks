package io.github.bluewind8791.checkinreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sets the alarms again after a reboot, an app update, or a clock change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // The keepalive call plans every alarm. Run it now: the session may have ended while the device was off.
        runAsync { AlarmWork.keepAlive(app) }
    }
}
