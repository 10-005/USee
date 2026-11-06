package com.usee.animal.ar

import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.SphereNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import kotlin.math.sin
import kotlin.math.cos

/** Non-AR scene: one model, fixed camera, no touch controls or camera session. */
class RoomPetView(private val reactContext: ReactContext) : SceneHostView(reactContext) {
  private var scene: SceneView? = null
  private val modelLoad = SceneModelLoad()
  private var disposed = false
  private var active = false
  private var bathing = false
  private var sceneOpacity = 1f
  fun setSceneOpacity(value: Float) {
    sceneOpacity = value.coerceIn(0f, 1f)
    scene?.alpha = sceneOpacity
  }
  fun setBathing(value: Boolean) { bathing = value }
  private var careMotion = ""
  fun setCareMotion(value: String) { careMotion = value }
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
    disposed = true
    visibilityRevision++
    visibility = INVISIBLE
    active = false
    stop()
  }

  private fun start() {
    if (disposed || !active || !isAttachedToWindow || petId.isEmpty() || scene != null) return
    val activity = reactContext.currentActivity as? ComponentActivity ?: return
    val view = object : SceneView(
      context = activity, sharedLifecycle = activity.lifecycle,
      isOpaque = false, cameraManipulator = null,
    ) {
      private var lastRendered = 0L
      override fun destroy() {
        if (isDestroyed) return
        modelLoad.cancel()
        onFrame = null
        childNodes.filter { it is OwnedImageNode || it is SphereNode }.forEach { node ->
          removeChildNode(node)
          node.destroy()
        }
        super.destroy()
      }

      override fun onFrame(frameTimeNanos: Long) {
        // A Choreographer callback may already be dequeued when navigation destroys the scene.
        if (isDestroyed || this@RoomPetView.scene !== this || !active) return
        if (frameTimeNanos - lastRendered < 33_333_333L) return
        lastRendered = frameTimeNanos
        super.onFrame(frameTimeNanos)
      }
    }
    scene = view
    view.alpha = sceneOpacity
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
    modelLoad.start(view) { instance ->
      if (disposed || scene !== view) return@start
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
      var behavior = RoomPetBehavior(node.durations, returningFromWalk = returningFromWalk)
      node.play(behavior.animation)
      node.isVisible = true
      var lastFrame = 0L
      var yaw = if (returningFromWalk) -135f else 0f
      node.rotation = Rotation(y = yaw)
      var readySent = false
      var previousCare = ""
      var careRevision = 1000000
      var carriedToy: OwnedImageNode? = null
      val mouth = node.emptyNodes.firstOrNull { it.name == "mouth" }
      val bubbles = mutableListOf<SphereNode>()
      var bathElapsed = 0f
      var wasBathing = false
      view.onFrame = { now ->
        if (!readySent) {
          readySent = true
          // Wait until the loaded model enters the frame loop before moving the room view.
          UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)?.dispatchEvent(
            ReadyEvent(UIManagerHelper.getSurfaceId(this), id),
          )
        }
        // No physics here: use wall time so a delayed frame cannot leave the
        // behavior timer behind a clip that has already reached its final pose.
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceAtLeast(0f)
        lastFrame = now
        if (bathing && !wasBathing) {
          bathElapsed = 0f
          careRevision++
          node.play(DogAnimation("SitIdle", true, careRevision))
          if (bubbles.isEmpty()) {
            val material = view.materialLoader.createColorInstance(
              colorOf(r = 0.95f, g = 0.99f, b = 1f, a = 0.88f),
              metallic = 0f, roughness = 0.4f, reflectance = 0.5f,
            )
            repeat(24) { index ->
              bubbles.add(SphereNode(
                engine = view.engine, radius = 0.055f + (index % 4) * 0.018f,
                materialInstance = material,
              ).apply { isShadowCaster = false; isVisible = false; view.addChildNode(this) })
            }
          }
        }
        if (bathing) bathElapsed += dt
        val bodyHeight = node.halfExtent.y * node.scale.y * 2f
        bubbles.forEachIndexed { index, bubble ->
          val age = bathElapsed - (index % 8) * 0.08f
          bubble.isVisible = bathing && age >= 0f && bathElapsed < 3f
          if (bubble.isVisible) {
            val cycle = (age % 1.1f) / 1.1f
            val fadeOut = ((3f - bathElapsed) / 0.45f).coerceIn(0f, 1f)
            val size = (sin(cycle * Math.PI).toFloat() * fadeOut).coerceAtLeast(0.01f)
            bubble.scale = Scale(size)
            bubble.position = Position(
              (index % 6 - 2.5f) * bodyHeight * 0.08f + sin(age * 3f + index) * 0.025f,
              bodyHeight * (0.2f + (index / 6) * 0.065f + cycle * 0.3f),
              bodyHeight * (0.18f + (index % 3) * 0.055f),
            )
          }
        }
        if (bathing) {
          // Keep the sitting clip throughout the bath; pause idle behavior.
        } else if (careMotion.isNotEmpty()) {
          if (previousCare != careMotion) {
            careRevision++
            node.play(DogAnimation(when (careMotion) {
              "eat" -> "Eat"
              "treat" -> "Eat"
              "drink" -> "Drink"
              "fetchRun", "fetchCarry" -> "Trot"
              "fetchPick", "fetchDrop" -> "EatDrinkStart"
              else -> "Walk"
            }, careMotion != "fetchPick" && careMotion != "fetchDrop", careRevision))
          }
        } else {
          if (previousCare.isNotEmpty() || wasBathing) behavior = RoomPetBehavior(node.durations)
          node.play(behavior.update(dt))
        }
        previousCare = careMotion
        wasBathing = bathing
        val targetYaw = when {
          bathing -> 0f
          careMotion == "fetchRun" || careMotion == "fetchPick" -> -170f
          careMotion == "fetchCarry" -> 10f
          careMotion == "fetchDrop" -> 0f
          careMotion == "treat" -> 0f
          careMotion == "return" -> 25f
          careMotion == "eat" || careMotion == "drink" -> -25f
          careMotion == "approach" -> -55f
          behavior.walking -> -135f
          behavior.resting -> -25f
          else -> 0f
        }
        yaw += (targetYaw - yaw) * (dt * 3f).coerceAtMost(1f)
        node.rotation = Rotation(y = yaw)
        if (careMotion == "fetchCarry" && carriedToy == null) {
          carriedToy = OwnedImageNode(
            materialLoader = view.materialLoader,
            imageFileLocation = "models/img_dog_toy.png",
            size = Size(0.252f, 0.252f, 0f),
            normal = Direction(0f, 0f, 1f),
          ).apply { isShadowCaster = false; isVisible = false; view.addChildNode(this) }
        }
        carriedToy?.let { toy ->
          toy.isVisible = careMotion == "fetchCarry" && mouth != null
          // Follow the animated mouth bone, while keeping the sprite facing the camera.
          mouth?.let {
            val forward = Math.toRadians(yaw.toDouble()).toFloat()
            toy.worldPosition = it.worldPosition + Position(
              sin(forward) * 0.30f, -0.125f, cos(forward) * 0.30f,
            )
          }
          toy.worldRotation = Rotation(z = 45f)
        }
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
