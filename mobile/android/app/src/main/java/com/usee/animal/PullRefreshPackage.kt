package com.usee.animal

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.views.view.ReactViewManager

class PullRefreshPackage : ReactPackage {
  override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> = emptyList()

  override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
    listOf(object : ReactViewManager() {
      override fun getName() = "PullRefreshContainer"
      override fun createViewInstance(context: ThemedReactContext): ReactViewGroup = PullRefreshContainer(context)
    })
}

// Follow the native refresh indicator, which owns the gesture even when a card is touched.
private class PullRefreshContainer(context: ThemedReactContext) :
  ReactViewGroup(context), ViewTreeObserver.OnPreDrawListener {
  private var refresh: SwipeRefreshLayout? = null

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    viewTreeObserver.addOnPreDrawListener(this)
  }

  override fun onDetachedFromWindow() {
    viewTreeObserver.removeOnPreDrawListener(this)
    refresh = null
    super.onDetachedFromWindow()
  }

  override fun onPreDraw(): Boolean {
    val layout = refresh?.takeIf { it.isAttachedToWindow } ?: findRefresh(this)
    refresh = layout
    if (layout != null) {
      val content = (0 until layout.childCount).map { layout.getChildAt(it) }.firstOrNull { it is ViewGroup }
      val indicator = (0 until layout.childCount).map { layout.getChildAt(it) }.firstOrNull { it !== content }
      if (content != null && indicator != null) {
        val distance = if (indicator.visibility == View.VISIBLE)
          (indicator.top - layout.progressViewStartOffset).coerceAtLeast(0) else 0
        content.translationY = distance * 0.55f * indicator.scaleY
      }
    }
    return true
  }

  private fun findRefresh(group: ViewGroup): SwipeRefreshLayout? {
    for (index in 0 until group.childCount) {
      val child = group.getChildAt(index)
      if (child is SwipeRefreshLayout) return child
      if (child is ViewGroup) findRefresh(child)?.let { return it }
    }
    return null
  }
}
