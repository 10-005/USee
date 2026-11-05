package com.usee.animal.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Paint
import android.opengl.Matrix
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import com.usee.animal.R
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import kotlin.math.hypot

/** Owns one cleanup event in the current AR session's world coordinate system. */
internal class WalkCleanupView(context: Context, private val scene: ARSceneView) : View(context) {
  // All three source images are 400x400px; display them at 200x200px.
  private val imageScale = 0.5f
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val bag = BitmapFactory.decodeResource(resources, R.drawable.img_poop_bag)
  private val filledBag = BitmapFactory.decodeResource(resources, R.drawable.img_poop_in_bag)
  private val poopOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also {
    context.assets.open("models/img_poop.png").use { stream -> BitmapFactory.decodeStream(stream, null, it) }
  }
  private val imageBounds = RectF()
  // A scene sprite uses the same depth buffer as the dog instead of covering it as an overlay.
  private val poopNode = ImageNode(
    materialLoader = scene.materialLoader,
    imageFileLocation = "models/img_poop.png",
    size = Size(1f, 1f, 0f),
    normal = Direction(0f, 0f, 1f),
  ).apply {
    isShadowCaster = false
    isShadowReceiver = false
    isVisible = false
    scene.addChildNode(this)
  }
  private val inverseView = FloatArray(16)
  private val spriteTransform = FloatArray(16)
  private var appearedAt = 0L
  private val state = WalkCleanupState()
  private var lastFrame = 0L
  private var tracking = false
  private var visiblePoop = false
  private var near = false
  private var poopX = 0f
  private var poopY = 0f
  private var poopZ = 0f
  private var screenX = 0f
  private var screenY = 0f
  private val cameraPoint = FloatArray(4)
  private val clip = FloatArray(4)
  private var dragging = false
  private var dragX = 0f
  private var dragY = 0f
  private var downX = 0f
  private var downY = 0f
  private var thrownAt = 0L
  private var throwX = 0f
  private var throwY = 0f
  private var targetX = 0f
  private var targetY = 0f
  private var collecting = false
  private val bagX get() = width / 2f
  private val bagY get() = height - maxOf(104f * density, bag.height * imageScale / 2f)
  private val bagHitWidth get() = maxOf(bag.width * imageScale / 2f, 32f * density)
  private val bagHitHeight get() = maxOf(bag.height * imageScale / 2f, 32f * density)

  init {
    setWillNotDraw(false)
    contentDescription = "배변봉투를 배변 쪽으로 밀어 던져주세요"
  }

  fun hitBag(x: Float, y: Float): Boolean = tracking && near && thrownAt == 0L &&
    kotlin.math.abs(x - bagX) <= bagHitWidth && kotlin.math.abs(y - bagY) <= bagHitHeight

  fun pause() {
    lastFrame = 0L
    tracking = false
    poopNode.isVisible = false
    dragging = false
    parent?.requestDisallowInterceptTouchEvent(false)
    invalidate()
  }

