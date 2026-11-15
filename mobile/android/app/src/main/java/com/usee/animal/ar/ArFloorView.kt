package com.usee.animal.ar

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.android.filament.LightManager
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode

/**
 * Cartoon Dog - Shiba Inu by billl90 (CGTrader), mobile mesh with baked IP animations.
 */
class ArFloorView(
  private val reactContext: ReactContext,
) : SceneHostView(reactContext) {
  private var sceneView: FloorSceneView? = null
  private val modelLoad = SceneModelLoad()
  private var cleanup: WalkCleanupView? = null
  private val careNodes = mutableMapOf<String, OwnedImageNode>()
  private data class CareRequest(val action: String, val x: Float, val y: Float)
  private var pendingCare: CareRequest? = null
  private var lastCareRequest = ""
  private var lastCanGiveCare = false

  private var traversalDebug: TraversalDebugView? = null
  private var showTraversalDebug = false
  private var dogNode: DogModelNode? = null
  private val dogHeading = DogHeadingSmoother()
  private val compass = WalkCompass(reactContext)
  private val thermal = WalkThermalMonitor(reactContext)
  fun setCompassRegion(region: String?) {
    val parts = region?.split(',') ?: return
    val latitude = parts.firstOrNull()?.toFloatOrNull() ?: return
    val longitude = parts.getOrNull(1)?.toFloatOrNull() ?: return
    compass.setLocation(latitude, longitude)
  }
  private var poseSegment = 0
  private var lastWalkPose = 0L
  private var lastDogRotationNanos = 0L
  private var shadowNode: CylinderNode? = null
  private var leash: DogLeash? = null
  private var leashHand: LeashPoint? = null
  private var leashCamera: LeashPoint? = null
  private var leashCameraRight: LeashPoint? = null
  private var leashGesture = false
  private var leashTap = false
  private var touchX = 0f
  private var touchY = 0f
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var modelReady = false
  private var active = false
  private var bagAvailable = false
  private var poopArmed = false
  private var lastPoopRequest = 0

  fun requestPoop(request: Int) {
    if (request > lastPoopRequest) {
      lastPoopRequest = request
      poopArmed = true
    }
  }

  fun setBagAvailable(available: Boolean) {
    bagAvailable = available
    cleanup?.bagAvailable = available
  }

  fun confirmCleanup(response: String) {
    val parts = response.split(':')
    val requestId = parts.firstOrNull()?.toLongOrNull() ?: return
    cleanup?.confirmCollection(requestId, parts.getOrNull(1) == "1")
  }
  private var disposed = false
  private var lastEventKey = ""
  private var pendingEventKey = ""
  private var pendingEventSince = 0L
  private var leader = DogLeader(requiresPlacement = false)
  private var lastPlacementRequest = 0
  private var lastActionRequest = ""
  private var scratch = SessionScratch()
  private var floorAnalyzer: SegFormerFloorAnalyzer? = null
  private var wasBlocked = false
  private val viewMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val cameraPoint = FloatArray(4)
  private val clipPoint = FloatArray(4)
  private val inverseProjection = FloatArray(16)

  fun requestPlacement(request: Int) {
    if (request > lastPlacementRequest) leader.requestPlacement()
    lastPlacementRequest = request
  }

  fun setShowTraversalDebug(visible: Boolean) {
    showTraversalDebug = visible
    traversalDebug?.setDisplay(showTraversalDebug)
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      if (cleanup?.hitBag(event.x, event.y) == true) return false
      leashGesture = active && dogNode?.isVisible == true && leash?.hitTest(
        event.x, event.y, viewMatrix, projectionMatrix, width, height, 24f * resources.displayMetrics.density,
      ) == true
      leashTap = leashGesture
      touchX = event.x
      touchY = event.y
    }
    return leashGesture || super.onInterceptTouchEvent(event)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!leashGesture) return super.onTouchEvent(event)
    if (event.actionMasked == MotionEvent.ACTION_MOVE &&
      kotlin.math.hypot(event.x - touchX, event.y - touchY) > touchSlop) leashTap = false
    if (event.actionMasked == MotionEvent.ACTION_UP) {
      if (leashTap && event.eventTime - event.downTime < 500L) performClick()
      leashGesture = false
      leashTap = false
    } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
      leashGesture = false
      leashTap = false
    }
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    if (active && dogNode?.isVisible == true) leader.requestRecall()
    return true
  }

  fun requestAction(request: String) {
    if (request.isNotEmpty() && request != lastActionRequest) {
      lastActionRequest = request
      val action = request.substringAfter(':')
      if (action == "look") {
        if (leader.requestAttention()) dogNode?.startAttention()
      } else {
        dogNode?.cancelAttention()
        leader.requestAction(action, visibleHeadingDegrees = dogNode?.rotation?.y)
      }
    }
  }

  fun requestCare(request: String) {
    if (!active || request.isEmpty() || request == lastCareRequest) return
    lastCareRequest = request
    val parts = request.split(':')
    val x = parts.getOrNull(2)?.toFloatOrNull() ?: return
    val y = parts.getOrNull(3)?.toFloatOrNull() ?: return
    if (x in 0f..1f && y in 0f..1f) pendingCare = CareRequest(parts[1], x, y)
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
    if (disposed || !active || !isAttachedToWindow || sceneView != null) {
      return
    }
    val activity = reactContext.currentActivity as? ComponentActivity ?: return
    compass.start()
    thermal.start()
    poseSegment++
    lastWalkPose = 0L
    // A newly created AR session has a new world coordinate system.
    leader = DogLeader(requiresPlacement = false)
    dogHeading.reset()
    lastDogRotationNanos = 0L
    scratch = SessionScratch()
    floorAnalyzer = SegFormerFloorAnalyzer(context)
    wasBlocked = false
    lastEventKey = ""
    pendingEventKey = ""
    val view = FloorSceneView(
      context = activity,
      sharedLifecycle = activity.lifecycle,
      sessionConfiguration = { session, config ->
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        scratch.depth.supported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        config.depthMode = if (scratch.depth.supported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
      },
    ).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      onSessionUpdated = { session, frame -> handleFrame(session, frame) }
      beforeDestroy = { modelLoad.cancel(); releaseSceneResources(this) }
      onFrameFailure = {
        post {
          if (sceneView === this) {
            stop()
            emit("error", false, "", false, false, false)
          }
        }
      }
    }
    sceneView = view
    val debug = TraversalDebugView(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      setDisplay(showTraversalDebug)
    }
    traversalDebug = debug
    addView(view)
    addView(debug)
    cleanup = WalkCleanupView(context, view) {
      if (active && !disposed) {
        UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
          BagPurchaseEvent(UIManagerHelper.getSurfaceId(this), id),
        )
      }
    }.also {
      it.bagAvailable = bagAvailable
      addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    modelLoad.start(view) { instance ->
      if (disposed || sceneView !== view) {
        return@start
      }
      view.post {
        if (disposed || sceneView !== view) {
          return@post
        }
        val node = DogModelNode(instance)
        val dogLeash = DogLeash(view)
        leash = dogLeash
        node.onPoseUpdated = { posedDog, time ->
          val camera = leashCamera
          val right = leashCameraRight
          val collar = if (camera != null && right != null) posedDog.leashPositionFor(camera, right) else null
          val hand = leashHand
          if (active && sceneView === view && posedDog.isVisible && collar != null && hand != null) {
            leader.updateLeashLength(dogLeash.update(LeashPoint(collar.x, collar.y, collar.z), hand, time,
              posedDog.leashBody(), thermal.reducedWorkload))
          } else {
            dogLeash.hide()
          }
        }
        node.attentionTarget = { leashCamera?.let { Position(it.x, it.y, it.z) } }
        leader = DogLeader(requiresPlacement = false, animations = DogAnimationController(node.durations))
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

  private fun releaseSceneResources(scene: ARSceneView) {
    compass.stop()
    thermal.stop()
    careNodes.values.forEach { node ->
      scene.removeChildNode(node)
      node.destroy()
    }
    careNodes.clear()
    pendingCare = null
    cleanup?.let { it.release(); removeView(it) }
    cleanup = null
    dogNode?.onPoseUpdated = null
    leash?.close()
    leash = null
    leashHand = null
    leashCamera = null
    leashCameraRight = null
    leashGesture = false
    leashTap = false
    floorAnalyzer?.close()
    floorAnalyzer = null
    modelReady = false
    dogNode = null
    shadowNode = null
  }

  private fun stop() {
    val view = sceneView ?: return
    sceneView = null
    removeView(view)
    view.destroy()
    traversalDebug?.let { removeView(it); it.release() }
    traversalDebug = null
  }

  private fun handleFrame(session: Session, frame: Frame) {
    // Rotate the physical CPU image into display orientation before semantic inference.
    val relative = frame.camera.displayOrientedPose.inverse().compose(frame.camera.pose)
    val right = relative.rotateVector(floatArrayOf(1f, 0f, 0f))
    val rotation = ((kotlin.math.round(Math.toDegrees(kotlin.math.atan2(-right[1], right[0]).toDouble()) / 90.0).toInt() * 90) + 360) % 360
    val previousDog = scratch.lastShown.dog
    floorAnalyzer?.submit(frame, rotation,
      active = showTraversalDebug || !previousDog.visible || previousDog.userMoving || previousDog.blocked || previousDog.moving,
      reducedWorkload = thermal.reducedWorkload)
    val shown = updateDog(
      session = session,
      frame = frame,
      leader = leader,
      scratch = scratch,
      modelReady = modelReady,
      floorMask = floorAnalyzer?.latest,
      semanticFailed = floorAnalyzer?.failed == true,
    )
    val blocked = shown.dog.visible && shown.dog.blocked && frame.camera.trackingState == com.google.ar.core.TrackingState.TRACKING
    if (blocked && !wasBlocked) {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(180L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(180L)
      }
    }
    wasBlocked = blocked
    val camera = frame.camera
    camera.getViewMatrix(viewMatrix, 0)
    camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
    val cameraPose = camera.displayOrientedPose
    if (camera.trackingState != TrackingState.TRACKING) {
      if (lastWalkPose != 0L) poseSegment++
      lastWalkPose = 0L
    } else if (frame.timestamp - lastWalkPose >= 250_000_000L && id != NO_ID) {
      lastWalkPose = frame.timestamp
      val pose = camera.pose
      val data = Arguments.createMap().apply {
        putInt("segment", poseSegment)
        putDouble("time", frame.timestamp / 1_000_000.0)
        putDouble("x", pose.tx().toDouble())
        putDouble("z", pose.tz().toDouble())
        compass.alignment(pose)?.let { putDouble("alignment", it) }
      }
      UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
        WalkPoseEvent(UIManagerHelper.getSurfaceId(this), id, data),
      )
    }
    if (camera.trackingState == TrackingState.TRACKING) sceneView?.updateFillLight(cameraPose)
    leashCamera = LeashPoint(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())
    val screenRight = cameraPose.rotateVector(floatArrayOf(1f, 0f, 0f))
    leashCameraRight = LeashPoint(screenRight[0], screenRight[1], screenRight[2])
    leashHand = if (camera.trackingState == com.google.ar.core.TrackingState.TRACKING &&
      android.opengl.Matrix.invertM(inverseProjection, 0, projectionMatrix, 0)) {
      // NDC (0,-1) is exactly the native AR viewport's bottom center. Keep the hand
      // 35cm in front of the camera, so it stays fixed when the phone turns or moves.
      val hand = LeashCurve.handInCamera(inverseProjection)
      val world = camera.displayOrientedPose.transformPoint(floatArrayOf(
        hand.x, hand.y, hand.z,
      ))
      LeashPoint(world[0], world[1], world[2])
    } else null
    android.opengl.Matrix.multiplyMV(cameraPoint, 0, viewMatrix, 0,
      floatArrayOf(shown.dog.x, shown.dog.y + DOG_MODEL_UNITS / 2f, shown.dog.z, 1f), 0)
    android.opengl.Matrix.multiplyMV(clipPoint, 0, projectionMatrix, 0, cameraPoint, 0)
    val onScreen = shown.dog.visible && clipPoint[3] > 0f &&
      kotlin.math.abs(clipPoint[0]) <= clipPoint[3] && kotlin.math.abs(clipPoint[1]) <= clipPoint[3]
    val careAvailable = camera.trackingState == TrackingState.TRACKING && onScreen && leader.canGiveCare
    val careRequest = pendingCare
    pendingCare = null
    if (careAvailable && careRequest != null && leashHand != null) {
      val launch = LeashCurve.screenPointInCamera(
        inverseProjection, 2f * careRequest.x - 1f, 1f - 2f * careRequest.y, 0.35f,
      )
      val origin = cameraPose.transformPoint(floatArrayOf(launch.x, launch.y, launch.z))
      leader.giveCare(careRequest.action, LeashPoint(origin[0], origin[1], origin[2]))
    }
    updateCareItem(cameraPose.tx(), cameraPose.tz())
    val node = dogNode
    val shadow = shadowNode
    val rotationNow = SystemClock.elapsedRealtimeNanos()
    val rotationDt = if (lastDogRotationNanos == 0L) 0f else (rotationNow - lastDogRotationNanos) / 1_000_000_000f
    lastDogRotationNanos = rotationNow
    if (node != null) {
      node.isVisible = shown.dog.visible
      shadow?.isVisible = shown.dog.visible
      if (shown.dog.visible) {
        node.position = Position(shown.dog.x, shown.dog.y + pawLift(node), shown.dog.z)
        val previousHeading = node.rotation.y
        val heading = dogHeading.update(shown.dog, rotationDt)
        node.rotation = Rotation(y = heading)
        shadow?.position = Position(shown.dog.x, shown.dog.y + 0.006f, shown.dog.z)
        node.play(renderTurnAnimation(
          DogAnimation(shown.dog.clip, shown.dog.clipLoop, shown.dog.animationRevision, shown.dog.animationPlaybackRate),
          previousHeading, heading, rotationDt,
          shown.dog.speedMetersPerSecond, shown.dog.blocked, shown.dog.motionPaused,
          scriptedTurn = shown.dog.spinning || shown.dog.scriptedFacing || shown.dog.turningToMove,
        ))
      } else {
        dogHeading.pause()
      }
    }
    sceneView?.planeRenderer?.isVisible = false
    if (showTraversalDebug) {
      traversalDebug?.update(frame, floorAnalyzer?.latest, scratch.planes, scratch.walls, shown.dog, scratch.userGround, scratch.depth)
    }
    if (camera.trackingState == com.google.ar.core.TrackingState.TRACKING && shown.dog.visible) {
      val dog = shown.dog
      val rightX = screenRight[0]
      val rightZ = screenRight[2]
      val now = SystemClock.elapsedRealtimeNanos()
      val obstacle = listOf(0.4f, 0.7f, 1f).any { distance ->
        val x = dog.x + rightX * distance
        val z = dog.z + rightZ * distance
        android.opengl.Matrix.multiplyMV(cameraPoint, 0, viewMatrix, 0, floatArrayOf(x, dog.y, z, 1f), 0)
        android.opengl.Matrix.multiplyMV(clipPoint, 0, projectionMatrix, 0, cameraPoint, 0)
        val onRight = clipPoint[3] > 0f && clipPoint[0] > 0f && clipPoint[0] <= clipPoint[3] &&
          kotlin.math.abs(clipPoint[1]) <= clipPoint[3]
        onRight && (scratch.depth.latest?.evidence(x, dog.y, z, now) == FloorEvidence.OBSTACLE ||
          floorAnalyzer?.latest?.let { it.isFresh(now) && it.evidenceAt(x, dog.y, z, now) == FloorEvidence.OBSTACLE } == true)
      }
      cleanup?.update(dog, camera.pose.tx(), camera.pose.tz(), viewMatrix, projectionMatrix, obstacle)
      if (poopArmed) {
        poopArmed = false
        cleanup?.arm { leader.beginPoop(rightX, rightZ) }
      }
    } else {
      dogNode?.cancelAttention()
      cleanup?.pause()
    }
    emit(shown.status, shown.dog.visible, shown.dog.clip, shown.dog.moving, onScreen,
      shown.dog.userMoving, careAvailable && leader.canGiveCare)
  }

  private fun updateCareItem(cameraX: Float, cameraZ: Float) {
    val item = leader.careItem
    careNodes.forEach { (action, node) -> node.isVisible = action == item?.action }
    if (item == null) return
    val scene = sceneView ?: return
    val (action, point, scale) = item
    val node = careNodes.getOrPut(action) {
      val bowl = action == "drink"
      OwnedImageNode(
        materialLoader = scene.materialLoader,
        imageFileLocation = if (bowl) "images/mock-adoption/img_full_water_bowl.png"
          else "images/mock-adoption/img_dog_treats.png",
        size = if (bowl) Size(0.24f, 0.186f, 0f) else Size(0.16f, 0.16f, 0f),
        normal = Direction(0f, 0f, 1f),
      ).apply {
        isShadowCaster = false
        isShadowReceiver = false
        scene.addChildNode(this)
      }
    }
    node.position = Position(point.x, point.y, point.z)
    node.scale = Scale(scale, scale, scale)
    node.rotation = Rotation(y = Math.toDegrees(
      kotlin.math.atan2(cameraX - point.x, cameraZ - point.z).toDouble(),
    ).toFloat())
  }

  private fun pawLift(node: ModelNode): Float {
    val feet = node.center.y - node.halfExtent.y
    return -node.scale.y * feet - PAW_SINK
  }

  private fun emit(status: String, visible: Boolean, clip: String, moving: Boolean, onScreen: Boolean,
    userMoving: Boolean, canGiveCare: Boolean = false) {
    if (disposed) {
      return
    }
    val cleanupRequestId = cleanup?.collectionRequestId ?: 0L
    val key = "$status|$visible|$moving|$onScreen|$userMoving|$cleanupRequestId|$canGiveCare"
    val now = SystemClock.elapsedRealtime()
    if (pendingEventKey != key) {
      pendingEventKey = key
      pendingEventSince = now
    }
    if (key == lastEventKey) {
      return
    }
    // React only needs a settled caption, not every transient tracking/clip change.
    if (lastEventKey.isNotEmpty() && lastCanGiveCare == canGiveCare && now - pendingEventSince < 300L) return
    lastEventKey = key
    lastCanGiveCare = canGiveCare
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
          onScreen = onScreen,
          userMoving = userMoving,
          cleanupRequestId = cleanupRequestId,
          canGiveCare = canGiveCare,
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
    // Keep the camera-facing side readable when the estimated key light is behind the dog.
    private val fillLight = LightNode(engine, LightManager.Type.DIRECTIONAL) {
      direction(0f, 0f, -1f)
      color(1f, 1f, 1f)
      intensity(24_000f)
      castShadows(false)
    }.also { addChildNode(it) }
    private val fillDirection = floatArrayOf(0f, 0f, -1f)
    private val worldFillDirection = FloatArray(3)

    fun updateFillLight(cameraPose: Pose) {
      cameraPose.rotateVector(fillDirection, 0, worldFillDirection, 0)
      fillLight.lightDirection = Direction(worldFillDirection[0], worldFillDirection[1], worldFillDirection[2])
    }

    var beforeDestroy: (() -> Unit)? = null
    var onFrameFailure: (() -> Unit)? = null
    private var frameFailed = false

    override fun onFrame(frameTimeNanos: Long) {
      if (isDestroyed || frameFailed || !isAttachedToWindow) return
      try {
        super.onFrame(frameTimeNanos)
      } catch (error: com.google.ar.core.exceptions.FatalException) {
        frameFailed = true
        android.util.Log.e("ArFloorView", "AR session frame failed", error)
        onFrameFailure?.invoke()
      }
    }

    override fun destroy() {
      if (isDestroyed) return
      onSessionUpdated = null
      beforeDestroy?.invoke()
      beforeDestroy = null
      removeChildNode(fillLight)
      fillLight.destroy()
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

  private class BagPurchaseEvent(surfaceId: Int, viewId: Int) : Event<BagPurchaseEvent>(surfaceId, viewId) {
    override fun getEventName() = "topBagPurchase"
    override fun canCoalesce() = false
    override fun getEventData() = Arguments.createMap()
  }

  private class WalkPoseEvent(surfaceId: Int, viewId: Int, private val data: com.facebook.react.bridge.WritableMap) :
    Event<WalkPoseEvent>(surfaceId, viewId) {
    override fun getEventName() = "topWalkPose"
    override fun getEventData() = data
  }

  private class TrackingEvent(
    surfaceId: Int,
    viewId: Int,
    private val status: String,
    private val visible: Boolean,
    private val clip: String,
    private val moving: Boolean,
    private val onScreen: Boolean,
    private val userMoving: Boolean,
    private val cleanupRequestId: Long,
    private val canGiveCare: Boolean,
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
      putBoolean("onScreen", onScreen)
      putBoolean("userMoving", userMoving)
      putDouble("cleanupRequestId", cleanupRequestId.toDouble())
      putBoolean("canGiveCare", canGiveCare)
    }
  }
}
