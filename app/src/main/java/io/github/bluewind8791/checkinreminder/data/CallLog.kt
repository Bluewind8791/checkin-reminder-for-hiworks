package io.github.bluewind8791.checkinreminder.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.io.File
import java.time.LocalDateTime

/**
 * History of background calls in `files/calls.log`, one line per call: time, kind, result, standby bucket.
 * Shows whether the keepalive keeps running every few hours under battery optimization.
 * Read it with `adb shell run-as <package> cat files/calls.log`. Holds no cookie and no response body.
 */
object CallLog {
    private const val FILE = "calls.log"
    private const val MAX_LINES = 500

    @Synchronized
    fun append(context: Context, at: LocalDateTime, kind: String, result: String) {
        // Standby buckets exist from API 28; minSdk is 26.
        val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(UsageStatsManager::class.java).appStandbyBucket.toString()
        } else {
            "-"
        }
        val file = File(context.filesDir, FILE)
        val lines = if (file.exists()) file.readLines() else emptyList()
        file.writeText((lines + "$at $kind $result bucket=$bucket").takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }
}
