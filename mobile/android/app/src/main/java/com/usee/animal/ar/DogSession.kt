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
  val navigation = WalkabilityMemory()
  val depth = DepthTerrain()
  val userGround = UserWalkRegion()
  val stops = WalkStopStats()
  val diagnostics = WalkDiagnostics()
  val groundLevel = GroundLevelFilter()
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
  val diagnosticNow = SystemClock.elapsedRealtimeNanos()
  val frameIntervalNanos = if (scratch.lastTimestamp > 0L && frame.timestamp > scratch.lastTimestamp)
    frame.timestamp - scratch.lastTimestamp else null
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
    if (scratch.lostSince == 0L) {
      scratch.lostSince = now
      scratch.navigation.clear()
      scratch.depth.clear()
      scratch.userGround.clear()
      scratch.groundLevel.clear()
    }
    if (leader.isPlaced) recordStop(scratch, "추적 끊김", diagnosticNow, frameIntervalNanos,
      floorMask, semanticFailed, camera.trackingFailureReason.name)
    val failed = camera.trackingFailureReason == TrackingFailureReason.CAMERA_UNAVAILABLE
    // Brief tracking noise freezes the model rather than flashing it on/off.
    if (!failed && now - scratch.lostSince < 200_000_000L) return scratch.lastShown
    return ShownDog(if (failed) "error" else "lost", hiddenDog())
  }
  scratch.lostSince = 0L
  val semanticNow = SystemClock.elapsedRealtimeNanos()
  // SceneView may deliver several render callbacks for a single camera frame.
  // Reuse its result instead of resetting motion or querying the floor again.
  if (frame.timestamp == scratch.lastTimestamp) return scratch.lastShown
  val pose = camera.pose
  pose.rotateVector(scratch.lookLocal, 0, scratch.lookWorld, 0)
  val now = frame.timestamp
  val dt = if (scratch.lastTimestamp == 0L) 0f else (now - scratch.lastTimestamp) / 1_000_000_000f
  scratch.lastTimestamp = now
  scratch.depth.update(frame, semanticNow)

  // Reuse ARCore's detected polygons. No raycast or whole-plane enumeration
  // for every candidate point; refresh the plane collection only at 5 Hz.
  if (scratch.planesTimestamp == 0L || now - scratch.planesTimestamp >= 200_000_000L) {
    scratch.planes = session.getAllTrackables(Plane::class.java)
    scratch.walls = scratch.planes.mapNotNull { wallSegment(it) }
    leader.updateWalls(scratch.walls)
    scratch.planesTimestamp = now
  }
  val cameraY = pose.ty()
  val detectedGround = floorYAt(scratch.planes, cameraY, pose.tx(), pose.tz())
  if (scratch.groundLevel.isEstimated && detectedGround != null) scratch.userGround.clear()
  val userGroundY = scratch.groundLevel.update(detectedGround, cameraY - 1.35f, scratch.planesTimestamp)
  scratch.userGround.update(pose.tx(), pose.tz(), userGroundY)
  leader.setTrustedGround(scratch.userGround::heightAt)
  leader.setRouteGround { x, z ->
    scratch.userGround.heightAt(x, z) ?: floorYAt(scratch.planes, cameraY, x, z, userGroundY)?.takeIf { ground ->
      when (scratch.depth.latest?.evidence(x, ground, z, semanticNow) ?: FloorEvidence.UNKNOWN) {
        FloorEvidence.CLEAR -> true
        FloorEvidence.OBSTACLE -> false
        FloorEvidence.UNKNOWN -> floorMask != null && !semanticFailed &&
          walkableFloorAt(scratch.planes, cameraY, floorMask, x, z, semanticNow, userGroundY) != null
      }
    }
  }
  leader.beginNavigationFrame()
  fun walkableFloor(x: Float, z: Float): Float? {
    scratch.userGround.heightAt(x, z)?.let { return it }
    val ground = floorYAt(scratch.planes, cameraY, x, z, userGroundY)
    val geometry = ground?.let { scratch.depth.latest?.evidence(x, it, z, semanticNow) } ?: FloorEvidence.UNKNOWN
    val evidence = if (geometry != FloorEvidence.UNKNOWN) geometry
      else if (ground == null || floorMask == null || semanticFailed) FloorEvidence.UNKNOWN
      else when (floorMask.evidenceAt(x, ground, z, semanticNow)) {
        FloorEvidence.CLEAR -> if (walkableFloorAt(scratch.planes, cameraY, floorMask, x, z, semanticNow, userGroundY) != null) FloorEvidence.CLEAR else FloorEvidence.UNKNOWN
        FloorEvidence.OBSTACLE -> FloorEvidence.OBSTACLE
        FloorEvidence.UNKNOWN -> FloorEvidence.UNKNOWN
      }
    val decision = scratch.navigation.resolve(x, z, ground, evidence,
      if (geometry != FloorEvidence.UNKNOWN) scratch.depth.latest!!.observationId else floorMask?.capturedAtNanos ?: -1L, semanticNow, leader.isPlaced)
    leader.observeNavigation(decision.cautious, if (semanticFailed && decision.height == null) "분석 실패" else decision.reason)
    return decision.height
  }
  val dog = leader.update(dt, pose.tx(), pose.tz(), scratch.lookWorld[0], scratch.lookWorld[2]) { x, z ->
    walkableFloor(x, z)
  }
  if (leader.isPlaced) recordStop(scratch, when {
    dt > 0.25f -> "프레임 지연"
    dog.blocked -> dog.stopReason
    else -> ""
  }, SystemClock.elapsedRealtimeNanos(), frameIntervalNanos, floorMask, semanticFailed)
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

