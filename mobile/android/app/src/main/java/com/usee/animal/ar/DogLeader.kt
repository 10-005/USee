package com.usee.animal.ar

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min

/**
 * 카메라보다 앞에서 걷도록 강아지를 이끈다.
 *
 * 목표 지점은 카메라 수평 전방 [LEAD_METERS]미터다.
 * 카메라를 돌리면 목표 지점이 그 방향으로 옮겨지고, 강아지는 그쪽으로 몸을 틀어 걸어간다.
 * 모델은 로컬 +Z가 앞이므로, yaw 0은 월드 +Z를 바라본다. 사용자는 그 뒷모습을 본다.
 */
class DogLeader {
  private var placed = false
  private var x = 0f
  private var y = 0f
  private var z = 0f
  private var yaw = 0f
  private var lastGroundY: Float? = null
  private var headingX = 0f
  private var headingZ = 1f
  private var hasHeading = false

  fun update(
    deltaSeconds: Float,
    cameraX: Float,
    cameraZ: Float,
    lookX: Float,
    lookZ: Float,
    groundY: Float?,
  ): DogFrame {
    val forward = horizontalForward(lookX, lookZ)
    val ground = groundY ?: lastGroundY
    if (groundY != null) lastGroundY = groundY
    if (forward == null || ground == null) {
      return frame(visible = false, clip = CLIP_IDLE, speed = 0f)
    }

    val targetX = cameraX + forward.first * LEAD_METERS
    val targetZ = cameraZ + forward.second * LEAD_METERS
    if (!placed || deltaSeconds > 0.25f) {
      x = targetX
      y = ground
      z = targetZ
      yaw = atan2(forward.first, forward.second)
      placed = true
      return frame(visible = true, clip = CLIP_IDLE, speed = 0f)
    }

    val dt = deltaSeconds.coerceIn(0f, 0.05f)
    y += (ground - y) * (1f - exp(-GROUND_FOLLOW * dt))

    val dx = targetX - x
    val dz = targetZ - z
    val gap = hypot(dx, dz)
    val desiredYaw = if (gap > YAW_LOCK_GAP) atan2(dx, dz) else atan2(forward.first, forward.second)
    val turn = wrapRadians(desiredYaw - yaw).coerceIn(-TURN_RATE * dt, TURN_RATE * dt)
    yaw = wrapRadians(yaw + turn)

    val ahead = (x - cameraX) * forward.first + (z - cameraZ) * forward.second
    val gain = if (ahead < MIN_AHEAD) FOLLOW_GAIN * 1.8f else FOLLOW_GAIN
    val speed = (gap * gain).coerceIn(0f, MAX_SPEED)
    val step = min(speed * dt, gap)
    if (gap > 1e-4f && step > 0f) {
      x += dx / gap * step
      z += dz / gap * step
    }

    val clip = when {
      speed < IDLE_SPEED -> CLIP_IDLE
      speed < GALLOP_SPEED -> CLIP_WALK
      else -> CLIP_GALLOP
    }
    return frame(visible = true, clip = clip, speed = speed)
  }

  private fun horizontalForward(lookX: Float, lookZ: Float): Pair<Float, Float>? {
    val length = hypot(lookX, lookZ)
    if (length >= MIN_HORIZONTAL) {
      headingX = lookX / length
      headingZ = lookZ / length
      hasHeading = true
    }
    if (!hasHeading) return null
    return headingX to headingZ
  }

  private fun frame(visible: Boolean, clip: String, speed: Float) = DogFrame(
    x = x,
    y = y,
    z = z,
    yawDegrees = Math.toDegrees(yaw.toDouble()).toFloat(),
    visible = visible && placed,
    clip = clip,
    speedMetersPerSecond = speed,
  )

  private fun wrapRadians(angle: Float): Float {
    var value = angle
    while (value > PI) value -= (2.0 * PI).toFloat()
    while (value < -PI) value += (2.0 * PI).toFloat()
    return value
  }

  private companion object {
    const val LEAD_METERS = 1.8f
    const val MAX_SPEED = 4.2f
    const val TURN_RATE = 5.2f
    const val FOLLOW_GAIN = 5f
    const val GROUND_FOLLOW = 10f
    const val MIN_HORIZONTAL = 0.15f
    const val YAW_LOCK_GAP = 0.2f
    const val MIN_AHEAD = 0.9f
    const val IDLE_SPEED = 0.18f
    const val GALLOP_SPEED = 2.05f
    const val CLIP_IDLE = "Idle"
    const val CLIP_WALK = "Walk"
    const val CLIP_GALLOP = "Gallop"
  }
}

data class DogFrame(
  val x: Float,
  val y: Float,
  val z: Float,
  val yawDegrees: Float,
  val visible: Boolean,
  val clip: String,
  val speedMetersPerSecond: Float,
)
