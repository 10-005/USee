package com.usee.animal.ar

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

internal const val USER_CLEARANCE_METERS = 0.35f

/** User motion is stronger evidence than image classification. Session-local world coordinates. */
internal class UserWalkRegion {
  private data class Cell(val x: Int, val z: Int)
  private data class Ground(val x: Float, val z: Float, val y: Float)
  private val trail = LinkedHashMap<Cell, Ground>()
  private var user: Ground? = null
  val groundY: Float? get() = user?.y

  fun clear() { trail.clear(); user = null }

  fun update(x: Float, z: Float, y: Float) {
    val previous = user
    val distance = previous?.let { hypot(x - it.x, z - it.z) } ?: 0f
    // Never connect a relocalization/teleport into a fictitious traversed corridor.
    if (previous != null && distance > 0.8f) trail.clear()
    val samples = if (previous != null && distance <= 0.8f) ceil(distance / 0.1f).toInt().coerceAtLeast(1) else 1
    for (i in 1..samples) {
      val t = i.toFloat() / samples
      val point = if (previous != null && distance <= 0.8f) Ground(
        previous.x + (x - previous.x) * t, previous.z + (z - previous.z) * t, previous.y + (y - previous.y) * t,
      ) else Ground(x, z, y)
      trail[Cell(floor(point.x / 0.2f).toInt(), floor(point.z / 0.2f).toInt())] = point
    }
    user = Ground(x, z, y)
  }

  fun heightAt(x: Float, z: Float): Float? {
    val current = user ?: return null
    if (hypot(x - current.x, z - current.z) <= USER_CLEARANCE_METERS + 0.0001f) return current.y
    val cellX = floor(x / 0.2f).toInt()
    val cellZ = floor(z / 0.2f).toInt()
    var nearest: Ground? = null
    // Trust half a meter around the user's current position and recorded path.
    // A 0.5 m radius can span three 0.2 m cells across a cell boundary.
    var nearestDistance = 0.5f
    for (dx in -3..3) for (dz in -3..3) {
      val point = trail[Cell(cellX + dx, cellZ + dz)] ?: continue
      val distance = hypot(point.x - x, point.z - z)
      if (distance <= nearestDistance) { nearest = point; nearestDistance = distance }
    }
    return nearest?.y
  }
}

/** Keep a ground level until a different level is observed in three separate plane refreshes. */
internal class GroundLevelFilter {
  private var height: Float? = null
  private var estimated = true
  private var candidate: Float? = null
  private var count = 0
  private var lastSample = -1L
  val isEstimated get() = estimated
  fun clear() { height = null; estimated = true; candidate = null; count = 0; lastSample = -1L }
  fun update(detected: Float?, fallback: Float, sample: Long): Float {
    val current = height
    if (detected == null) { if (current == null) height = fallback; return height!! }
    // Repeated observations of a distant plane do not make it the walking floor.
    if (!detected.isFinite() || current != null && kotlin.math.abs(detected - current) > 0.45f) {
      candidate = null
      count = 0
      if (current == null) height = fallback
      return height!!
    }
    if (current == null || estimated) { height = detected; estimated = false; lastSample = sample; return detected }
    if (sample == lastSample) return current
    lastSample = sample
    if (kotlin.math.abs(detected - current) <= 0.04f) {
      height = current + (detected - current) * 0.2f
      candidate = null; count = 0
    } else {
      if (candidate?.let { kotlin.math.abs(detected - it) <= 0.04f } == true) count++
      else { candidate = detected; count = 1 }
      if (count >= 3) { height = detected; candidate = null; count = 0 }
    }
    return height!!
  }
}
