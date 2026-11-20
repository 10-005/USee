package com.usee.animal

import android.view.MotionEvent
import android.view.ViewConfiguration
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.views.view.ReactViewManager
import kotlin.math.abs
import kotlin.math.max

class PostTabPageManager : ReactViewManager() {
  override fun getName() = "PostTabPage"
  override fun createViewInstance(context: ThemedReactContext): ReactViewGroup = PostTabPage(context)
}

private class PostTabPage(context: ThemedReactContext) : ReactViewGroup(context) {
  private enum class Axis { UNDECIDED, VERTICAL, HORIZONTAL }
  private var axis = Axis.UNDECIDED
  private var startX = 0f
  private var startY = 0f
  private val slop = ViewConfiguration.get(context).scaledTouchSlop

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        startX = event.rawX
        startY = event.rawY
        axis = Axis.UNDECIDED
        parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_MOVE -> if (axis == Axis.UNDECIDED) {
        val dx = abs(event.rawX - startX)
        val dy = abs(event.rawY - startY)
        if (max(dx, dy) >= slop) {
          axis = if (dx > 1.7f * dy) Axis.HORIZONTAL else Axis.VERTICAL
          parent?.requestDisallowInterceptTouchEvent(axis == Axis.VERTICAL)
        }
      }
    }
    val handled = super.dispatchTouchEvent(event)
    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
      parent?.requestDisallowInterceptTouchEvent(axis != Axis.HORIZONTAL)
    } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      parent?.requestDisallowInterceptTouchEvent(false)
    }
    return handled
  }
}
