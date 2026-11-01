package com.usee.animal.ar

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode

/**
 * 시바견 모델: "Shiba Inu" by Quaternius, CC0.
 * https://poly.pizza/m/y4wdQpg767
 */
class ArFloorView(
  private val reactContext: ReactContext,
) : FrameLayout(reactContext) {
  private var sceneView: ARSceneView? = null
  private var dogNode: ModelNode? = null
  private var currentClip = ""
  private var modelReady = false
  private var active = false
  private var disposed = false
  private var lastEventKey = ""
  private val leader = DogLeader()
  private val scratch = SessionScratch()

  fun setActive(next: Boolean) {
    if (active == next) {
      return
    }
    active = next
    if (next) {
      start()
    } else {
      stop()
    }
  }

  fun dispose() {
    if (disposed) {
      return
    }
    disposed = true
    active = false
    stop()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (active) {
      start()
    }
  }

  override fun onDetachedFromWindow() {
    stop()
    super.onDetachedFromWindow()
  }

  private fun start() {
    if (disposed || sceneView != null) {
      return
    }
    val activity = reactContext.currentActivity as? ComponentActivity ?: return
    val view = FloorSceneView(
      context = activity,
      sharedLifecycle = activity.lifecycle,
      sessionConfiguration = { session, config ->
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
          Config.DepthMode.AUTOMATIC
        } else {
          Config.DepthMode.DISABLED
        }
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
      },
    ).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      onSessionUpdated = { session, frame -> handleFrame(session, frame) }
    }
    sceneView = view
    addView(view)
    view.modelLoader.loadModelInstanceAsync("models/dog.glb") { instance ->
      if (instance == null || disposed || sceneView !== view) {
        return@loadModelInstanceAsync
      }
      view.post {
        if (disposed || sceneView !== view) {
          return@post
        }
        val node = ModelNode(
          modelInstance = instance,
          autoAnimate = false,
          scaleToUnits = 0.9f,
          centerOrigin = Position(y = -1f),
        )
        node.isVisible = false
        view.addChildNode(node)
        dogNode = node
        modelReady = true
        playClip(node, "Idle")
      }
    }
  }

  private fun stop() {
    modelReady = false
    dogNode = null
    currentClip = ""
    val view = sceneView ?: return
    sceneView = null
    removeView(view)
    view.destroy()
  }

  private fun handleFrame(session: Session, frame: Frame) {
    val shown = updateDog(
      session = session,
      frame = frame,
      leader = leader,
      scratch = scratch,
      modelReady = modelReady,
    )
    val node = dogNode
    if (node != null) {
      node.isVisible = shown.dog.visible
      if (shown.dog.visible) {
        node.position = Position(shown.dog.x, shown.dog.y, shown.dog.z)
        node.rotation = Rotation(y = shown.dog.yawDegrees)
        playClip(node, shown.dog.clip)
      }
    }
    sceneView?.planeRenderer?.isVisible = !shown.dog.visible
    emit(shown.status, shown.dog.visible, shown.dog.clip)
  }

  private fun playClip(node: ModelNode, clip: String) {
    if (clip == currentClip) {
      return
    }
    if (currentClip.isNotEmpty()) {
      node.stopAnimation(currentClip)
    }
    node.playAnimation(clip, loop = true)
    currentClip = clip
  }

  private fun emit(status: String, visible: Boolean, clip: String) {
    if (disposed) {
      return
    }
    val key = "$status|$visible|$clip"
    if (key == lastEventKey) {
      return
    }
    lastEventKey = key
    val viewId = id
    if (viewId == NO_ID) {
      return
    }
    post {
      if (disposed) {
        return@post
      }
      val surfaceId = UIManagerHelper.getSurfaceId(this)
      UIManagerHelper.getEventDispatcherForReactTag(reactContext, viewId)?.dispatchEvent(
        TrackingEvent(
          surfaceId = surfaceId,
          viewId = viewId,
          status = status,
          visible = visible,
          clip = clip,
        ),
      )
    }
  }

  /**
   * SceneView 2.3.3 calls `display.rotation` during layout. React Native can lay the view out
   * before it is attached to a window, and `display` is null then.
   */
  private class FloorSceneView(
    context: Context,
    sharedLifecycle: Lifecycle,
    sessionConfiguration: ((Session, Config) -> Unit)?,
  ) : ARSceneView(
    context = context,
    sharedLifecycle = sharedLifecycle,
    sessionConfiguration = sessionConfiguration,
  ) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
      val rotation = display?.rotation ?: return
      session?.setDisplayGeometry(rotation, width, height)
    }
  }

  private class TrackingEvent(
    surfaceId: Int,
    viewId: Int,
    private val status: String,
    private val visible: Boolean,
    private val clip: String,
  ) : Event<TrackingEvent>(surfaceId, viewId) {
    override fun getEventName(): String = "topTracking"

    override fun canCoalesce(): Boolean = true

    override fun getEventData() = Arguments.createMap().apply {
      putString("status", status)
      putBoolean("visible", visible)
      putDouble("x", 0.0)
      putDouble("y", 0.0)
      putDouble("scale", 1.0)
      putDouble("facing", 1.0)
      putString("clip", clip)
    }
  }
}
