package com.usee.animal.ar

import kotlin.math.hypot

/** Confirm translation for 120ms before interpreting it as a new walking direction. */
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

  fun clear() { initialized = false; confirmed = false; candidateSeconds = 0f }

  fun observe(vx: Float, vz: Float, dt: Float, fallbackX: Float, fallbackZ: Float): Boolean {
    if (!initialized) { x = fallbackX; z = fallbackZ; initialized = true }
    val speed = hypot(vx, vz)
    if (!speed.isFinite() || speed < 0.3f || speed >= 4f || dt <= 0f || dt > 0.25f) {
      candidateSeconds = 0f
      return false
    }
    val nx = vx / speed; val nz = vz / speed
    if (candidateSeconds == 0f || nx * candidateX + nz * candidateZ < 0.94f) {
      candidateX = nx; candidateZ = nz; candidateSeconds = 0f
    }
    candidateSeconds += dt
    if (candidateSeconds + 0.000001f < 0.12f) return false
    val changed = nx * x + nz * z < 0.70710677f
    confirmed = true
    x = nx; z = nz
    candidateSeconds = 0f
    return changed
  }
}
