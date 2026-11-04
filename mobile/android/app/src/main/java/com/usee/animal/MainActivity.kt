package com.usee.animal

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.swmansion.rnscreens.fragment.restoration.RNScreensFragmentFactory

class MainActivity : ReactActivity() {
  private var dismissSplash: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    supportFragmentManager.fragmentFactory = RNScreensFragmentFactory()
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) return

    val windowRoot = window.decorView as ViewGroup
    val overlay = FrameLayout(this).apply {
      setBackgroundColor(Color.rgb(192, 214, 165))
      isClickable = true
    }
    val video = VideoView(this)
    overlay.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    // Match the video's aspect ratio while covering the entire window.
    overlay.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
      val width = right - left
      val height = bottom - top
      val scale = maxOf(width / 720.0, height / 1280.0)
      val targetWidth = kotlin.math.ceil(720 * scale).toInt()
      val targetHeight = kotlin.math.ceil(1280 * scale).toInt()
      if (video.layoutParams.width != targetWidth || video.layoutParams.height != targetHeight) {
        video.layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER)
      }
    }
    windowRoot.addView(overlay, ViewGroup.LayoutParams(-1, -1))
    val timeout = Runnable { dismissSplash?.invoke() }
    dismissSplash = {
      dismissSplash = null
      overlay.removeCallbacks(timeout)
      video.stopPlayback()
      windowRoot.removeView(overlay)
    }
    video.setOnPreparedListener { it.setVolume(0f, 0f) }
    video.setOnCompletionListener { dismissSplash?.invoke() }
    video.setOnErrorListener { _, _, _ -> dismissSplash?.invoke(); true }
    overlay.postDelayed(timeout, 8000)
    video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.usee_splash}"))
    video.start()
  }

  override fun onDestroy() {
    dismissSplash?.invoke()
    super.onDestroy()
  }
  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "USee"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
