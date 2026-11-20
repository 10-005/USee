package com.usee.animal.ar

import android.content.Context
import android.widget.FrameLayout

/** React lays out this host, but does not service layout requests from native children. */
open class SceneHostView(context: Context) : FrameLayout(context) {
  private val layoutChildren = Runnable {
    if (isAttachedToWindow && width > 0 && height > 0) {
      measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
      )
      layout(left, top, right, bottom)
    }
  }

  override fun requestLayout() {
    super.requestLayout()
    removeCallbacks(layoutChildren)
    post(layoutChildren)
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(layoutChildren)
    super.onDetachedFromWindow()
  }
}
