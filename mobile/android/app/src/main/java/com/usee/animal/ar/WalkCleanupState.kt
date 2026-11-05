package com.usee.animal.ar

/** Time advances only for tracked frames after the dog has been placed. */
internal class WalkCleanupState(private val triggerSeconds: Float = 30f) {
  private var elapsed = 0f
  private var occurred = false
  var pending = false
    private set

  fun update(dt: Float, rightObstacle: Boolean): Boolean {
    if (occurred) return false
    elapsed += dt.coerceAtLeast(0f)
    if (elapsed < triggerSeconds || !rightObstacle) return false
    occurred = true
    pending = true
    return true
  }

  fun canCollect(distanceMeters: Float) = pending && distanceMeters < 2f

  fun shouldShowPoop(collecting: Boolean, throwElapsedMillis: Long, awaitingConfirmation: Boolean): Boolean =
    pending && !awaitingConfirmation && (!collecting || throwElapsedMillis < 350L)

  fun collect() { pending = false }
}
