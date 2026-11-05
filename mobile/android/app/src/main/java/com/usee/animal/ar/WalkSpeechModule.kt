package com.usee.animal.ar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

/** Foreground walk-only speech sessions. Back off instead of spinning on service errors. */
class WalkSpeechModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context), LifecycleEventListener {
  private val handler = Handler(Looper.getMainLooper())
  private var recognizer: SpeechRecognizer? = null
  private var enabled = false
  private var generation = 0
  private var failures = 0
  private var preferOnDevice = true
  private val restart = Runnable { listen() }

  init { context.addLifecycleEventListener(this) }
  override fun getName() = "WalkSpeech"
  @ReactMethod fun addListener(eventName: String) = Unit
  @ReactMethod fun removeListeners(count: Double) = Unit

  private fun emit(status: String, transcript: String = "", reason: String? = null, errorCode: Int? = null) {
    if (!context.hasActiveReactInstance()) return
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("WalkSpeechResult", Arguments.createMap().apply {
        putString("status", status)
        putString("transcript", transcript)
        reason?.let { putString("reason", it) }
        errorCode?.let { putInt("errorCode", it) }
      })
  }

  @ReactMethod fun start() { handler.post {
    if (enabled) return@post
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      emit("permissionDenied"); return@post
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      emit("unavailable"); return@post
    }
    enabled = true
    failures = 0
    preferOnDevice = true
    listen()
  } }

  private fun listen() {
    if (!enabled) return
    val session = ++generation
    recognizer?.destroy()
    recognizer = null
    try {
      val speech = if (preferOnDevice && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
      else SpeechRecognizer.createSpeechRecognizer(context)
      recognizer = speech
      speech.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { if (session == generation && enabled) emit("listening") }
        override fun onResults(results: Bundle?) {
          if (session != generation || !enabled) return
          failures = 0
          emit("recognized", results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
          schedule(350)
        }
        override fun onError(error: Int) {
          if (session != generation || !enabled) return
          if (Build.VERSION.SDK_INT >= 31 && preferOnDevice &&
            (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
            preferOnDevice = false
            emit("retrying", reason = "language", errorCode = error)
            schedule(500)
            return
          }
          if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            shutdown(); emit("permissionDenied"); return
          }
          val idle = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
          val reason = when (error) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
            SpeechRecognizer.ERROR_AUDIO -> "audio"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "rateLimit"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language"
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "service"
            else -> "unknown"
          }
          if (!idle && ++failures >= 3) { shutdown(); emit("error", reason = reason, errorCode = error); return }
          emit(if (idle) "waiting" else "retrying", reason = reason, errorCode = error)
          schedule(if (idle) 500 else (1000L * failures).coerceAtMost(5000))
        }
        override fun onBeginningOfSpeech() {
          if (session == generation && enabled) emit("hearing")
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
      })
      speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      })
    } catch (_: Exception) { shutdown(); emit("error") }
  }

  private fun schedule(delay: Long) {
    // Ignore any late callbacks from a completed session.
    generation++
    handler.removeCallbacks(restart)
    if (enabled) handler.postDelayed(restart, delay)
  }
  private fun shutdown() {
    enabled = false
    generation++
    handler.removeCallbacks(restart)
    recognizer?.cancel()
    recognizer?.destroy()
    recognizer = null
  }
  @ReactMethod fun stop() { handler.post { shutdown() } }
  override fun onHostResume() = Unit
  override fun onHostPause() { shutdown() }
  override fun onHostDestroy() { shutdown() }
  override fun invalidate() {
    context.removeLifecycleEventListener(this)
    handler.post { shutdown() }
    super.invalidate()
  }
}
