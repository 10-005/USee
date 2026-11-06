package com.usee.animal.ar

import kotlin.math.abs
import kotlin.math.sign

/** Remembers visual facing independently of navigation and collision decisions. Angles are degrees. */
internal class DogHeadingSmoother {
  private var heading: Float? = null
  private var target = 0f
  private var candidate = 0f
  private var candidateSeconds = 0f
  private var angularSpeed = 0f

  fun reset() {
    heading = null
    pause()
  }

  fun pause() {
    candidateSeconds = 0f
    angularSpeed = 0f
  }

  /** Scripted full turns must bypass the walking direction debounce. */
  fun snap(desired: Float): Float {
    reset()
    return update(desired, 0f)
  }

  fun update(desired: Float, deltaSeconds: Float): Float {
    val current = heading ?: return wrap(desired).also {
      heading = it
      target = it
      candidate = it
    }
    val dt = deltaSeconds.coerceIn(0f, 0.05f)
    if (dt <= 0f) return current
    if (abs(wrap(desired - target)) <= 8f) {
      candidateSeconds = 0f
      candidate = desired
    } else {
      if (candidateSeconds == 0f || abs(wrap(desired - candidate)) > 15f) {
        candidate = desired
        candidateSeconds = 0f
      }
      candidateSeconds += dt
      if (candidateSeconds >= 0.18f) {
        target = wrap(desired)
        candidateSeconds = 0f
      }
    }
    var error = wrap(target - current)
    // Around 180 degrees, tiny input noise must not reverse the chosen turn.
    if (abs(error) > 170f && abs(angularSpeed) > 1f) error = abs(error) * sign(angularSpeed)
    val desiredSpeed = (error * 5f).coerceIn(-120f, 120f)
    angularSpeed += (desiredSpeed - angularSpeed).coerceIn(-480f * dt, 480f * dt)
    val step = angularSpeed * dt
    val next = if (step * error >= 0f && abs(step) >= abs(error)) {
      angularSpeed = 0f
      target
    } else wrap(current + step)
    heading = next
    return next
  }

  private fun wrap(angle: Float): Float = ((angle + 180f) % 360f + 360f) % 360f - 180f
}
