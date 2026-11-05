package com.usee.animal.ar

import kotlin.math.*

internal fun attentionWeight(seconds: Float, duration: Float): Float =
  (seconds / 0.25f).coerceIn(0f, 1f) * ((duration - seconds) / 0.25f).coerceIn(0f, 1f)

internal fun attentionYaw(forwardX: Float, forwardZ: Float, targetX: Float, targetZ: Float): Float {
  val angle = atan2(targetX, targetZ) - atan2(forwardX, forwardZ)
  return atan2(sin(angle), cos(angle))
}

internal fun attentionTailDegrees(seconds: Float): Float =
  sin(seconds * (8f * PI.toFloat())) * 20f * attentionWeight(seconds, 2f)

internal fun smoothAttentionYaw(currentDegrees: Float, targetDegrees: Float, dt: Float): Float {
  val delta = (targetDegrees - currentDegrees) * PI.toFloat() / 180f
  val shortest = atan2(sin(delta), cos(delta)) * 180f / PI.toFloat()
  return currentDegrees + shortest * (1f - exp(-dt.coerceIn(0f, 0.1f) / 0.12f))
}
