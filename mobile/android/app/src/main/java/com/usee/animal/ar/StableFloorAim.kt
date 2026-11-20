package com.usee.animal.ar

import kotlin.math.abs
import kotlin.math.hypot

/** Tracks continuous aim at a fixed place on the ground in AR world coordinates. */
internal class StableFloorAim {
  private var start: FloorWaypoint? = null
  private var startedAt = 0L
  private var lastFrame = 0L

  fun clear() { start = null }

  fun update(timestamp: Long, target: FloorWaypoint?): FloorWaypoint? {
    if (target == null) {
      clear()
      return null
    }
    val initial = start
    if (initial == null || timestamp - lastFrame !in 0L..500_000_000L ||
      hypot(target.x - initial.x, target.z - initial.z) > 0.3f || abs(target.y - initial.y) > 0.15f) {
      start = target
      startedAt = timestamp
    }
    lastFrame = timestamp
    return target.takeIf { timestamp - startedAt >= 5_000_000_000L }
  }
}

internal fun centerFloorTarget(cameraX: Float, cameraY: Float, cameraZ: Float,
  ray: FloatArray, groundY: Float): FloorWaypoint? {
  // Intersect the center ray with the estimated walking level, in front of the user.
  if (ray[1] >= -0.001f || cameraY - groundY < 0.35f) return null
  val scale = (groundY - cameraY) / ray[1]
  val dx = ray[0] * scale
  val dz = ray[2] * scale
  if (hypot(dx, dz) !in 0.4f..2.5f) return null
  return FloorWaypoint(cameraX + dx, cameraZ + dz, groundY)
}
