package com.usee.animal.ar

/** Time advances only for tracked frames after the dog has been placed. */
internal class WalkCleanupState {
  private var occurred = false
  var pending = false
    private set

  fun request(): Boolean {
    if (occurred) return false
    occurred = true
    pending = true
    return true
  }

  fun update(dt: Float, rightObstacle: Boolean): Boolean = false

  fun canCollect(distanceMeters: Float) = pending && distanceMeters < 2f

  fun shouldShowPoop(collecting: Boolean, throwElapsedMillis: Long, awaitingConfirmation: Boolean): Boolean =
    pending && !awaitingConfirmation && (!collecting || throwElapsedMillis < 350L)

  fun collect() { pending = false }
}
