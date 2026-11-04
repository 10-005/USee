package com.usee.animal.ar

import io.github.sceneview.model.ModelInstance
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode

internal class DogModelNode(
  instance: ModelInstance,
  private val manualPlayback: Boolean = false,
) : ModelNode(
  modelInstance = instance, autoAnimate = false, scaleToUnits = DOG_MODEL_UNITS,
) {
  private val indices = (0 until animationCount).associateBy { animator.getAnimationName(it) }
  val durations = indices.mapValues { animator.getAnimationDuration(it.value) }
  private val leashSocket = emptyNodes.firstOrNull { it.name == "LeashAttachment" }
  private val neck = emptyNodes.firstOrNull { it.name == "neck" }
  private val bodyBack = emptyNodes.firstOrNull { it.name == "Spine_base" }
  private val bodyFront = emptyNodes.firstOrNull { it.name == "Spine_05" }
  private val head = emptyNodes.firstOrNull { it.name == "head" }
  private val nose = emptyNodes.firstOrNull { it.name == "nose" }
  private val leftSocket = emptyNodes.firstOrNull { it.name == "LeashAttachmentLeft" }
  private val rightSocket = emptyNodes.firstOrNull { it.name == "LeashAttachmentRight" }
  private var frontLeash = false
  /** Read after animator updates: capsules follow the same pose as the collar sockets. */
  fun leashBody(): LeashBody? {
    fun Position.point() = LeashPoint(x,y,z)
    val back = bodyBack?.worldPosition?.point() ?: return null
    val front = bodyFront?.worldPosition?.point() ?: return null
    val neckPoint = neck?.worldPosition?.point() ?: return null
    val headPoint = head?.worldPosition?.point() ?: return null
    val nosePoint = nose?.worldPosition?.point() ?: return null
    val origin = worldPosition.point()
    val scale = (getWorldPosition(Position(x = 1f)).point() - origin).length()
    return LeashBody(listOf(
      LeashCapsule(back,front,0.085f*scale + 0.009f),
      LeashCapsule(neckPoint,headPoint,0.045f*scale + 0.009f),
      LeashCapsule(headPoint + (nosePoint-headPoint)*0.25f,nosePoint,0.065f*scale + 0.009f),
    ),neckPoint)
  }
  val leashPosition: Position?
    get() = leashSocket?.worldPosition ?: neck?.getWorldPosition(Position(0f, 0.05069732f, 0.05752291f))
  fun leashPositionFor(camera: LeashPoint, cameraRight: LeashPoint): Position? {
    val origin = worldPosition
    val forward = getWorldPosition(Position(z = 1f)) - origin
    val facing = LeashInteraction.facingViewer(
      LeashPoint(forward.x, 0f, forward.z), LeashPoint(camera.x - origin.x, 0f, camera.z - origin.z), frontLeash,
    )
    frontLeash = facing
    if (!facing) return leashPosition
    val left = leftSocket?.worldPosition ?: return leashPosition
    val right = rightSocket?.worldPosition ?: return leashPosition
    val separation = LeashPoint(left.x - right.x, left.y - right.y, left.z - right.z)
    val side = separation.x * cameraRight.x + separation.y * cameraRight.y + separation.z * cameraRight.z
    return if (side <= 0f) left else right
  }
  var onPoseUpdated: ((DogModelNode, Long) -> Unit)? = null
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
    if (manualPlayback) {
      // Never expose the bind pose while waiting for the next SceneView frame.
      applyPose(now)
    } else {
      playAnimation(next, loop = state.loop)
    }
  }

  override fun onFrame(frameTimeNanos: Long) {
    super.onFrame(frameTimeNanos)
    if (manualPlayback) {
      applyPose(frameTimeNanos)
      onPoseUpdated?.invoke(this, frameTimeNanos)
      return
    }
    if (blendPrevious(frameTimeNanos)) animator.updateBoneMatrices()
    onPoseUpdated?.invoke(this, frameTimeNanos)
  }

  private fun applyPose(frameTimeNanos: Long) {
    if (current < 0) return
    val elapsed = (frameTimeNanos - startedAt) / 1_000_000_000f
    animator.applyAnimation(current, sampleAnimationTime(elapsed, animator.getAnimationDuration(current), looping))
    blendPrevious(frameTimeNanos)
    animator.updateBoneMatrices()
  }

  private fun blendPrevious(frameTimeNanos: Long): Boolean {
    if (previous < 0) return false
    val alpha = ((frameTimeNanos - transitionStartedAt) / 150_000_000f).coerceIn(0f, 1f)
    if (alpha >= 1f) {
      previous = -1
      return false
    }
    val duration = animator.getAnimationDuration(previous).coerceAtLeast(0.001f)
    val elapsed = ((frameTimeNanos - previousStartedAt) / 1_000_000_000f).coerceAtLeast(0f)
    val time = if (manualPlayback) sampleAnimationTime(elapsed, duration, previousLooping)
      else if (previousLooping) elapsed % duration else elapsed.coerceAtMost(duration)
    animator.applyCrossFade(previous, time, alpha)
    return true
  }
}

/** Hold one-shot clips just before their wrap point; clamp early frame timestamps. */
internal fun sampleAnimationTime(elapsed: Float, duration: Float, loop: Boolean): Float {
  if (duration <= 0f) return 0f
  val time = elapsed.coerceAtLeast(0f)
  return if (loop) time % duration else time.coerceAtMost((duration - 0.0001f).coerceAtLeast(0f))
}