private val FOOTPRINT = arrayOf(0f to 0f, -0.12f to 0f, 0.12f to 0f, 0f to -0.12f, 0f to 0.12f)

private fun recordStop(scratch: SessionScratch, reason: String, now: Long, frameIntervalNanos: Long?,
  floorMask: SemanticFloorMask?, semanticFailed: Boolean, trackingFailure: String? = null) {
  scratch.stops.record(reason, now)
  val report = scratch.diagnostics.report(reason, now, frameIntervalNanos, floorMask, semanticFailed,
    scratch.depth.latest, scratch.depth.supported, trackingFailure, scratch.stops)
  if (report != null) android.util.Log.i("ArWalkStops", report)
}

/** Shared by motion and diagnostics so the overlay cannot invent a different floor policy. */
internal fun walkableFloorAt(planes: Collection<Plane>, cameraY: Float, mask: SemanticFloorMask, x: Float, z: Float, now: Long, referenceY: Float? = null): Float? {
  val ground = floorYAt(planes, cameraY, x, z, referenceY) ?: return null
  if (!mask.allows(x, ground, z, now)) return null
  val accepted = FOOTPRINT.count { (dx, dz) -> mask.allows(x + dx, ground, z + dz, now) }
  return if (accepted >= 4) ground else null
}

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

internal fun floorYAt(planes: Collection<Plane>, cameraY: Float, x: Float, z: Float, referenceY: Float? = null): Float? {
  val standingHeight = referenceY ?: (cameraY - 1.35f)
  var bestY: Float? = null
  var bestDistance = Float.POSITIVE_INFINITY
  for (plane in planes) {
    if (plane.trackingState != TrackingState.TRACKING ||
      plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING || plane.subsumedBy != null) continue
    val y = plane.centerPose.ty()
    if (!y.isFinite() || y > cameraY - 0.35f || y < cameraY - 2.5f) continue
    val distance = abs(y - standingHeight)
    if (referenceY != null && distance > 0.45f) continue
    if (distance < bestDistance && plane.isPoseInPolygon(Pose.makeTranslation(x, y, z))) {
      bestY = y
      bestDistance = distance
    }
  }
  return bestY
}
