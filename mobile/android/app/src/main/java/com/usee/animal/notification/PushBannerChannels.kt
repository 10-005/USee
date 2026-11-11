package com.usee.animal.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object PushBannerChannels {
  const val CHANNEL_ID = "usee_push"

  fun ensure(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(CHANNEL_ID, "알림", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "댓글, 입양, 채팅 알림"
        enableVibration(true)
      }
    manager.createNotificationChannel(channel)
  }
}
