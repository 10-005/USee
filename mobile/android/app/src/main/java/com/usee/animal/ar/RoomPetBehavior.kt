package com.usee.animal.ar

import kotlin.random.Random

/** Only advances while the room is visible. No movement or root translation. */
internal class RoomPetBehavior(
  private val durations: Map<String, Float>,
  private val nextDelay: () -> Float = { Random.nextInt(18, 36).toFloat() },
) {
  private var elapsed = 0f
  private var remaining = 0f
  private var scratchAt = nextDelay()
  private val queue = ArrayDeque<String>()
  var animation = DogAnimation("SitIdle")
    private set
  val resting get() = elapsed >= 10f

  fun update(dt: Float): DogAnimation {
    elapsed += dt
    if (queue.isNotEmpty()) {
      remaining -= dt
      if (remaining <= 0f) advance()
    } else if (elapsed >= scratchAt) {
      scratchAt = elapsed + nextDelay()
      queue.addAll(if (resting) listOf("LieEnd", "ScratchEar", "LieStart", "LieIdle")
        else listOf("ScratchEar", "SitIdle"))
      advance()
    } else if (resting && animation.clip == "SitIdle") {
      queue.addAll(listOf("SitEnd", "LieStart", "LieIdle"))
      advance()
    }
    return animation
  }

  private fun advance() {
    val clip = queue.removeFirst()
    val loop = queue.isEmpty()
    remaining = durations[clip] ?: 1f
    animation = DogAnimation(clip, loop, animation.revision + 1)
  }
}
