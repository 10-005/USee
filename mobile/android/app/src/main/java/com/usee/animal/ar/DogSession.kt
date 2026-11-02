package com.usee.animal.ar

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.hypot

internal class SessionScratch {
  val lookLocal = floatArrayOf(0f, 0f, -1f)
  val lookWorld = FloatArray(3)
  var lastTimestamp = 0L
  var planesTimestamp = 0L
  var planes: Collection<Plane> = emptyList()
  var lostSince = 0L
  var lastShown = ShownDog("searching", hiddenDog())
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
  if (!modelReady) {
    leader.cancelPlacement()
    return ShownDog("searching", hiddenDog())
  }
  if (camera.trackingState != TrackingState.TRACKING) {
    leader.cancelPlacement()
    leader.pauseTracking()
    scratch.lastTimestamp = 0L
    scratch.planesTimestamp = 0L
    val now = System.nanoTime()
    if (scratch.lostSince == 0L) scratch.lostSince = now
    val failed = camera.trackingFailureReason == TrackingFailureReason.CAMERA_UNAVAILABLE
    // Brief tracking noise freezes the model rather than flashing it on/off.
    if (!failed && now - scratch.lostSince < 200_000_000L) return scratch.lastShown
    return ShownDog(if (failed) "error" else "lost", hiddenDog())
  }
  scratch.lostSince = 0L
  // SceneView may deliver several render callbacks for a single camera frame.
  // Reuse its result instead of resetting motion or querying the floor again.
  if (frame.timestamp == scratch.lastTimestamp) return scratch.lastShown
  val pose = camera.pose
  pose.rotateVector(scratch.lookLocal, 0, scratch.lookWorld, 0)
  val now = frame.timestamp
  val dt = if (scratch.lastTimestamp == 0L) 0f else (now - scratch.lastTimestamp) / 1_000_000_000f
  scratch.lastTimestamp = now

  // Reuse ARCore's detected polygons. No raycast or whole-plane enumeration
  // for every candidate point; refresh the plane collection only at 5 Hz.
  if (scratch.planesTimestamp == 0L || now - scratch.planesTimestamp >= 200_000_000L) {
    scratch.planes = session.getAllTrackables(Plane::class.java)
    scratch.planesTimestamp = now
  }
  val cameraY = pose.ty()
  val dog = leader.update(dt, pose.tx(), pose.tz(), scratch.lookWorld[0], scratch.lookWorld[2]) { x, z ->
    floorYAt(scratch.planes, cameraY, x, z)
  }
  val lookLength = hypot(scratch.lookWorld[0], scratch.lookWorld[2])
  var ready = false
  if (!leader.isPlaced && lookLength > 0.15f) {
    val targetX = pose.tx() + scratch.lookWorld[0] / lookLength * DOG_LEAD_METERS
    val targetZ = pose.tz() + scratch.lookWorld[2] / lookLength * DOG_LEAD_METERS
    val ground = floorYAt(scratch.planes, cameraY, targetX, targetZ)
    ready = ground != null
  }
  val status = when {
    dog.visible -> "anchored"
    ready -> "ready"
    else -> "searching"
  }
  return ShownDog(status, dog).also {
    scratch.lastShown = it
  }
}

private fun hiddenDog() = DogFrame(0f, 0f, 0f, 0f, false, "Idle", 0f)

private fun floorYAt(planes: Collection<Plane>, cameraY: Float, x: Float, z: Float): Float? {
  val standingHeight = cameraY - 1.35f
  var bestY: Float? = null
  var bestDistance = Float.POSITIVE_INFINITY
  for (plane in planes) {
    if (plane.trackingState != TrackingState.TRACKING ||
      plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING || plane.subsumedBy != null) continue
    val y = plane.centerPose.ty()
    if (y > cameraY - 0.35f) continue
    val distance = abs(y - standingHeight)
    if (distance < bestDistance && plane.isPoseInPolygon(Pose.makeTranslation(x, y, z))) {
      bestY = y
      bestDistance = distance
    }
  }
  return bestY
}
