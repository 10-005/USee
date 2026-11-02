package com.usee.animal.ar

import android.content.Context
import android.os.SystemClock
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
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode

/**
 * Cartoon Dog - Shiba Inu by billl90 (CGTrader), mobile mesh with baked IP animations.
 */
class ArFloorView(
  private val reactContext: ReactContext,
) : FrameLayout(reactContext) {
  private var sceneView: ARSceneView? = null
  private var dogNode: DogModelNode? = null
  private var shadowNode: CylinderNode? = null
  private var modelReady = false
  private var active = false
  private var disposed = false
  private var lastEventKey = ""
  private var pendingEventKey = ""
  private var pendingEventSince = 0L
  private var leader = DogLeader(requiresPlacement = true)
  private var lastPlacementRequest = 0
  private var lastActionRequest = ""
  private var scratch = SessionScratch()

  fun requestPlacement(request: Int) {
    if (request > lastPlacementRequest) leader.requestPlacement()
    lastPlacementRequest = request
  }

  fun requestAction(request: String) {
    if (request.isNotEmpty() && request != lastActionRequest) {
      lastActionRequest = request
      leader.requestAction(request.substringAfter(':'))
    }
  }

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
    // A newly created AR session has a new world coordinate system.
    leader = DogLeader(requiresPlacement = true)
    scratch = SessionScratch()
    lastEventKey = ""
    pendingEventKey = ""
    val view = FloorSceneView(
      context = activity,
      sharedLifecycle = activity.lifecycle,
      sessionConfiguration = { _, config ->
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        // Walking uses plane polygons, so per-frame depth processing is unnecessary.
        config.depthMode = Config.DepthMode.DISABLED
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
        val node = DogModelNode(instance)
        leader = DogLeader(requiresPlacement = true, animations = DogAnimationController(node.durations))
        node.isShadowCaster = true
        node.isVisible = false
        val shadow = CylinderNode(
          engine = view.engine,
          radius = 0.225f,
          height = 0.01f,
          materialInstance = view.materialLoader.createColorInstance(
            colorOf(r = 0f, g = 0f, b = 0f, a = 0.42f),
            metallic = 0f,
            roughness = 1f,
            reflectance = 0f,
          ),
        ).apply {
          isShadowCaster = false
          isVisible = false
        }
        view.addChildNode(shadow)
        view.addChildNode(node)
        shadowNode = shadow
        dogNode = node
        modelReady = true
        node.play(DogAnimation())
      }
    }
  }

  private fun stop() {
    modelReady = false
    dogNode = null
    shadowNode = null
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
    val shadow = shadowNode
    if (node != null) {
      node.isVisible = shown.dog.visible
      shadow?.isVisible = shown.dog.visible
      if (shown.dog.visible) {
        node.position = Position(shown.dog.x, shown.dog.y + pawLift(node), shown.dog.z)
        node.rotation = Rotation(y = shown.dog.yawDegrees)
        shadow?.position = Position(shown.dog.x, shown.dog.y + 0.006f, shown.dog.z)
        node.play(DogAnimation(shown.dog.clip, shown.dog.clipLoop, shown.dog.animationRevision))
      }
    }
    sceneView?.planeRenderer?.isVisible = !shown.dog.visible
    emit(shown.status, shown.dog.visible, shown.dog.clip, shown.dog.moving)
  }

  private fun pawLift(node: ModelNode): Float {
    val feet = node.center.y - node.halfExtent.y
    return -node.scale.y * feet - PAW_SINK
  }

  private fun emit(status: String, visible: Boolean, clip: String, moving: Boolean) {
    if (disposed) {
      return
    }
    val key = "$status|$visible|$moving"
    val now = SystemClock.elapsedRealtime()
    if (pendingEventKey != key) {
      pendingEventKey = key
      pendingEventSince = now
    }
    if (key == lastEventKey) {
      return
    }
    // React only needs a settled caption, not every transient tracking/clip change.
    if (lastEventKey.isNotEmpty() && now - pendingEventSince < 300L) return
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
          moving = moving,
        ),
      )
    }
  }

  private companion object {
    const val PAW_SINK = 0.012f
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
    override fun destroy() {
      onSessionUpdated = null
      // SceneView destroys its material loader before user-created renderables.
      // Detach also calls destroy(), so release the shadow here for every exit path.
      childNodes.filterIsInstance<CylinderNode>().forEach { shadow ->
        removeChildNode(shadow)
        shadow.destroy()
      }
      super.destroy()
    }

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
    private val moving: Boolean,
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
      putBoolean("moving", moving)
    }
  }
}
