package com.usee.animal.ar

import android.opengl.Matrix
import io.github.sceneview.node.Node
import kotlin.math.*
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
  private val tail = emptyNodes.firstOrNull { it.name == "Tail_01" }
  private val lowerSpine = emptyNodes.firstOrNull { it.name == "Spine_02" }
  private val middleSpine = emptyNodes.firstOrNull { it.name == "Spine_03" }
  private val upperSpine = emptyNodes.firstOrNull { it.name == "Spine_04" }
  private class AttentionBone(val node: Node) {
    val original = FloatArray(16)
    var applied = false
  }
  // Parent-to-child order makes the total spine bend propagate to shoulders and forelegs.
  private val attentionBones = listOfNotNull(lowerSpine, middleSpine, upperSpine, bodyFront, neck, head, tail).map { AttentionBone(it) }
  private val attentionWorld = FloatArray(16)
  private val attentionParent = FloatArray(16)
  private val attentionInverse = FloatArray(16)
  private val attentionYawRotation = FloatArray(16)
  private val attentionPitchRotation = FloatArray(16)
  private val attentionRotation = FloatArray(16)
  private val attentionRotated = FloatArray(16)
  private val attentionLocal = FloatArray(16)
  private var attentionStartedAt = 0L
  private var attentionLastPose = 0L
  private var attentionYawDegrees = 0f
  var attentionTarget: (() -> Position?)? = null

  fun startAttention() {
    if (neck != null && head != null && tail != null) {
      attentionStartedAt = System.nanoTime()
      attentionLastPose = 0L
    }
  }

  fun cancelAttention() {
    attentionStartedAt = 0L
    restoreAttentionBones()
  }

  private fun restoreAttentionBones() {
    attentionBones.filter { it.applied }.forEach {
      transformManager.setTransform(it.node.transformInstance, it.original)
      it.applied = false
    }
  }

  private fun applyAttention(now: Long): Boolean {
    if (attentionStartedAt == 0L) return false
    val seconds = ((now - attentionStartedAt) / 1_000_000_000f).coerceAtLeast(0f)
    if (seconds >= 3f) { attentionStartedAt = 0L; return false }
    val target = attentionTarget?.invoke() ?: return false
    val h = head?.worldPosition ?: return false
    val n = nose?.worldPosition ?: return false
    val dx = target.x - h.x
    val dz = target.z - h.z
    val targetYaw = attentionYaw(n.x - h.x, n.z - h.z, dx, dz) * 180f / PI.toFloat()
    attentionYawDegrees = if (attentionLastPose == 0L) targetYaw else
      smoothAttentionYaw(attentionYawDegrees, targetYaw, (now - attentionLastPose) / 1_000_000_000f)
    attentionLastPose = now
    val yaw = attentionYawDegrees
    val pitch = ((atan2(target.y - h.y, hypot(dx, dz)) -
      atan2(n.y - h.y, hypot(n.x - h.x, n.z - h.z))) * 180f / PI.toFloat()).coerceIn(-25f, 25f)
    val weight = attentionWeight(seconds, 3f)
    val torsoYaw = attentionTorsoDegrees(yaw, seconds)
    val remainingYaw = yaw * weight - torsoYaw
    val cameraYaw = atan2(dx, dz)
    // Capture the animated pose first. Restore it before the next animation update,
    // so these offsets never accumulate or overwrite another animation's baseline.
    attentionBones.forEach { transformManager.getTransform(it.node.transformInstance, it.original) }
    attentionBones.forEach { bone ->
      when (bone.node) {
        lowerSpine -> rotateAttentionBone(bone, torsoYaw * 0.15f, 0f, cameraYaw)
        middleSpine -> rotateAttentionBone(bone, torsoYaw * 0.25f, 0f, cameraYaw)
        upperSpine, bodyFront -> rotateAttentionBone(bone, torsoYaw * 0.30f, 0f, cameraYaw)
        neck -> rotateAttentionBone(bone, remainingYaw * 0.67f, pitch * 0.5f * weight, cameraYaw)
        head -> rotateAttentionBone(bone, remainingYaw * 0.33f, pitch * 0.5f * weight, cameraYaw)
        tail -> rotateAttentionBone(bone, attentionTailDegrees(seconds), 0f, cameraYaw)
      }
    }
    return true
  }

  /** Rotate just the selected joint basis in world axes while keeping its origin fixed. */
  private fun rotateAttentionBone(bone: AttentionBone, yaw: Float, pitch: Float, cameraYaw: Float) {
    val instance = bone.node.transformInstance
    transformManager.getWorldTransform(instance, attentionWorld)
    Matrix.setRotateM(attentionYawRotation, 0, yaw, 0f, 1f, 0f)
    Matrix.setRotateM(attentionPitchRotation, 0, -pitch, cos(cameraYaw), 0f, -sin(cameraYaw))
    Matrix.multiplyMM(attentionRotation, 0, attentionPitchRotation, 0, attentionYawRotation, 0)
    Matrix.multiplyMM(attentionRotated, 0, attentionRotation, 0, attentionWorld, 0)
    for (index in 12..15) attentionRotated[index] = attentionWorld[index]
    val parent = transformManager.getParent(instance)
    if (parent != 0) {
      transformManager.getWorldTransform(transformManager.getInstance(parent), attentionParent)
      if (!Matrix.invertM(attentionInverse, 0, attentionParent, 0)) return
      Matrix.multiplyMM(attentionLocal, 0, attentionInverse, 0, attentionRotated, 0)
      transformManager.setTransform(instance, attentionLocal)
    } else transformManager.setTransform(instance, attentionRotated)
    bone.applied = true
  }
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
    restoreAttentionBones()
    super.onFrame(frameTimeNanos)
    if (manualPlayback) {
      applyPose(frameTimeNanos)
      if (applyAttention(frameTimeNanos)) animator.updateBoneMatrices()
      onPoseUpdated?.invoke(this, frameTimeNanos)
      return
    }
    val blended = blendPrevious(frameTimeNanos)
    if (applyAttention(frameTimeNanos) || blended) animator.updateBoneMatrices()
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
