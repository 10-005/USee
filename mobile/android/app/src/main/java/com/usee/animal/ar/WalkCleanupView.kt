package com.usee.animal.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.Matrix
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Owns one cleanup event in the current AR session's world coordinate system. */
internal class WalkCleanupView(context: Context) : View(context) {
  var showNotices = false
    set(value) { field = value; invalidate() }
  var petName = "강아지"
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
  private val bagY get() = height - 104f * density
  private val size get() = 24f * density

  init {
    setWillNotDraw(false)
    contentDescription = "배변봉투를 배변 쪽으로 밀어 던져주세요"
  }

  fun hitBag(x: Float, y: Float): Boolean = tracking && near && thrownAt == 0L &&
    hypot(x - bagX, y - bagY) <= 32 * density

  fun pause() {
    lastFrame = 0L
    tracking = false
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
      poopX = dog.x
      poopY = dog.y + 0.02f
      poopZ = dog.z
      onPoop()
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      val pattern = longArrayOf(0, 160, 140, 160, 140, 160)
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(pattern, -1)
      }
    }
    near = state.canCollect(hypot(userX - poopX, userZ - poopZ))
    Matrix.multiplyMV(cameraPoint, 0, view, 0, floatArrayOf(poopX, poopY, poopZ, 1f), 0)
    Matrix.multiplyMV(clip, 0, projection, 0, cameraPoint, 0)
    visiblePoop = clip[3] > 0f && kotlin.math.abs(clip[0]) <= clip[3] && kotlin.math.abs(clip[1]) <= clip[3]
    if (visiblePoop) {
      screenX = (clip[0] / clip[3] + 1f) * width / 2f
      screenY = (1f - clip[1] / clip[3]) * height / 2f
    }
    if (!near && dragging) {
      dragging = false
      parent?.requestDisallowInterceptTouchEvent(false)
    }
    invalidate()
  }

  private fun square(canvas: Canvas, x: Float, y: Float, color: Int, alpha: Int = 255) {
    paint.color = color
    paint.alpha = alpha
    canvas.drawRect(x - size / 2, y - size / 2, x + size / 2, y + size / 2, paint)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!tracking || !state.pending) return
    val now = SystemClock.elapsedRealtime()
    if (showNotices) {
      paint.color = Color.argb(210, 17, 17, 17)
      canvas.drawRoundRect(20 * density, 332 * density, width - 20 * density, 384 * density, 12 * density, 12 * density, paint)
      paint.color = Color.WHITE
      paint.textSize = 16 * resources.displayMetrics.scaledDensity
      paint.textAlign = Paint.Align.CENTER
      canvas.drawText("${petName}의 배변을 치워주세요", width / 2f, 364 * density, paint)
    }
    if (visiblePoop && (!collecting || now - thrownAt < 350L)) square(canvas, screenX, screenY, Color.rgb(123, 76, 43))
    if (thrownAt != 0L) {
      val elapsed = now - thrownAt
      if (elapsed < 350L) {
        val t = elapsed / 350f
        square(canvas, throwX + (targetX - throwX) * t,
          throwY + (targetY - throwY) * t - 48 * density * 4 * t * (1 - t), Color.rgb(74, 163, 113))
      } else if (collecting && elapsed < 850L) {
        square(canvas, targetX, targetY - (elapsed - 350) / 500f * 48 * density, Color.rgb(45, 100, 70),
          ((1 - (elapsed - 350) / 500f) * 255).toInt())
        paint.alpha = 255
      } else {
        thrownAt = 0L
        if (collecting) state.collect()
        collecting = false
      }
      postInvalidateOnAnimation()
    } else if (near) {
      square(canvas, if (dragging) dragX else bagX, if (dragging) dragY else bagY, Color.rgb(74, 163, 113))
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        if (!tracking || !near || thrownAt != 0L || hypot(event.x - bagX, event.y - bagY) > 32 * density) return false
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
