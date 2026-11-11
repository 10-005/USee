package com.usee.animal.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.usee.animal.R

class PushBannerModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = "PushBanner"

  @ReactMethod
  fun show(title: String, body: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      PushBannerChannels.ensure(context)
      val launchIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
      val pendingIntent =
        launchIntent?.let { intent ->
          PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        }
      val notification =
        NotificationCompat.Builder(context, PushBannerChannels.CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(title)
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setCategory(NotificationCompat.CATEGORY_MESSAGE)
          .setDefaults(NotificationCompat.DEFAULT_ALL)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)
          .build()
      val manager = context.getSystemService(NotificationManager::class.java)
      manager.notify(System.currentTimeMillis().toInt(), notification)
      promise.resolve(null)
    } catch (error: Exception) {
      promise.reject("PUSH_BANNER_FAILED", "알림 배너를 표시하지 못했습니다.", error)
    }
  }
}
