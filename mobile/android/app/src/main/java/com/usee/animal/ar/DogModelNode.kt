package com.usee.animal.ar

import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode

internal class DogModelNode(instance: ModelInstance) : ModelNode(
  modelInstance = instance, autoAnimate = false, scaleToUnits = 0.55f,
) {
  private val indices = (0 until animationCount).associateBy { animator.getAnimationName(it) }
  val durations = indices.mapValues { animator.getAnimationDuration(it.value) }
  private var current = -1
  private var revision = -1
  private var startedAt = 0L
  private var looping = true
  private var previous = -1
  private var previousStartedAt = 0L
  private var previousLooping = true
  private var transitionStartedAt = 0L

  fun play(state: DogAnimation) {
    if (state.revision == revision) return
    val next = indices[state.clip] ?: indices.getValue("Idle")
    val now = System.nanoTime()
    previous = current
    previousStartedAt = startedAt
    previousLooping = looping
    transitionStartedAt = now
    if (current >= 0) stopAnimation(current)
    current = next
    revision = state.revision
    startedAt = now
    looping = state.loop
    playAnimation(next, loop = state.loop)
  }

  override fun onFrame(frameTimeNanos: Long) {
    super.onFrame(frameTimeNanos)
    if (previous < 0) return
    val alpha = ((frameTimeNanos - transitionStartedAt) / 150_000_000f).coerceIn(0f, 1f)
    if (alpha >= 1f) {
      previous = -1
      return
    }
    val duration = animator.getAnimationDuration(previous).coerceAtLeast(0.001f)
    val elapsed = ((frameTimeNanos - previousStartedAt) / 1_000_000_000f).coerceAtLeast(0f)
    val time = if (previousLooping) elapsed % duration else elapsed.coerceAtMost(duration)
    animator.applyCrossFade(previous, time, alpha)
    animator.updateBoneMatrices()
  }
}
