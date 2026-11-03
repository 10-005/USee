package com.usee.animal.ar

import kotlin.random.Random

/** Only advances while the room is visible. No movement or root translation. */
internal class RoomPetBehavior(
  private val durations: Map<String, Float>,
  returningFromWalk: Boolean = false,
  private val nextDelay: () -> Float = { Random.nextInt(18, 36).toFloat() },
) {
  private var arriving = returningFromWalk
  private var walkingSeconds = 0f
  private var sittingSeconds = 0f
  private var sitting = false
  private var elapsed = 0f
  private var remaining = 0f
  private var scratchAt = nextDelay()
  private val queue = ArrayDeque<String>()
  var animation = DogAnimation(if (returningFromWalk) "Walk" else "SitIdle")
    private set
  val resting get() = elapsed >= 10f
  val walking get() = arriving && !sitting

  fun update(dt: Float): DogAnimation {
    if (arriving) {
      if (!sitting) {
        walkingSeconds += dt
        if (walkingSeconds >= RETURN_WALK_SECONDS) {
          sitting = true
          animation = DogAnimation("SitStart", false, animation.revision + 1)
        }
      } else {
        sittingSeconds += dt
        if (sittingSeconds >= (durations["SitStart"] ?: 1.25f)) {
          arriving = false
          animation = DogAnimation("SitIdle", true, animation.revision + 1)
        }
      }
      return animation
    }
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

  companion object {
    const val RETURN_WALK_SECONDS = 3f
  }
}
