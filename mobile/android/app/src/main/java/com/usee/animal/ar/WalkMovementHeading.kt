package com.usee.animal.ar

import kotlin.math.exp
import kotlin.math.hypot

/**
 * Confirm translation for 120ms before interpreting it as a new walking direction.
 * Within a confirmed direction, follow displacement through two averaging stages so
 * the roughly once-per-second body sway of each stride cancels out.
 */
internal class WalkMovementHeading {
  var x = 0f
    private set
  var z = -1f
    private set
  private var initialized = false
  var confirmed = false
    private set
  private var candidateX = 0f
  private var candidateZ = -1f
  private var candidateSeconds = 0f
  private var sumX = 0f
  private var sumZ = 0f
  private var averageX = 0f
  private var averageZ = 0f

  fun clear() {
    initialized = false; confirmed = false; candidateSeconds = 0f
    sumX = 0f; sumZ = 0f; averageX = 0f; averageZ = 0f
  }

  fun observe(vx: Float, vz: Float, dt: Float, fallbackX: Float, fallbackZ: Float): Boolean {
    if (!initialized) { x = fallbackX; z = fallbackZ; initialized = true }
    val speed = hypot(vx, vz)
    if (!speed.isFinite() || speed < 0.3f || speed >= 4f || dt <= 0f || dt > 0.25f) {
      candidateSeconds = 0f
      return false
    }
    val decay = exp(-dt / SMOOTH_SECONDS)
    sumX = sumX * decay + vx * dt
    sumZ = sumZ * decay + vz * dt
    averageX += (sumX - averageX) * (1f - decay)
    averageZ += (sumZ - averageZ) * (1f - decay)
    val nx = vx / speed; val nz = vz / speed
    if (candidateSeconds == 0f || nx * candidateX + nz * candidateZ < 0.94f) {
      candidateX = nx; candidateZ = nz; candidateSeconds = 0f
    }
    candidateSeconds += dt
    if (candidateSeconds + 0.000001f >= 0.12f) {
      candidateSeconds = 0f
      val changed = nx * x + nz * z < 0.70710677f
      if (!confirmed || changed) {
        confirmed = true
        x = nx; z = nz
        // Restart the averages along the new direction so the old one cannot pull it back.
        val length = hypot(sumX, sumZ).coerceAtLeast(0.1f)
        sumX = nx * length; sumZ = nz * length
        averageX = sumX; averageZ = sumZ
        return changed
      }
    }
    if (confirmed) {
      val length = hypot(averageX, averageZ)
      if (length > 0.05f) { x = averageX / length; z = averageZ / length }
    }
    return false
  }

  private companion object {
    const val SMOOTH_SECONDS = 0.8f
  }
}
