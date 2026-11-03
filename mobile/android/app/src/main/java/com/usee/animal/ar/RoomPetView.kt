package com.usee.animal.ar

import android.graphics.PixelFormat
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.facebook.react.bridge.ReactContext
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation

/** Non-AR scene: one model, fixed camera, no touch controls or camera session. */
class RoomPetView(private val reactContext: ReactContext) : FrameLayout(reactContext) {
  private var scene: SceneView? = null
  private var active = false
  private var petId = ""

  fun setPetId(value: String) {
    if (petId == value) return
    petId = value
    stop()
    start()
  }

  fun setActive(value: Boolean) {
    active = value
    if (value) start() else stop()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    start()
  }

  override fun onDetachedFromWindow() {
    stop()
    super.onDetachedFromWindow()
  }

  fun dispose() {
    active = false
    stop()
  }

  private fun start() {
    if (!active || !isAttachedToWindow || petId.isEmpty() || scene != null) return
    val activity = reactContext.currentActivity as? ComponentActivity ?: return
    val view = object : SceneView(
      context = activity, sharedLifecycle = activity.lifecycle,
      isOpaque = false, cameraManipulator = null,
    ) {
      private var lastRendered = 0L
      override fun onFrame(frameTimeNanos: Long) {
        if (frameTimeNanos - lastRendered < 33_333_333L) return
        lastRendered = frameTimeNanos
        super.onFrame(frameTimeNanos)
      }
    }
    scene = view
    // Transparent SurfaceView must composite over the React Native background image.
    view.setZOrderOnTop(true)
    view.holder.setFormat(PixelFormat.TRANSLUCENT)
    view.setOnTouchListener { _, _ -> true }
    view.cameraNode.position = Position(0f, 0.35f, 2.4f)
    addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // Temporary shared Shiba model for all IDs; keep petId for future breed assets.
    view.modelLoader.loadModelInstanceAsync("models/dog.glb") { instance ->
      if (instance == null || scene !== view) return@loadModelInstanceAsync
      val node = DogModelNode(instance)
      node.isShadowCaster = false
      node.position = Position(0f, -node.scale.y * (node.center.y - node.halfExtent.y), 0f)
      view.addChildNode(node)
      val behavior = RoomPetBehavior(node.durations)
      node.play(behavior.animation)
      var lastFrame = 0L
      var yaw = 0f
      view.onFrame = { now ->
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrame = now
        node.play(behavior.update(dt))
        val targetYaw = if (behavior.resting) -25f else 0f
        yaw += (targetYaw - yaw) * (dt * 3f).coerceAtMost(1f)
        node.rotation = Rotation(y = yaw)
      }
    }
  }

  private fun stop() {
    val view = scene ?: return
    scene = null
    view.onFrame = null
    removeView(view)
    view.destroy()
  }
}
