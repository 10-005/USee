package com.usee.animal.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import java.util.UUID
import org.json.JSONObject
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.usee.animal.R

class PushBannerModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = "PushBanner"

  @ReactMethod
  fun dismissNotification(seq: String) {
    reactApplicationContext.getSystemService(NotificationManager::class.java)
      .cancel("usee:notification:$seq", 0)
  }

  @ReactMethod
  fun dismissChat(room: String, through: String) {
    val lastRead = through.toLongOrNull() ?: return
    val manager = reactApplicationContext.getSystemService(NotificationManager::class.java)
    val prefix = "usee:chat:$room:"
    for (entry in manager.activeNotifications) {
      val tag = entry.tag ?: continue
      if (!tag.startsWith(prefix)) continue
      val message = tag.removePrefix(prefix).toLongOrNull() ?: continue
      if (message <= lastRead) manager.cancel(tag, entry.id)
    }
  }

  @ReactMethod
  fun show(title: String, body: String, payload: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      PushBannerChannels.ensure(context)
      val launchIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
          flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
          action = Intent.ACTION_VIEW
          // Unique URI keeps each notification's PendingIntent and destination independent.
          data = Uri.parse("usee://notification/${UUID.randomUUID()}?payload=${Uri.encode(payload)}")
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
      val data = JSONObject(payload).optJSONObject("data")
      val tag = pushTag { key -> data?.optString(key)?.takeIf { it.isNotEmpty() } }
      manager.notify(tag ?: UUID.randomUUID().toString(), 0, notification)
      promise.resolve(null)
    } catch (error: Exception) {
      promise.reject("PUSH_BANNER_FAILED", "알림 배너를 표시하지 못했습니다.", error)
    }
  }
}
