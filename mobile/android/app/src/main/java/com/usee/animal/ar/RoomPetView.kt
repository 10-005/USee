package com.usee.animal.ar

import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation

/** Non-AR scene: one model, fixed camera, no touch controls or camera session. */
class RoomPetView(private val reactContext: ReactContext) : FrameLayout(reactContext) {
  private var scene: SceneView? = null
  private var active = false
  private var petId = ""
  private var returningFromWalk = false
  private var visibilityRevision = 0

  init {
    isClickable = true
    setOnClickListener {
      UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
        PetPressEvent(UIManagerHelper.getSurfaceId(this), id),
      )
    }
  }

  fun setReturningFromWalk(value: Boolean) {
    if (returningFromWalk == value) return
    returningFromWalk = value
    // A late prop update must not leave an already-loaded model in SitIdle.
    if (value) stop()
  }

  fun setPetId(value: String) {
    if (petId == value) return
    petId = value
    stop()
  }

  fun setActive(value: Boolean) {
    val request = ++visibilityRevision
    active = value
    if (value) {
      visibility = VISIBLE
    } else {
      visibility = INVISIBLE
      scene?.visibility = INVISIBLE
      if (!isAttachedToWindow) {
        stop()
        emitHidden()
        return
      }
      // Commit an invisible SurfaceView before potentially expensive GPU teardown.
      // Navigation resumes only after teardown, never while its old surface is visible.
      postOnAnimation {
        postOnAnimation {
          if (!active && visibilityRevision == request) {
            stop()
            emitHidden()
          }
        }
      }
    }
  }

  // React props are delivered one by one. Create the scene only after all of them
  // (including the return flag) belong to the same React render.
  fun onPropsUpdated() = start()

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    start()
  }

  override fun onDetachedFromWindow() {
    stop()
    super.onDetachedFromWindow()
  }

  fun dispose() {
    visibilityRevision++
    visibility = INVISIBLE
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
    // Match the expanded 900px viewport to the original 554px framing. The
    // wider lens adds headroom while preserving model size and the bed anchor.
    val extraWorldHeight = (900f / 554f - 1f) * (2.4f * 24f / 28f)
    view.cameraNode.focalLength = 28.0 * 554.0 / 900.0
    view.cameraNode.position = Position(0f, 0.35f + extraWorldHeight / 2f, 2.4f)
    addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    // Temporary shared Shiba model for all IDs; keep petId for future breed assets.
    view.modelLoader.loadModelInstanceAsync("models/dog.glb") { instance ->
      if (instance == null || scene !== view) return@loadModelInstanceAsync
      val node = DogModelNode(instance, manualPlayback = true)
      node.isVisible = false
      node.scale = node.scale * 2.5f
      node.isShadowCaster = false
      node.position = Position(0f, -node.scale.y * (node.center.y - node.halfExtent.y), 0f)
      view.addChildNode(node)
      var downX = 0f
      var downY = 0f
      val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
      view.onTouchEvent = { event, hit ->
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
          MotionEvent.ACTION_UP -> {
            val dx = event.x - downX
            val dy = event.y - downY
            if (dx * dx + dy * dy <= slop * slop && hit?.node === node) performClick()
          }
        }
        true
      }
      val behavior = RoomPetBehavior(node.durations, returningFromWalk = returningFromWalk)
      node.play(behavior.animation)
      node.isVisible = true
      var lastFrame = 0L
      var yaw = if (returningFromWalk) -135f else 0f
      node.rotation = Rotation(y = yaw)
      UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
        ReadyEvent(UIManagerHelper.getSurfaceId(this), id),
      )
      view.onFrame = { now ->
        // No physics here: use wall time so a delayed frame cannot leave the
        // behavior timer behind a clip that has already reached its final pose.
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceAtLeast(0f)
        lastFrame = now
        node.play(behavior.update(dt))
        val targetYaw = when {
          behavior.walking -> -135f
          behavior.resting -> -25f
          else -> 0f
        }
        yaw += (targetYaw - yaw) * (dt * 3f).coerceAtMost(1f)
        node.rotation = Rotation(y = yaw)
      }
    }
  }

  private fun stop() {
    val view = scene ?: return
    scene = null
    view.visibility = INVISIBLE
    view.onFrame = null
    removeView(view)
    view.destroy()
  }

  private fun emitHidden() {
    UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
      HiddenEvent(UIManagerHelper.getSurfaceId(this), id),
    )
  }

  private class HiddenEvent(surfaceId: Int, viewId: Int) : Event<HiddenEvent>(surfaceId, viewId) {
    override fun getEventName() = "topHidden"
    override fun getEventData() = Arguments.createMap()
  }

  private class ReadyEvent(surfaceId: Int, viewId: Int) : Event<ReadyEvent>(surfaceId, viewId) {
    override fun getEventName() = "topReady"
    override fun getEventData() = Arguments.createMap()
  }

  private class PetPressEvent(surfaceId: Int, viewId: Int) : Event<PetPressEvent>(surfaceId, viewId) {
    override fun getEventName() = "topPetPress"
    override fun getEventData() = Arguments.createMap()
  }
}
