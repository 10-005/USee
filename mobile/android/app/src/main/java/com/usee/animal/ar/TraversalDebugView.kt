package com.usee.animal.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.opengl.Matrix
import android.os.SystemClock
import android.view.View
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/** 30%-opacity diagnostic overlay above the live AR camera. No frame/image is retained. */
internal class TraversalDebugView(context: Context) : View(context) {
  private var showAreas = false
  private var showNotices = false

  fun setDisplay(areas: Boolean, notices: Boolean) {
    showAreas = areas
    showNotices = notices
    invalidate()
  }

  private val paint = Paint().apply { isFilterBitmap = false }
  private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12f * resources.displayMetrics.scaledDensity }
  private var bitmap: Bitmap? = null
  private var nextUpdate = 0L
  private var caption = "분석 결과 대기 · 미확인 영역은 노랑"
  private var statistics = ""
  private val destination = Rect()

  init { isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

  fun update(frame: Frame, mask: SemanticFloorMask?, planes: Collection<Plane>, walls: List<WallSegment>, dog: DogFrame, stopSummary: String, userGround: UserWalkRegion, depth: DepthTerrain) {
    val now = SystemClock.elapsedRealtimeNanos()
    if (width <= 0 || height <= 0 || now < nextUpdate) return
    nextUpdate = now + 250_000_000L
    statistics = "${depth.status} · $stopSummary"
    val columns = 40
    val rows = ceil(columns.toDouble() * height / width).toInt().coerceIn(24, 128)
    val colors = IntArray(columns * rows) { YELLOW }
    val tracking = frame.camera.trackingState == TrackingState.TRACKING
    caption = when {
      !tracking -> "추적 끊김 · 이동 중단 · 미확인 영역은 노랑"
      mask == null -> "${motionLabel(dog)} · 분석 결과 대기"
      !mask.isFresh(now) -> "${motionLabel(dog)} · 분석 결과 만료"
      else -> "${motionLabel(dog)} · 분석 ${(now - mask.capturedAtNanos) / 1_000_000}ms 전"
    }
    if (tracking) {
      val screen = FloatArray(colors.size * 2)
      for (i in colors.indices) {
        screen[2 * i] = (i % columns + 0.5f) / columns
        screen[2 * i + 1] = (i / columns + 0.5f) / rows
      }
      val view = FloatArray(16)
      val projection = FloatArray(16)
      val vp = FloatArray(16)
      val inverse = FloatArray(16)
      frame.camera.getViewMatrix(view, 0)
      frame.camera.getProjectionMatrix(projection, 0, 0.1f, 100f)
      Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
      val invertible = Matrix.invertM(inverse, 0, vp, 0)
      val origin = frame.camera.pose.translation
      // Heights are read once per diagnostic refresh, never once per pixel.
      val heights = planes.filter {
        it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.subsumedBy == null
      }.map { it.centerPose.ty() }.filter { it <= origin[1] - 0.35f }.plus(listOfNotNull(userGround.groundY)).distinct().sortedBy { abs(it - (origin[1] - 1.35f)) }
      val ray = FloatArray(4)
      val world = FloatArray(4)
      for (i in colors.indices) {
        if (!invertible) continue
        ray[0] = screen[2 * i] * 2f - 1f
        ray[1] = 1f - screen[2 * i + 1] * 2f
        ray[2] = 1f
        ray[3] = 1f
        Matrix.multiplyMV(world, 0, inverse, 0, ray, 0)
        val dx = world[0] / world[3] - origin[0]
        val dy = world[1] / world[3] - origin[1]
        val dz = world[2] / world[3] - origin[2]
        val semantic = mask?.pathAlongWorldDirection(dx, dy, dz, now) ?: -1
        if (semantic == 0) colors[i] = RED
        if (abs(dy) < 0.0001f) continue
        for (y in heights) {
          val t = (y - origin[1]) / dy
          if (t <= 0f) continue
          val x = origin[0] + dx * t
          val z = origin[2] + dz * t
          val trusted = userGround.heightAt(x, z)
          if (trusted != null && abs(trusted - y) <= 0.18f) { colors[i] = GREEN; break }
          val ground = floorYAt(planes, origin[1], x, z, userGround.groundY) ?: continue
          if (abs(ground - y) > 0.01f) continue
          val geometry = depth.latest?.evidence(x, ground, z, now) ?: FloorEvidence.UNKNOWN
          if (geometry == FloorEvidence.OBSTACLE) { colors[i] = RED; break }
          if (geometry == FloorEvidence.CLEAR) {
            val allowed = (!dog.visible || abs(ground - dog.y) <= 0.18f) &&
              hypot(x - origin[0], z - origin[2]) >= 0.45f &&
              walls.none { it.blocks(x, z, x, z, ground, DOG_BODY_RADIUS, DOG_MODEL_UNITS) }
            colors[i] = if (allowed) GREEN else YELLOW
            break
          }
          if (mask == null) continue
          val groundClass = mask.pathAtWorld(x, ground, z, now)
          if (groundClass == 0) { colors[i] = RED; break }
          if (groundClass < 0) { colors[i] = YELLOW; break }
          val floor = walkableFloorAt(planes, origin[1], mask, x, z, now, userGround.groundY)
          val heightAllowed = !dog.visible || abs(ground - dog.y) <= 0.18f
          val walkerAllowed = hypot(x - origin[0], z - origin[2]) >= 0.45f
          val wallAllowed = walls.none { it.blocks(x, z, x, z, ground, DOG_BODY_RADIUS, DOG_MODEL_UNITS) }
          if (floor != null && heightAllowed && walkerAllowed && wallAllowed) colors[i] = GREEN
          break
        }
      }
    }
    if (bitmap?.width != columns || bitmap?.height != rows) {
      bitmap?.recycle()
      bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
    }
    bitmap!!.setPixels(colors, 0, columns, 0, 0, columns, rows)
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!showAreas && !showNotices) return
    if (showAreas) {
      destination.set(0, 0, width, height)
      paint.alpha = 77
      val current = bitmap
      if (current != null) canvas.drawBitmap(current, null, destination, paint)
      else canvas.drawColor((YELLOW and 0x00ffffff) or (77 shl 24))
      paint.alpha = 255
    }
    if (!showNotices) return
    val density = resources.displayMetrics.density
    val top = 236f * density
    paint.color = Color.BLACK
    canvas.drawRect(8f * density, top, width - 8f * density, top + 84f * density, paint)
    canvas.drawText("초록: 실제 보행 경로·이동 가능 / 빨강: 장애물", 16f * density, top + 18f * density, text)
    canvas.drawText("노랑: 길이지만 이동 조건 미충족 / 미확인", 16f * density, top + 37f * density, text)
    canvas.drawText(caption, 16f * density, top + 55f * density, text)
    canvas.drawText(statistics, 16f * density, top + 74f * density, text)
  }

  private fun motionLabel(dog: DogFrame) = when {
    dog.ignoresObstacles -> "복귀 중 · 장애물 무시"
    dog.blocked -> "정지: ${dog.stopReason}"
    dog.cautious && dog.moving -> "감속: 바닥 재확인"
    dog.moving -> "이동 중"
    else -> "대기"
  }

  fun release() { bitmap?.recycle(); bitmap = null }

  companion object {
    const val GREEN: Int = -0xff37ad // #00C853, fully opaque
    const val RED: Int = -0x10dadb // #EF2525, fully opaque
    const val YELLOW: Int = -0x2a00 // #FFD600, fully opaque
  }
}
