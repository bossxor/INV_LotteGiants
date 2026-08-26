package com.bossxor.lottegiants.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

object WearLiveNotifier {
    private const val CHANNEL_ID = "sajik_live"
    private const val NOTIF_ID = 2101

    fun update(context: Context, snap: SajikSnapshot) {
        if (snap.status != "LIVE") {
            cancel(context)
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.live_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "${snap.scoreLine}  ${snap.inning.ifBlank { "LIVE" }}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(tap)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val ongoing = OngoingActivity.Builder(context, NOTIF_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher)
            .setTouchIntent(tap)
            .setStatus(Status.Builder().addTemplate(text).build())
            .build()
        ongoing.apply(context)
        nm.notify(NOTIF_ID, builder.build())
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
