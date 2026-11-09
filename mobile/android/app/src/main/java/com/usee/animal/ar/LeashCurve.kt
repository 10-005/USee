package com.usee.animal.ar

import kotlin.math.ceil
import kotlin.math.sqrt

internal data class LeashPoint(val x: Float, val y: Float, val z: Float) {
  operator fun plus(other: LeashPoint) = LeashPoint(x + other.x, y + other.y, z + other.z)
  operator fun minus(other: LeashPoint) = LeashPoint(x - other.x, y - other.y, z - other.z)
  operator fun times(value: Float) = LeashPoint(x * value, y * value, z * value)
  fun length() = sqrt(x * x + y * y + z * z)
}

/** A damped midpoint adds slack/lag while both endpoints stay attached every render frame. */
internal class LeashCurve {
  private var middle: LeashPoint? = null
  private var velocity = LeashPoint(0f, 0f, 0f)

  fun reset() { middle = null; velocity = LeashPoint(0f, 0f, 0f) }

  fun points(collar: LeashPoint, hand: LeashPoint, dt: Float, segments: Int = 24): List<LeashPoint> {
    require(segments > 0)
    val distance = (hand - collar).length()
    val sag = 0.025f + (1f - distance / 2.2f).coerceIn(0f, 1f) * 0.32f
    val desired = (collar + hand) * 0.5f + LeashPoint(0f, -2f * sag, 0f)
    val previous = middle
    var control = previous ?: desired
    if (previous == null || (desired - previous).length() > 1f || dt > 0.25f) {
      control = desired
      velocity = LeashPoint(0f, 0f, 0f)
    } else {
      // Substeps keep the damped spring stable across rendering frame rates.
      val duration = dt.coerceIn(0f, 0.1f)
      val steps = ceil(duration / (1f / 120f)).toInt().coerceAtLeast(1)
      val step = duration / steps
      repeat(steps) {
        velocity = velocity + ((desired - control) * 90f - velocity * 16f) * step
        control = control + velocity * step
      }
    }
    middle = control
    return List(segments + 1) { i ->
      val t = i.toFloat() / segments
      collar * ((1f - t) * (1f - t)) + control * (2f * t * (1f - t)) + hand * (t * t)
    }
  }

  companion object {
    fun handInCamera(inverseProjection: FloatArray, depth: Float = 0.35f): LeashPoint =
      screenPointInCamera(inverseProjection, 0f, -1f, depth)

    fun screenPointInCamera(inverseProjection: FloatArray, ndcX: Float, ndcY: Float, depth: Float): LeashPoint {
      // Division by W cancels when fixing Z.
      val x = ndcX * inverseProjection[0] + ndcY * inverseProjection[4] + inverseProjection[12]
      val y = ndcX * inverseProjection[1] + ndcY * inverseProjection[5] + inverseProjection[13]
      val z = ndcX * inverseProjection[2] + ndcY * inverseProjection[6] + inverseProjection[14]
      val scale = -depth / z
      return LeashPoint(x * scale, y * scale, -depth)
    }

    /** Column-major transform: unit-height cylinder's Y axis joins the two endpoints. */
    fun segmentTransform(start: LeashPoint, end: LeashPoint): FloatArray {
      val delta = end - start
      val length = delta.length().coerceAtLeast(0.00001f)
      val up = delta * (1f / length)
      val lateralLength = sqrt(up.x * up.x + up.y * up.y)
      val right = if (lateralLength > 0.0001f) LeashPoint(up.y / lateralLength, -up.x / lateralLength, 0f)
        else LeashPoint(1f, 0f, 0f)
      val forward = LeashPoint(right.y * up.z - right.z * up.y, right.z * up.x - right.x * up.z, right.x * up.y - right.y * up.x)
      val center = (start + end) * 0.5f
      return floatArrayOf(
        right.x, right.y, right.z, 0f,
        delta.x, delta.y, delta.z, 0f,
        forward.x, forward.y, forward.z, 0f,
        center.x, center.y, center.z, 1f,
      )
    }
  }
}
