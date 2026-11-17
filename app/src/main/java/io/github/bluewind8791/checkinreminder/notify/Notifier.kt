package io.github.bluewind8791.checkinreminder.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.bluewind8791.checkinreminder.R
import io.github.bluewind8791.checkinreminder.domain.Notice
import io.github.bluewind8791.checkinreminder.ui.LoginActivity
import java.time.format.DateTimeFormatter

/** Notification channels and texts. */
object Notifier {
    private const val CHANNEL_CHECKIN = "checkin"
    private const val CHANNEL_ACCOUNT = "account"

    // One fixed ID per kind: a new notification replaces the old one of the same kind.
    private const val ID_CHECKIN = 1
    private const val ID_LOGIN = 2
    private const val ID_FAILED = 3

    /**
     * The Hiworks app (seen on a device on 2026-11-11).
     * Must match the `<queries>` entry in the manifest.
     */
    private const val HIWORKS_PACKAGE = "kr.co.hiworks.office"
    private const val HIWORKS_WEB = "https://office.hiworks.com/"
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun show(context: Context, notice: Notice) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CHECKIN, "출근 알림", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_ACCOUNT, "계정·오류", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
        when (notice) {
            is Notice.CheckIn -> post(
                context, manager, CHANNEL_CHECKIN, ID_CHECKIN,
                title = "출근 체크를 아직 안 했습니다",
                text = "${notice.start.format(TIME)} 출근, ${notice.minutesBefore}분 전입니다. 누르면 하이웍스 앱을 엽니다.",
                tap = hiworks(context),
            )
            Notice.Login -> post(
                context, manager, CHANNEL_ACCOUNT, ID_LOGIN,
                title = "다시 로그인하세요",
                text = "세션이 만료돼 출근 여부를 확인할 수 없습니다.",
                tap = Intent(context, LoginActivity::class.java),
            )
            is Notice.Failed -> post(
                context, manager, CHANNEL_ACCOUNT, ID_FAILED,
                title = "출근 여부를 확인하지 못했습니다",
                text = "${if (notice.network) "네트워크 오류" else "응답 오류"}입니다. 하이웍스 앱에서 직접 확인하세요.",
                tap = hiworks(context),
            )
        }
    }

    /** Without the notification permission (API 33 and later), the system drops the notification. */
    private fun post(
        context: Context,
        manager: NotificationManager,
        channel: String,
        id: Int,
        title: String,
        text: String,
        tap: Intent,
    ) {
        val open = PendingIntent.getActivity(
            context, id, tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    /** The Hiworks app, or the Hiworks web when the app is not installed. */
    private fun hiworks(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(HIWORKS_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(HIWORKS_WEB))
}
