package com.usee.animal.ar

import kotlin.math.*

data class WallSegment(
  val ax: Float, val az: Float,
  val bx: Float, val bz: Float,
  val minY: Float = -10f, val maxY: Float = 10f,
) {
  fun blocks(x: Float, z: Float, nextX: Float, nextZ: Float, floorY: Float): Boolean {
    if (maxY < floorY + 0.04f || minY > floorY + 0.55f) return false
    val start = pointDistanceSquared(x, z)
    val end = pointDistanceSquared(nextX, nextZ)
    val distance = if (intersects(x, z, nextX, nextZ)) 0f else minOf(
      start, end, pointSegmentDistanceSquared(ax, az, x, z, nextX, nextZ),
      pointSegmentDistanceSquared(bx, bz, x, z, nextX, nextZ),
    )
    val clearanceSquared = 0.28f * 0.28f
    if (distance >= clearanceSquared) return false
    // A newly detected wall may already overlap the dog. Allow escape along
    // or away from that wall, but never a deeper approach or a crossing.
    if (start < clearanceSquared && end >= start - 0.00001f && distance >= start - 0.00001f) {
      return hypot(nextX - x, nextZ - z) < 0.00001f
    }
    return true
  }

  private fun pointDistanceSquared(x: Float, z: Float) = pointSegmentDistanceSquared(x, z, ax, az, bx, bz)

  private fun intersects(x: Float, z: Float, nextX: Float, nextZ: Float): Boolean {
    val dx = nextX - x
    val dz = nextZ - z
    val wx = bx - ax
    val wz = bz - az
    val cross = dx * wz - dz * wx
    if (abs(cross) < 0.000001f) return false
    val t = ((ax - x) * wz - (az - z) * wx) / cross
    val u = ((ax - x) * dz - (az - z) * dx) / cross
    return t in 0f..1f && u in 0f..1f
  }

  private fun pointSegmentDistanceSquared(
    x: Float, z: Float, ax: Float, az: Float, bx: Float, bz: Float,
  ): Float {
    val dx = bx - ax
    val dz = bz - az
    val lengthSquared = dx * dx + dz * dz
    val t = if (lengthSquared < 0.000001f) 0f else (((x - ax) * dx + (z - az) * dz) / lengthSquared).coerceIn(0f, 1f)
    val ex = x - ax - dx * t
    val ez = z - az - dz * t
    return ex * ex + ez * ez
  }
}

data class WallRoute(val yaw: Float, val detouring: Boolean = false, val blocked: Boolean = false)

class WallAvoidance {
  var walls: List<WallSegment> = emptyList()
  private var detourYaw: Float? = null
  private var intentYaw = 0f
  private var clearSeconds = 0f
  private var rejectedYaw: Float? = null
  private var rejectedSeconds = 0f

  fun isClear(x: Float, z: Float, nextX: Float, nextZ: Float, y: Float) =
    walls.none { it.blocks(x, z, nextX, nextZ, y) }

  fun steer(x: Float, z: Float, y: Float, desiredYaw: Float, distance: Float, intent: Float, dt: Float): WallRoute {
    rejectedSeconds -= dt
    if (rejectedSeconds <= 0f) rejectedYaw = null
    if (cos(intent - intentYaw) < 0f) {
      detourYaw = null
      rejectedYaw = null
    }
    intentYaw = intent
    val dx = sin(desiredYaw) * distance
    val dz = cos(desiredYaw) * distance
    val blocker = walls.firstOrNull { it.blocks(x, z, x + dx, z + dz, y) }
    if (blocker == null) {
      clearSeconds += dt
      if (clearSeconds >= 0.3f) {
        detourYaw = null
        rejectedYaw = null
      }
      return WallRoute(desiredYaw)
    }
    clearSeconds = 0f
    fun clear(angle: Float) = isClear(x, z, x + sin(angle) * distance, z + cos(angle) * distance, y)
    // Keep the selected side until the direct path clears, instead of zigzagging
    // as the target moves behind the wall.
    detourYaw?.let { if (clear(it)) return WallRoute(it, detouring = true) }
    var best: Float? = null
    var score = Float.NEGATIVE_INFINITY
    fun consider(angle: Float) {
      if (rejectedYaw?.let { cos(angle - it) > 0.9f } == true || !clear(angle)) return
      val progress = cos(angle - desiredYaw)
      if (progress > score) {
        best = angle
        score = progress
      }
    }
    for (offset in OFFSETS) consider(desiredYaw + offset)
    val tangent = atan2(blocker.bx - blocker.ax, blocker.bz - blocker.az)
    consider(tangent)
    consider(tangent + PI.toFloat())
    detourYaw = best
    return if (best != null) WallRoute(best!!, detouring = true) else WallRoute(desiredYaw, blocked = true)
  }

  fun rejectRoute(yaw: Float) {
    rejectedYaw = yaw
    rejectedSeconds = 0.35f
    detourYaw = null
  }

  private companion object {
    val OFFSETS = floatArrayOf(-0.3f, 0.3f, -0.6f, 0.6f, -1f, 1f, -1.57f, 1.57f)
  }
}