  fun update(dog: DogFrame, userX: Float, userZ: Float, view: FloatArray, projection: FloatArray,
    rightObstacle: Boolean, onPoop: () -> Unit) {
    val now = SystemClock.elapsedRealtime()
    val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.25f)
    lastFrame = now
    tracking = true
    if (state.update(dt, rightObstacle)) {
      onPoop()
    }
    if (state.pending && appearedAt == 0L && dog.poopDeposited) {
      // Place the sprite on the far side of the dog from the user.
      val distance = hypot(dog.x - userX, dog.z - userZ).coerceAtLeast(0.01f)
      poopX = dog.x + (dog.x - userX) / distance * 0.30f
      poopY = dog.y + 0.02f
      poopZ = dog.z + (dog.z - userZ) / distance * 0.30f
      appearedAt = now
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      val pattern = longArrayOf(0, 160, 140, 160, 140, 160)
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(pattern, -1)
      }
    }
    near = appearedAt != 0L && state.canCollect(hypot(userX - poopX, userZ - poopZ))
    Matrix.multiplyMV(cameraPoint, 0, view, 0, floatArrayOf(poopX, poopY, poopZ, 1f), 0)
    Matrix.multiplyMV(clip, 0, projection, 0, cameraPoint, 0)
    visiblePoop = clip[3] > 0f && kotlin.math.abs(clip[0]) <= clip[3] && kotlin.math.abs(clip[1]) <= clip[3]
    if (visiblePoop) {
      screenX = (clip[0] / clip[3] + 1f) * width / 2f
      screenY = (1f - clip[1] / clip[3]) * height / 2f
    }
    poopNode.isVisible = appearedAt != 0L && state.pending && visiblePoop && (!collecting || now - thrownAt < 350L)
    if (poopNode.isVisible && height > 0 && Matrix.invertM(inverseView, 0, view, 0)) {
      val worldPerPixel = 2f * clip[3] / (projection[5] * height)
      val spriteWidth = poopOptions.outWidth * imageScale * worldPerPixel
      val spriteHeight = poopOptions.outHeight * imageScale * worldPerPixel
      val drop = (1f - ((now - appearedAt) / 450f).coerceIn(0f, 1f))
      val lift = 16f * density * drop * drop * worldPerPixel
      // Keep the projected image at 200x200px, independent of camera distance.
      inverseView.copyInto(spriteTransform)
      for (row in 0..2) {
        spriteTransform[row] *= spriteWidth
        spriteTransform[4 + row] *= spriteHeight
      }
      spriteTransform[12] = poopX + inverseView[4] * (spriteHeight / 2f + lift)
      spriteTransform[13] = poopY + inverseView[5] * (spriteHeight / 2f + lift)
      spriteTransform[14] = poopZ + inverseView[6] * (spriteHeight / 2f + lift)
      poopNode.transformManager.setTransform(poopNode.transformInstance, spriteTransform)
      screenY -= poopOptions.outHeight * imageScale / 2f + lift / worldPerPixel
    }
    if (!near && dragging) {
      dragging = false
      parent?.requestDisallowInterceptTouchEvent(false)
    }
    invalidate()
  }

  fun release() {
    scene.removeChildNode(poopNode)
    poopNode.destroy()
    bag.recycle()
    filledBag.recycle()
  }

  private fun image(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, alpha: Int = 255) {
    paint.alpha = alpha
    paint.isFilterBitmap = true
    imageBounds.set(x - bitmap.width * imageScale / 2f, y - bitmap.height * imageScale / 2f,
      x + bitmap.width * imageScale / 2f, y + bitmap.height * imageScale / 2f)
    canvas.drawBitmap(bitmap, null, imageBounds, paint)
    paint.alpha = 255
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!tracking || !state.pending) return
    val now = SystemClock.elapsedRealtime()
    if (thrownAt != 0L) {
      val elapsed = now - thrownAt
      if (elapsed < 350L) {
        val t = elapsed / 350f
        image(canvas, bag, throwX + (targetX - throwX) * t,
          throwY + (targetY - throwY) * t - 48 * density * 4 * t * (1 - t))
      } else if (collecting && elapsed < 850L) {
        poopNode.isVisible = false
        image(canvas, filledBag, targetX, targetY - (elapsed - 350) / 500f * 48 * density,
          ((1 - (elapsed - 350) / 500f) * 255).toInt())
        paint.alpha = 255
      } else {
        thrownAt = 0L
        if (collecting) state.collect()
        collecting = false
      }
      postInvalidateOnAnimation()
    } else if (near) {
      image(canvas, bag, if (dragging) dragX else bagX, if (dragging) dragY else bagY)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        if (!hitBag(event.x, event.y)) return false
        dragging = true
        downX = event.x
        downY = event.y
        dragX = event.x
        dragY = event.y
        parent.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_MOVE -> {
        if (!dragging) return false
        dragX = event.x
        dragY = event.y
      }
      MotionEvent.ACTION_UP -> {
        if (!dragging) return false
        dragging = false
        val dx = event.x - downX
        val dy = event.y - downY
        val length = hypot(dx, dy)
        if (length >= 24 * density && near && tracking) {
          val tx = screenX - downX
          val ty = screenY - downY
          val along = (tx * dx + ty * dy) / length
          val miss = kotlin.math.abs(tx * dy - ty * dx) / length
          collecting = visiblePoop && along > 0f && miss <= 40 * density
          throwX = event.x
          throwY = event.y
          targetX = if (collecting) screenX else event.x + dx * 2
          targetY = if (collecting) screenY else event.y + dy * 2
          thrownAt = SystemClock.elapsedRealtime()
        }
        parent.requestDisallowInterceptTouchEvent(false)
        performClick()
      }
      MotionEvent.ACTION_CANCEL -> {
        dragging = false
        parent.requestDisallowInterceptTouchEvent(false)
      }
    }
    invalidate()
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }
}
