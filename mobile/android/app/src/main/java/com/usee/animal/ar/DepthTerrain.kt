package com.usee.animal.ar

import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Bounded, current-camera depth geometry; never turns missing depth into free space. */
internal class DepthTerrain {
  var supported = false
  var latest: DepthGrid? = null
    private set
  private var nextRead = 0L
  private var sourceTimestamp = -1L
  private var observedAt = 0L
  val status: String get() = when {
    !supported -> "Depth 미지원 · 평면+분석"
    latest?.fresh(SystemClock.elapsedRealtimeNanos()) == true -> "Depth 사용 · 분석 보조"
    else -> "Depth 준비 · 평면+분석"
  }

  fun clear() { latest = null; nextRead = 0L; sourceTimestamp = -1L }

  fun update(frame: Frame, now: Long) {
    if (frame.camera.trackingState != TrackingState.TRACKING) { clear(); return }
    if (!supported || now < nextRead) return
    nextRead = now + 200_000_000L
    try {
      frame.acquireDepthImage16Bits().use { image ->
        if (image.timestamp != sourceTimestamp) {
          sourceTimestamp = image.timestamp
          observedAt = now
        }
        val plane = image.planes[0]
        val intrinsics = frame.camera.textureIntrinsics
        val dimensions = intrinsics.imageDimensions
        val focal = intrinsics.focalLength
        val principal = intrinsics.principalPoint
        val sx = image.width.toFloat() / dimensions[0]
        val sy = image.height.toFloat() / dimensions[1]
        val transform = FloatArray(16)
        frame.camera.pose.toMatrix(transform, 0)
        latest = DepthGrid.read(plane.buffer, image.width, image.height, plane.rowStride,
          plane.pixelStride, focal[0] * sx, focal[1] * sy, principal[0] * sx,
          principal[1] * sy, transform, observedAt)
      }
    } catch (_: NotYetAvailableException) {
      // Startup or a textureless view: retain only the bounded-age previous observation.
    } catch (_: com.google.ar.core.exceptions.ResourceExhaustedException) {
      // Another camera consumer is briefly holding images. Retry next sampling interval.
    }
  }
}

/** Pure geometry for tests. Millimetres are optical-axis depth, not distance along a ray. */
internal class DepthGrid(val capturedAt: Long) {
  val observationId get() = capturedAt
  private val cells = HashMap<Pair<Int, Int>, MutableList<Float>>()
  fun fresh(now: Long) = now >= capturedAt && now - capturedAt <= 600_000_000L
  fun add(x: Float, y: Float, z: Float) {
    cells.getOrPut(key(x, z)) { ArrayList() }.add(y)
  }
  fun evidence(x: Float, ground: Float, z: Float, now: Long): FloorEvidence {
    if (!fresh(now)) return FloorEvidence.UNKNOWN
    val heights = cells[key(x, z)] ?: return FloorEvidence.UNKNOWN
    // Several samples suppress isolated depth speckles. Furniture and walls occupy body height.
    if (heights.count { it - ground in 0.12f..0.85f } >= 3) return FloorEvidence.OBSTACLE
    if (heights.count { abs(it - ground) <= 0.08f } >= 3) return FloorEvidence.CLEAR
    // A measured drop/step is not a walkable continuation of this floor.
    if (heights.count { it < ground - 0.12f } >= 3) return FloorEvidence.OBSTACLE
    return FloorEvidence.UNKNOWN
  }
  companion object {
    private fun key(x: Float, z: Float) = floor(x / 0.15f).toInt() to floor(z / 0.15f).toInt()
    fun read(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int,
      fx: Float, fy: Float, cx: Float, cy: Float, pose: FloatArray, now: Long): DepthGrid {
      val grid = DepthGrid(now)
      val step = max(1, kotlin.math.ceil(kotlin.math.sqrt(width.toDouble() * height / 12000)).toInt())
      val base = buffer.position()
      for (v in 0 until height step step) for (u in 0 until width step step) {
        val offset = base + v * rowStride + u * pixelStride
        val mm = (buffer.get(offset).toInt() and 255) or ((buffer.get(offset + 1).toInt() and 255) shl 8)
        if (mm !in 300..5000) continue
        val depth = mm / 1000f
        val x = (u - cx) * depth / fx
        val y = -(v - cy) * depth / fy
        val z = -depth
        grid.add(pose[0]*x + pose[4]*y + pose[8]*z + pose[12],
          pose[1]*x + pose[5]*y + pose[9]*z + pose[13],
          pose[2]*x + pose[6]*y + pose[10]*z + pose[14])
      }
      return grid
    }
  }
}
