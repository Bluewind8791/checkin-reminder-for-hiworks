package io.github.bluewind8791.checkinreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs the keepalive call and the check stages when their alarms ring. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        runAsync {
            when (intent.action) {
                AlarmScheduler.ACTION_KEEPALIVE -> AlarmWork.keepAlive(app)
                AlarmScheduler.ACTION_CHECK -> AlarmScheduler.checkAlarm(intent)?.let { AlarmWork.check(app, it) }
            }
        }
    }
}

/** Keeps the receiver alive until [work] ends. */
internal fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}
