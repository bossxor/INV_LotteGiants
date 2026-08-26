package com.bossxor.lottegiants.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.Charset

class WearEventListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        SnapshotRepository.hydrate(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearPaths.EVENT) return
        val text = messageEvent.data.toString(Charset.forName("UTF-8"))
        if (text.isNotBlank()) showEventNotification(text)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearPaths.SNAPSHOT) return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            SnapshotRepository.updateFromDataMap(this, map)
        }
    }

    private fun showEventNotification(message: String) {
        val channelId = "sajik_events"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        nm.notify(message.hashCode(), notification)
    }
}
