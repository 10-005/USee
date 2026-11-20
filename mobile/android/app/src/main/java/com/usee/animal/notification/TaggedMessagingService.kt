package com.usee.animal.notification

import android.content.Intent
import io.invertase.firebase.messaging.ReactNativeFirebaseMessagingService

internal fun pushTag(value: (String) -> String?): String? {
  if (value("notificationType") == "CHAT") {
    val room = value("roomSeq") ?: return null
    val message = value("messageSeq") ?: return null
    return "usee:chat:$room:$message"
  }
  return value("notificationSeq")?.let { "usee:notification:$it" }
}

class TaggedMessagingService : ReactNativeFirebaseMessagingService() {
  override fun handleIntent(intent: Intent) {
    // Firebase uses this tag and ID zero for automatically displayed notifications.
    pushTag(intent::getStringExtra)?.let { intent.putExtra("gcm.n.tag", it) }
    super.handleIntent(intent)
  }
}
