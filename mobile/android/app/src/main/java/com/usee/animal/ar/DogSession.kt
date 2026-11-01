package com.usee.animal.ar

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.hypot

internal class SessionScratch {
  val lookLocal = floatArrayOf(0f, 0f, -1f)
  val lookWorld = FloatArray(3)
  val origin = FloatArray(3)
  val down = floatArrayOf(0f, -1f, 0f)
  var lastTimestamp = 0L
  var failure: TrackingFailureReason? = null
}

internal data class ShownDog(
  val status: String,
  val dog: DogFrame,
)

internal fun updateDog(
  session: Session,
  frame: Frame,
  leader: DogLeader,
  scratch: SessionScratch,
  modelReady: Boolean,
): ShownDog {
  val camera = frame.camera
  val tracking = camera.trackingState == TrackingState.TRACKING
  if (!modelReady) {
    return ShownDog("searching", hiddenDog())
  }
  if (!tracking) {
    val status = if (scratch.failure == TrackingFailureReason.CAMERA_UNAVAILABLE) "error" else "lost"
    return ShownDog(status, hiddenDog())
  }

  val pose = camera.pose
  pose.rotateVector(scratch.lookLocal, 0, scratch.lookWorld, 0)
  val now = frame.timestamp
  val dt = if (scratch.lastTimestamp == 0L) 0f else (now - scratch.lastTimestamp) / 1_000_000_000f
  scratch.lastTimestamp = now

  val ground = groundHeight(
    frame = frame,
    session = session,
    cameraX = pose.tx(),
    cameraY = pose.ty(),
    cameraZ = pose.tz(),
    forwardX = scratch.lookWorld[0],
    forwardZ = scratch.lookWorld[2],
    scratch = scratch,
  )
  val dog = leader.update(
    deltaSeconds = dt,
    cameraX = pose.tx(),
    cameraZ = pose.tz(),
    lookX = scratch.lookWorld[0],
    lookZ = scratch.lookWorld[2],
    groundY = ground,
  )
  return if (dog.visible) {
    ShownDog("anchored", dog)
  } else {
    ShownDog("searching", dog)
  }
}

private fun hiddenDog() = DogFrame(
  x = 0f,
  y = 0f,
  z = 0f,
  yawDegrees = 0f,
  visible = false,
  clip = "Idle",
  speedMetersPerSecond = 0f,
)

private fun groundHeight(
  frame: Frame,
  session: Session,
  cameraX: Float,
  cameraY: Float,
  cameraZ: Float,
  forwardX: Float,
  forwardZ: Float,
  scratch: SessionScratch,
): Float? {
  val horizontal = hypot(forwardX, forwardZ).coerceAtLeast(0.001f)
  val aheadX = cameraX + forwardX / horizontal * 1.8f
  val aheadZ = cameraZ + forwardZ / horizontal * 1.8f
  raycastFloor(frame, aheadX, cameraY, aheadZ, scratch)?.let { return it }
  raycastFloor(frame, cameraX, cameraY, cameraZ, scratch)?.let { return it }
  return session.getAllTrackables(Plane::class.java)
    .asSequence()
    .filter { plane ->
      plane.trackingState == TrackingState.TRACKING &&
        plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
        plane.subsumedBy == null &&
        plane.centerPose.ty() < cameraY - 0.4f
    }
    .minByOrNull { plane ->
      val dx = plane.centerPose.tx() - aheadX
      val dz = plane.centerPose.tz() - aheadZ
      hypot(dx, dz)
    }
    ?.centerPose
    ?.ty()
}

private fun raycastFloor(
  frame: Frame,
  x: Float,
  cameraY: Float,
  z: Float,
  scratch: SessionScratch,
): Float? {
  scratch.origin[0] = x
  scratch.origin[1] = cameraY
  scratch.origin[2] = z
  return try {
    frame.hitTest(scratch.origin, 0, scratch.down, 0)
      .firstOrNull { hit ->
        val plane = hit.trackable as? Plane
        plane != null &&
          plane.trackingState == TrackingState.TRACKING &&
          plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
          plane.subsumedBy == null
      }
      ?.hitPose
      ?.ty()
  } catch (_: Exception) {
    null
  }
}
