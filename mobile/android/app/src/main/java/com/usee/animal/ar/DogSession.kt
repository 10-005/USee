package com.usee.animal.ar

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot

internal class SessionScratch {
  val lookLocal = floatArrayOf(0f, 0f, -1f)
  val lookWorld = FloatArray(3)
  var lastTimestamp = 0L
  var planesTimestamp = 0L
  var planes: Collection<Plane> = emptyList()
  var walls: List<WallSegment> = emptyList()
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
  floorMask: SemanticFloorMask?,
  semanticFailed: Boolean,
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
  if (semanticFailed) {
    leader.pauseTracking()
    return ShownDog("recognitionError", hiddenDog())
  }
  val semanticNow = SystemClock.elapsedRealtimeNanos()
  if (floorMask == null || semanticNow - floorMask.capturedAtNanos > SemanticFloorMask.MAX_AGE_NANOS) {
    scratch.lastTimestamp = 0L
    return ShownDog("analyzing", leader.pausedFrame()).also { scratch.lastShown = it }
  }
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
    scratch.walls = scratch.planes.mapNotNull { wallSegment(it) }
    leader.updateWalls(scratch.walls)
    scratch.planesTimestamp = now
  }
  val cameraY = pose.ty()
  fun walkableFloor(x: Float, z: Float): Float? {
    val ground = floorYAt(scratch.planes, cameraY, x, z) ?: return null
    // Check a small footprint, not just one pixel at the center of the dog.
    for ((dx, dz) in FOOTPRINT) {
      if (!floorMask.allows(x + dx, ground, z + dz, semanticNow)) return null
    }
    return ground
  }
  val dog = leader.update(dt, pose.tx(), pose.tz(), scratch.lookWorld[0], scratch.lookWorld[2]) { x, z ->
    walkableFloor(x, z)
  }
  val lookLength = hypot(scratch.lookWorld[0], scratch.lookWorld[2])
  var ready = false
  if (!leader.isPlaced && lookLength > 0.15f) {
    val targetX = pose.tx() + scratch.lookWorld[0] / lookLength * DOG_LEAD_METERS
    val targetZ = pose.tz() + scratch.lookWorld[2] / lookLength * DOG_LEAD_METERS
    val ground = walkableFloor(targetX, targetZ)
    ready = ground != null && leader.canPlaceAt(pose.tx(), pose.tz(), targetX, targetZ, ground)
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

private val FOOTPRINT = arrayOf(0f to 0f, -0.15f to 0f, 0.15f to 0f, 0f to -0.15f, 0f to 0.15f)

private fun hiddenDog() = DogFrame(0f, 0f, 0f, 0f, false, "Idle", 0f)

private fun wallSegment(plane: Plane): WallSegment? {
  if (plane.type != Plane.Type.VERTICAL || plane.trackingState == TrackingState.STOPPED || plane.subsumedBy != null) return null
  val polygon = plane.polygon.duplicate().apply { rewind() }
  if (polygon.remaining() < 6) return null
  val pose = plane.centerPose
  val normal = pose.rotateVector(floatArrayOf(0f, 1f, 0f))
  val horizontal = hypot(normal[0], normal[2])
  if (horizontal < 0.8f) return null
  val tangentX = -normal[2] / horizontal
  val tangentZ = normal[0] / horizontal
  var low = Float.POSITIVE_INFINITY
  var high = Float.NEGATIVE_INFINITY
  var minY = Float.POSITIVE_INFINITY
  var maxY = Float.NEGATIVE_INFINITY
  while (polygon.remaining() >= 2) {
    val world = pose.transformPoint(floatArrayOf(polygon.get(), 0f, polygon.get()))
    val along = (world[0] - pose.tx()) * tangentX + (world[2] - pose.tz()) * tangentZ
    low = minOf(low, along)
    high = maxOf(high, along)
    minY = minOf(minY, world[1])
    maxY = maxOf(maxY, world[1])
  }
  if (high - low < 0.05f) return null
  return WallSegment(
    pose.tx() + tangentX * low, pose.tz() + tangentZ * low,
    pose.tx() + tangentX * high, pose.tz() + tangentZ * high,
    minY, maxY,
  )
}

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
