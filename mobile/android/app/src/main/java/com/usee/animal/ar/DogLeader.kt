package com.usee.animal.ar

import kotlin.math.*

/** Predicts a walking companion's route in AR world coordinates. Model forward is local +Z. */
internal const val DOG_LEAD_METERS = 1.6f
internal const val DOG_MODEL_UNITS = 0.825f
internal const val DOG_BODY_RADIUS = 0.24f

class DogLeader(
  private val requiresPlacement: Boolean = false,
  private val animations: DogAnimationController = DogAnimationController(),
) {
  private enum class PoopStage { WALK_RIGHT, POOP, RETURN }
  private var poopStage: PoopStage? = null
  private var poopOriginX = 0f
  private var poopOriginZ = 0f
  private var poopOriginYaw = 0f
  private var poopTargetX = 0f
  private var poopTargetZ = 0f
  private var poopDeposited = false
  private data class Care(
    val action: String,
    val origin: LeashPoint,
    val target: LeashPoint,
    val travelSeconds: Float,
    val standSeconds: Float,
    var consuming: Boolean = false,
    var seconds: Float = 0f,
  )
  private var care: Care? = null
  internal val canGiveCare: Boolean
    get() = placed && care == null && spinElapsed == null && poopStage == null &&
      (animations.current.clip == "SitIdle" || animations.current.clip == "SitIdle2")

  internal fun giveCare(action: String, origin: LeashPoint): Boolean {
    if (!canGiveCare || (action != "drink" && action != "eat")) return false
    val frontX = x + sin(yaw) * 0.4f
    val frontZ = z + cos(yaw) * 0.4f
    val ground = trustedGround(frontX, frontZ)?.takeIf { abs(it - y) <= 0.08f } ?: y
    val target = LeashPoint(frontX, ground + if (action == "drink") 0.09f else 0.08f, frontZ)
    care = Care(
      action = action,
      origin = origin,
      target = target,
      travelSeconds = if (action == "drink") 0.55f else 0.65f,
      standSeconds = animations.clipSeconds("SitEnd") / CARE_STAND_RATE,
    )
    commandedHold = "sit"
    attentionSeconds = 0f
    animations.pause()
    animations.carePose("SitEnd", playbackRate = CARE_STAND_RATE)
    return true
  }

  internal data class CareItem(val action: String, val point: LeashPoint, val scale: Float)

  internal val careItem: CareItem?
    get() = care?.let { current ->
      if (current.action == "eat" && current.consuming && animations.current.clip != "EatDrinkStart") {
        null
      } else {
        val t = (current.seconds / current.travelSeconds).coerceIn(0f, 1f)
        val progress = if (current.action == "drink") t * t * (3f - 2f * t) else t
        val arc = if (current.action == "eat") 1.6f * t * (1f - t) else 0f
        val point = current.origin * (1f - progress) + current.target * progress
        CareItem(current.action, point.copy(y = point.y + arc), 0.18f + 0.82f * progress)
      }
    }

  fun beginPoop(rightX: Float, rightZ: Float) {
    if (!placed || poopStage != null || commandedHold != null || attentionSeconds > 0f) return
    val length = hypot(rightX, rightZ)
    if (length < 0.001f) return
    poopOriginX = x
    poopOriginZ = z
    poopOriginYaw = yaw
    poopTargetX = x + rightX / length * 0.40f
    poopTargetZ = z + rightZ / length * 0.40f
    poopStage = PoopStage.WALK_RIGHT
    poopDeposited = false
    returning = false
    recalling = false
    placementRequested = false
    floorPath.clear()
    animations.pause()
  }

  private var placed = false
  val isPlaced: Boolean get() = placed
  private var placementRequested = false
  private val wallAvoidance = WallAvoidance(bodyRadius = DOG_BODY_RADIUS, bodyHeight = DOG_MODEL_UNITS)
  private var trustedGround: (Float, Float) -> Float? = { _, _ -> null }
  private var routeGround: ((Float, Float) -> Float?)? = null
  private var shortRange = false
  internal val leadMeters get() = if (shortRange) LOW_LIGHT_LEAD_METERS else DOG_LEAD_METERS
  private val walkerClearance get() = if (shortRange) LOW_LIGHT_LEAD_METERS else 0.45f

  internal fun setShortRange(enabled: Boolean) {
    if (shortRange == enabled) return
    shortRange = enabled
    floorPath.clear()
    floorRetrySeconds = 0f
    floorDirectClearSeconds = 0f
    if (enabled) {
      returning = false
      // Re-place through the normal placement checks if the former lead is on blocked ground.
      if (placed && !recalling && care == null && spinElapsed == null) requestPlacement()
    }
  }

  internal fun setTrustedGround(provider: (Float, Float) -> Float?) { trustedGround = provider }
  internal fun setRouteGround(provider: (Float, Float) -> Float?) { routeGround = provider }

  private fun trustedSegment(ax: Float, az: Float, bx: Float, bz: Float): Boolean {
    val samples = ceil(hypot(bx - ax, bz - az) / 0.08f).toInt().coerceAtLeast(1)
    return (0..samples).all { i ->
      val t = i.toFloat() / samples
      trustedGround(ax + (bx - ax) * t, az + (bz - az) * t) != null
    }
  }

  private fun clearSegment(ax: Float, az: Float, bx: Float, bz: Float, ground: Float): Boolean =
    trustedSegment(ax, az, bx, bz) || wallAvoidance.isClear(ax, az, bx, bz, ground)

  fun beginNavigationFrame() { floorUncertain = false; floorFailureReason = "" }
  fun observeNavigation(cautious: Boolean, reason: String) {
    floorUncertain = floorUncertain || cautious
    if (reason.isNotEmpty()) floorFailureReason = reason
  }

  fun updateWalls(walls: List<WallSegment>) {
    wallAvoidance.walls = walls
  }

  fun canPlaceAt(cameraX: Float, cameraZ: Float, targetX: Float, targetZ: Float, floorY: Float) =
    clearSegment(cameraX, cameraZ, targetX, targetZ, floorY)

  internal fun placeFromCenter(cameraX: Float, cameraZ: Float, target: FloorWaypoint): DogFrame? {
    if (requiresPlacement || shortRange || poopStage != null || commandedHold != null || attentionSeconds > 0f ||
      !canPlaceAt(cameraX, cameraZ, target.x, target.z, target.y)) return null
    val dx = target.x - cameraX
    val dz = target.z - cameraZ
    val length = hypot(dx, dz)
    if (length <= 0f) return null
    headingX = dx / length
    headingZ = dz / length
    hasHeading = true
    yaw = atan2(headingX, headingZ)
    resetMovementFollowing()
    floorPath.clear()
    floorRetrySeconds = 0f
    floorDirectClearSeconds = 0f
    stopReason = ""
    turningToMove = false
    animations.pause()
    return placeAt(target.x, target.z, target.y)
  }

  private fun placeAt(targetX: Float, targetZ: Float, ground: Float): DogFrame {
    x = targetX
    z = targetZ
    y = ground
    placed = true
    placementRequested = false
    speed = 0f
    moving = false
    blocked = false
    returning = false
    recalling = false
    leashLength = 0f
    return frame(true, 0f)
  }

  fun requestPlacement() {
    if (poopStage != null || commandedHold != null || attentionSeconds > 0f) return
    placementRequested = true
    resetMovementFollowing()
  }

  fun cancelPlacement() {
    placementRequested = false
  }

  fun requestRecall() {
    if (attentionSeconds > 0f) return
    if (commandedHold != null) return
    if (placed && poopStage == null) {
      recalling = true
      resetMovementFollowing()
    }
  }

  fun updateLeashLength(meters: Float) {
    if (meters.isFinite() && meters >= 0f) leashLength = meters
  }

  fun needsUnrestrictedReturn(cameraX: Float, cameraZ: Float): Boolean = placed && poopStage == null && commandedHold == null && attentionSeconds <= 0f &&
    (recalling || !shortRange && !followingMovement && (returning || leashLength >= 2.6f || hypot(cameraX - x, cameraZ - z) >= 2.6f))

  fun requestAction(action: String, visibleHeadingDegrees: Float? = null) {
    if (!placed || poopStage != null) return
    turningToMove = false
    if (care != null) {
      if (action != "go" && action != "stop") return
      care = null
    }
    attentionSeconds = 0f
    if (action == "spin") {
      if (spinElapsed != null) return
      resetMovementFollowing()
      spinElapsed = 0f
      spinStartYaw = yaw
      commandedHold = "stop"
      animations.pause()
      recalling = false
      returning = false
      placementRequested = false
      speed = 0f
      moving = false
      floorPath.clear()
      return
    }
    spinElapsed = null
    if (action == "sit" || action == "stop" || action == "go") {
      val next = if (action == "go") null else action
      if (commandedHold == next) return
      resetMovementFollowing()
      // Navigation yaw can lead the rendered heading while a walking turn is
      // being smoothed. Sit in the direction actually visible at command time.
      if (action == "sit" && visibleHeadingDegrees?.isFinite() == true) {
        yaw = wrap(Math.toRadians(visibleHeadingDegrees.toDouble()).toFloat())
      }
      commandedHold = next
      animations.pause()
      if (action == "sit") animations.holdPosition(true, 0f)
      recalling = false
      returning = false
      placementRequested = false
      speed = 0f
      moving = false
      floorPath.clear()
      return
    }
    if (commandedHold != null) return
    animations.request(action)
  }

  private var commandedHold: String? = null
  private var attentionSeconds = 0f
  private var spinElapsed: Float? = null
  private var spinStartYaw = 0f

  fun requestAttention(): Boolean {
    if (!placed || poopStage != null || spinElapsed != null || care != null) return false
    attentionSeconds = 3f
    speed = 0f
    moving = false
    recalling = false
    returning = false
    placementRequested = false
    floorPath.clear()
    return true
  }

  fun pauseTracking() {
    care = null
    attentionSeconds = 0f
    if (spinElapsed != null) commandedHold = null
    spinElapsed = null
    hasCamera = false
    velocityX = 0f
    velocityZ = 0f
    speed = 0f
    if (poopStage != PoopStage.POOP) animations.pause()
    moving = false
    blocked = false
    userMoving = false
    motionPaused = true
    turningToMove = false
    resetMovementFollowing()
  }

  internal fun pausedFrame(): DogFrame {
    pauseTracking()
    return frame(placed, 0f)
  }
  private var x = 0f
  private var y = 0f
  private var z = 0f
  private var yaw = 0f
  private var headingX = 0f
  private var headingZ = -1f
  private var hasHeading = false
  private var previousCameraX = 0f
  private var previousCameraZ = 0f
  private var hasCamera = false
  private var velocityX = 0f
  private var velocityZ = 0f
  private var speed = 0f
  private var backing = false
  private var moving = false
  private var motionPaused = false
  private var turningToMove = false
  private val movementHeading = WalkMovementHeading()
  private var followingMovement = false
  private var catchingUp = false
  private var passingSide = 0f
  private var passedUserSide = false
  private var passedUserFront = false
  private fun resetMovementFollowing() {
    movementHeading.clear()
    followingMovement = false
    catchingUp = false
    passingSide = 0f
    passedUserSide = false
    passedUserFront = false
  }
  private var returning = false
  private var recalling = false
  private var leashLength = 0f
  private var blocked = false
  private var userMoving = false
  private var floorUncertain = false
  private var floorFailureReason = ""
  private var stopReason = ""
  private val floorPath = ArrayDeque<FloorWaypoint>()
  private var floorRetrySeconds = 0f
  private var floorDirectClearSeconds = 0f

  fun update(
    deltaSeconds: Float,
    cameraX: Float,
    cameraZ: Float,
    lookX: Float,
    lookZ: Float,
    floorAt: (x: Float, z: Float) -> Float?,
  ): DogFrame {
    // The renderer may run faster than the camera. Repeated samples are not a
    // tracking pause and must not erase acceleration or the walking animation.
    if (deltaSeconds == 0f && hasCamera) return frame(placed, speed)
    motionPaused = false
    turningToMove = false
    if (poopStage != null) {
      previousCameraX = cameraX
      previousCameraZ = cameraZ
      velocityX = 0f
      velocityZ = 0f
      hasCamera = true
      userMoving = false
      blocked = false
      return updatePoop(deltaSeconds.coerceIn(0f, 0.25f))
    }
    val length = hypot(lookX, lookZ)
    if (length > 0.15f && (!placed || recalling || placementRequested)) {
      headingX = lookX / length
      headingZ = lookZ / length
      hasHeading = true
    }
    if (!hasHeading) return frame(false, 0f)

    // Resume tracking without interpreting a pause or relocalization as walking.
    val continuous = hasCamera && deltaSeconds > 0f && deltaSeconds <= 0.25f
    // Valid camera intervals must advance the whole elapsed time. Segment floor
    // and wall checks still validate the full step at low frame rates.
    val dt = deltaSeconds.coerceIn(0f, 0.25f)
    var measuredX = 0f
    var measuredZ = 0f
    if (continuous) {
      val vx = (cameraX - previousCameraX) / deltaSeconds
      val vz = (cameraZ - previousCameraZ) / deltaSeconds
      if (hypot(vx, vz) < 4f) {
        measuredX = vx
        measuredZ = vz
        val blend = 1f - exp(-deltaSeconds / 0.3f)
        velocityX += (vx - velocityX) * blend
        velocityZ += (vz - velocityZ) * blend
      } else {
        velocityX = 0f
        velocityZ = 0f
      }
    } else {
      velocityX = 0f
      velocityZ = 0f
      speed = 0f
    }
    previousCameraX = cameraX
    previousCameraZ = cameraZ
    hasCamera = true
    care?.let { return updateCare(it, dt) }
    spinElapsed?.let { elapsed ->
      val next = (elapsed + deltaSeconds.coerceIn(0f, 0.25f)).coerceAtMost(3f)
      spinElapsed = next
      yaw = wrap(spinStartYaw + next / 3f * 2f * PI.toFloat())
      speed = 0f
      moving = false
      blocked = false
      userMoving = hypot(velocityX, velocityZ) > 0.15f
      stopReason = ""
      if (next >= 3f) {
        spinElapsed = null
        commandedHold = null
        yaw = spinStartYaw
        animations.pause()
        // Render the final 360-degree pose before ordinary stationary-heading
        // suppression takes over; following resumes on the next camera frame.
        return frame(true, 0f).copy(scriptedFacing = true)
      } else animations.holdSpin()
      return frame(true, 0f)
    }
    if (placed && (commandedHold != null || attentionSeconds > 0f)) {
      speed = 0f
      moving = false
      blocked = false
      userMoving = hypot(velocityX, velocityZ) > 0.15f
      stopReason = ""
      animations.holdPosition(commandedHold == "sit", dt)
      attentionSeconds = (attentionSeconds - deltaSeconds.coerceIn(0f, 0.25f)).coerceAtLeast(0f)
      return frame(true, 0f)
    }
    if (!recalling && !placementRequested && continuous) {
      val directionChanged = movementHeading.observe(measuredX, measuredZ, dt, headingX, headingZ)
      if (movementHeading.confirmed) followingMovement = true
      if (directionChanged) {
        followingMovement = true
        catchingUp = true
        passingSide = 0f
        passedUserSide = false
        passedUserFront = false
        floorPath.clear()
        floorRetrySeconds = 0f
      }
      if (followingMovement) {
        headingX = movementHeading.x
        headingZ = movementHeading.z
        // Recover the forward walking position instead of only shrinking a
        // radial leash distance, with ordinary floor/wall/user checks intact.
        returning = false
        val ahead = (x - cameraX) * headingX + (z - cameraZ) * headingZ
        val lateral = abs((x - cameraX) * headingZ - (z - cameraZ) * headingX)
        if (ahead >= leadMeters - 0.35f && lateral <= 0.4f) catchingUp = false
        else if (ahead < 0.5f || lateral > 0.8f) catchingUp = true
      }
    }
    val along = velocityX * headingX + velocityZ * headingZ
    if (followingMovement) backing = false
    else {
      if (along < -0.25f) backing = true
      if (along > -0.08f) backing = false
    }

    // Use the confirmed walking direction; looking around must not drag the dog.
    val lead = if (placed && backing && !shortRange) 1.3f else leadMeters
    val prediction = if (placed && !shortRange) 0.25f else 0f
    userMoving = hypot(velocityX, velocityZ) > 0.15f
    val userDistance = hypot(cameraX - x, cameraZ - z)
    // Rendered rope length includes its vertical span and slack. Horizontal distance
    // remains a fallback until a rope sample is available.
    if (placed && !shortRange && !followingMovement && (leashLength >= 2.6f || userDistance >= 2.6f)) returning = true
    if (returning && userDistance <= DOG_LEAD_METERS + 0.1f && leashLength < 2.6f) returning = false
    val distance = userDistance.coerceAtLeast(0.001f)
    // Leash recall uses the same forward position as initial placement.
    val targetX = if (recalling) cameraX + headingX * leadMeters
      else if (returning) cameraX + (x - cameraX) / distance * 1.5f
      else cameraX + headingX * lead + velocityX * prediction
    val targetZ = if (recalling) cameraZ + headingZ * leadMeters
      else if (returning) cameraZ + (z - cameraZ) / distance * 1.5f
      else cameraZ + headingZ * lead + velocityZ * prediction
    if (placed && placementRequested) {
      placementRequested = false
      val px = cameraX + headingX * lead
      val pz = cameraZ + headingZ * lead
      val ground = floorAt(px, pz)
      if (ground != null && canPlaceAt(cameraX, cameraZ, px, pz, ground)) {
        return placeAt(px, pz, ground)
      }
    }
    if (!placed) {
      if (requiresPlacement && !placementRequested) return frame(false, 0f)
      // A press applies only at this moment; don't spawn later on an unrelated floor.
      placementRequested = false
      val ground = floorAt(targetX, targetZ) ?: return frame(false, 0f)
      if (!canPlaceAt(cameraX, cameraZ, targetX, targetZ, ground)) return frame(false, 0f)
      yaw = atan2(headingX, headingZ)
      return placeAt(targetX, targetZ, ground)
    }
    if (!continuous) {
      speed = 0f
      moving = false
      blocked = false
      userMoving = false
      motionPaused = true
      stopReason = "프레임 지연"
      animations.pause()
      return frame(true, 0f)
    }

    val unrestrictedReturn = recalling || returning
    stopReason = ""

    var navigationTargetX = targetX
    var navigationTargetZ = targetZ
    if (followingMovement && catchingUp && !recalling && !shortRange) {
      val ahead = (x - cameraX) * headingX + (z - cameraZ) * headingZ
      val lateral = (x - cameraX) * headingZ - (z - cameraZ) * headingX
      val finalGap = hypot(targetX - x, targetZ - z).coerceAtLeast(0.0001f)
      val crossesUser = !avoidsWalker((targetX - x) / finalGap, (targetZ - z) / finalGap, finalGap, cameraX, cameraZ)
      if (passingSide == 0f && ahead < 0.5f && abs(lateral) < 0.85f && crossesUser) {
        passingSide = if (lateral >= 0f) 1f else -1f
      }
      if (passingSide != 0f && !passedUserSide) {
        if (abs(lateral) >= 0.85f) {
          passedUserSide = true
          floorPath.clear()
          floorRetrySeconds = 0f
        } else {
          // Commit to a clear passing side while turning, rather than switching
          // between straight and lateral routes as the user keeps walking.
          navigationTargetX = cameraX + headingZ * passingSide - headingX * 0.3f
          navigationTargetZ = cameraZ - headingX * passingSide - headingZ * 0.3f
        }
      }
      if (passedUserSide) {
        if (ahead >= 1f) passedUserFront = true
        if (!passedUserFront) {
          // Stay in the passing lane until ahead; merging toward the centre
          // too early repeatedly hits the moving user's clearance radius.
          navigationTargetX = targetX + headingZ * passingSide * 0.95f
          navigationTargetZ = targetZ - headingX * passingSide * 0.95f
        }
      }
    }
    val dx = navigationTargetX - x
    val dz = navigationTargetZ - z
    val gap = hypot(dx, dz)
    // A resting dog tolerates small tracking sway; an approaching dog settles closer.
    val arrivalGap = when {
      shortRange -> 0.02f
      recalling -> 0.08f
      moving -> 0.12f
      else -> 0.22f
    }
    var desiredYaw = when {
      gap > arrivalGap -> atan2(dx, dz)
      recalling -> atan2(cameraX - x, cameraZ - z)
      backing -> atan2(cameraX - x, cameraZ - z)
      else -> atan2(headingX, headingZ)
    }

    // Steering is cheap geometry and reacts to each new camera sample. Only
    // the actual next step needs AR floor queries, never a long route ahead.
    var routeFound = unrestrictedReturn || gap <= arrivalGap
    if (!unrestrictedReturn && gap > arrivalGap) {
      val directYaw = desiredYaw
      var bestScore = Float.POSITIVE_INFINITY
      for (offset in ROUTE_OFFSETS) {
        val angle = directYaw + offset
        val ux = sin(angle)
        val uz = cos(angle)
        val distance = min(gap, 0.45f)
        if (!avoidsWalker(ux, uz, distance, cameraX, cameraZ)) continue
        val score = hypot(navigationTargetX - x - ux * distance, navigationTargetZ - z - uz * distance) +
          abs(wrap(angle - yaw)) * 0.06f
        if (score < bestScore) {
          bestScore = score
          desiredYaw = angle
          routeFound = true
        }
        if (offset == 0f) break
      }
    }
    val lookAhead = min(gap, max(0.35f, min(0.8f, speed * 0.4f)))
    val wallRoute = if (!unrestrictedReturn && routeFound && gap > arrivalGap &&
      !trustedSegment(x, z, x + sin(desiredYaw) * lookAhead, z + cos(desiredYaw) * lookAhead)) wallAvoidance.steer(
      x, z, y, desiredYaw, lookAhead,
      atan2(headingX, headingZ), dt,
    ) else WallRoute(desiredYaw)
    val previousYaw = yaw
    var turnRemaining = 0f
    fun faceStep(stepX: Float, stepZ: Float): Boolean {
      val travelYaw = atan2(stepX, stepZ)
      val turn = wrap(travelYaw - yaw)
      yaw = wrap(yaw + turn.coerceIn(-4.5f * dt, 4.5f * dt))
      turnRemaining = wrap(travelYaw - yaw)
      if (abs(turnRemaining) <= 0.07f) {
        yaw = travelYaw
        return true
      }
      turningToMove = true
      return false
    }
    val maxSpeed = if (wallRoute.detouring || floorPath.isNotEmpty() || unrestrictedReturn) 1.1f else 3.6f
    val walkingSpeed = max(0f, velocityX * sin(desiredYaw) + velocityZ * cos(desiredYaw))
    val catchUpSpeed = if (catchingUp) max(gap * 1.8f, hypot(velocityX, velocityZ) + 1.5f)
      else if (unrestrictedReturn) gap * 1.8f
      else min(walkingSpeed + gap * 1.8f, max(1.1f, walkingSpeed + 0.35f))
    val wantedSpeed = if (routeFound && gap > arrivalGap) min(maxSpeed, catchUpSpeed) else 0f
    val acceleration = if (wantedSpeed > speed) 2.8f else 5f
    speed += (wantedSpeed - speed).coerceIn(-acceleration * dt, acceleration * dt)
    val step = min(speed * dt, max(0f, gap - if (recalling || shortRange) 0f else 0.12f))
    var ux = sin(wallRoute.yaw)
    var uz = cos(wallRoute.yaw)
    var actualSpeed = 0f
    if (step > 0f && unrestrictedReturn) {
      floorPath.clear()
      // Explicit return ignores walls, semantic floor and ledges. Preserve the last
      // known ground height instead of snapping to furniture or an unknown floor.
      if (faceStep(ux, uz)) {
        x += ux * step
        z += uz * step
        actualSpeed = step / dt
      }
    } else if (step > 0f && routeFound) {
      floorRetrySeconds = (floorRetrySeconds - dt).coerceAtLeast(0f)
      val directClear = !wallRoute.detouring && !wallRoute.blocked && avoidsWalker(ux, uz, step, cameraX, cameraZ) &&
        clearSegment(x, z, x + ux * step, z + uz * step, y)
      var ground = if (directClear) stepFloor(ux, uz, step, floorAt) else null
      if (ground != null) floorDirectClearSeconds += dt else floorDirectClearSeconds = 0f
      if (floorDirectClearSeconds >= 0.3f) floorPath.clear()
      var routeStep = step
      if (ground == null || floorPath.isNotEmpty()) {
        while (floorPath.isNotEmpty() && hypot(floorPath.first().x - x, floorPath.first().z - z) < 0.015f) floorPath.removeFirst()
        if (floorPath.isEmpty() && floorRetrySeconds <= 0f) {
          floorRetrySeconds = 0.3f
          floorPath.addAll(LocalFloorPlanner.plan(FloorWaypoint(x, z, y), navigationTargetX, navigationTargetZ,
            { px, pz -> trustedGround(px, pz) ?: (routeGround ?: floorAt)(px, pz) },
            { from, to ->
              val trusted = trustedSegment(from.x, from.z, to.x, to.z)
              (trusted || abs(to.y - from.y) <= 0.08f && abs(to.y - y) <= 0.18f) &&
                clearSegment(from.x, from.z, to.x, to.z, from.y) &&
                avoidsWalkerSegment(from.x, from.z, to.x, to.z, cameraX, cameraZ, walkerClearance)
            }))
        }
        floorPath.firstOrNull()?.let { waypoint ->
          val distanceToWaypoint = hypot(waypoint.x - x, waypoint.z - z).coerceAtLeast(0.0001f)
          val sx = (waypoint.x - x) / distanceToWaypoint
          val sz = (waypoint.z - z) / distanceToWaypoint
          val nextStep = min(step, distanceToWaypoint)
          val nextFloor = if (avoidsWalker(sx, sz, nextStep, cameraX, cameraZ) &&
            clearSegment(x, z, x + sx * nextStep, z + sz * nextStep, y)) stepFloor(sx, sz, nextStep, floorAt) else null
          if (nextFloor != null) { ux = sx; uz = sz; ground = nextFloor; routeStep = nextStep }
          else { floorPath.clear(); ground = null }
        }
        if (ground == null && floorPath.isEmpty() && (wallRoute.detouring || wallRoute.blocked)) stopReason = "벽·우회 없음"
      }
      if (ground != null) {
        val travel = if (floorUncertain) min(routeStep, 0.35f * dt) else routeStep
        if (faceStep(ux, uz)) {
          x += ux * travel
          z += uz * travel
          y += (ground - y) * min(1f, dt * 8f)
          actualSpeed = travel / dt
        }
        if (floorUncertain) speed = min(speed, 0.35f)
      } else if (wallRoute.detouring) {
        wallAvoidance.rejectRoute(wallRoute.yaw)
      }
    } else if (step > 0f && wallRoute.detouring) {
      wallAvoidance.rejectRoute(wallRoute.yaw)
    }
    blocked = !unrestrictedReturn && gap > arrivalGap && actualSpeed == 0f && !turningToMove
    if (blocked && stopReason.isEmpty()) stopReason = when {
      !routeFound -> "벽·진로 없음"
      !avoidsWalker(ux, uz, step, cameraX, cameraZ) -> "사용자 거리"
      !clearSegment(x, z, x + ux * step, z + uz * step, y) -> "벽"
      else -> floorFailureReason.ifEmpty { "바닥 미확인" }
    }
    if (!blocked) stopReason = ""
    if (actualSpeed == 0f) speed = 0f
    if (blocked) {
      // A rejected step must not rotate in place or keep its previous gait.
      yaw = previousYaw
      moving = false
      animations.pause()
      return frame(true, 0f)
    }
    val turnRate = wrap(yaw - previousYaw) / dt
    moving = actualSpeed > 0.07f
    // Every committed step is aligned with the model's +Z (head) direction.
    val forwardSpeed = actualSpeed
    val rightSpeed = 0f
    animations.update(
      deltaSeconds.coerceAtMost(0.25f), hypot(velocityX, velocityZ), actualSpeed,
      forwardSpeed, rightSpeed, if (turningToMove) turnRate else 0f, turnRemaining,
      headFirst = true,
    )
    return frame(true, actualSpeed)
  }

  private fun updatePoop(dt: Float): DogFrame {
    if (dt <= 0f) return frame(true, speed)
    if (poopStage == PoopStage.POOP) {
      speed = 0f
      moving = false
      animations.update(dt, 0f, 0f, 0f, 0f, 0f, 0f)
      // The controller uses the GLB clip duration, so return only after the pose ends.
      if (animations.current.clip != "Poop") poopStage = PoopStage.RETURN
      return frame(true, 0f)
    }
    val outbound = poopStage == PoopStage.WALK_RIGHT
    val targetX = if (outbound) poopTargetX else poopOriginX
    val targetZ = if (outbound) poopTargetZ else poopOriginZ
    val dx = targetX - x
    val dz = targetZ - z
    val distance = hypot(dx, dz)
    val step = min(distance, 0.4f * dt)
    if (distance > 0.0001f) {
      yaw = atan2(dx, dz)
      x += dx / distance * step
      z += dz / distance * step
    }
    speed = step / dt
    moving = speed > 0.07f
    animations.update(dt, 0f, speed, speed, 0f, 0f, 0f)
    if (distance <= step + 0.0001f) {
      x = targetX
      z = targetZ
      yaw = poopOriginYaw
      speed = 0f
      moving = false
      animations.pause()
      if (outbound) {
        poopStage = PoopStage.POOP
        poopDeposited = true
        animations.request("poop")
        animations.update(dt, 0f, 0f, 0f, 0f, 0f, 0f)
      } else {
        poopStage = null
        // Do not interpret the user's movement during the sequence as a velocity jump.
        hasCamera = false
        leashLength = 0f
      }
    }
    return frame(true, speed)
  }

  private fun updateCare(current: Care, dt: Float): DogFrame {
    current.seconds += dt
    speed = 0f
    moving = false
    blocked = false
    userMoving = hypot(velocityX, velocityZ) > 0.15f
    stopReason = ""
    if (current.consuming) {
      if (!animations.advanceConsumption(dt)) {
        care = null
        animations.pause()
        animations.holdPosition(true, dt)
      }
    } else if (current.seconds >= max(current.standSeconds, current.travelSeconds)) {
      current.consuming = true
      animations.beginConsumption(current.action)
    }
    return frame(true, speed)
  }

  private fun avoidsWalker(
    ux: Float, uz: Float, distance: Float, cameraX: Float, cameraZ: Float,
  ): Boolean {
    val dx = x - cameraX
    val dz = z - cameraZ
    val closest = (-(dx * ux + dz * uz)).coerceIn(0f, distance)
    val currentDistance = hypot(dx, dz)
    val nearestDistance = hypot(dx + ux * closest, dz + uz * closest)
    return nearestDistance >= min(walkerClearance, currentDistance) - 0.0001f
  }

  private fun stepFloor(
    ux: Float, uz: Float, distance: Float,
    floorAt: (Float, Float) -> Float?,
  ): Float? {
    var previousY = y
    val samples = ceil(distance / 0.08f).toInt().coerceAtLeast(1)
    for (i in 1..samples) {
      val px = x + ux * distance * i / samples
      val pz = z + uz * distance * i / samples
      val ground = trustedGround(px, pz) ?: floorAt(px, pz) ?: return null
      if (!ground.isFinite() || abs(ground - previousY) > 0.08f || abs(ground - y) > 0.18f) {
        stopReason = "높이 차"
        return null
      }
      previousY = ground
    }
    return previousY
  }

  private fun frame(visible: Boolean, actualSpeed: Float) = DogFrame(
    x, y, z, Math.toDegrees(yaw.toDouble()).toFloat(), visible && placed,
    animations.current.clip, actualSpeed, animations.current.loop, animations.current.revision, moving, blocked, userMoving, recalling || returning, floorUncertain, stopReason, poopDeposited, spinElapsed != null,
    animationPlaybackRate = animations.current.playbackRate,
    motionPaused = motionPaused,
    turningToMove = turningToMove,
  )

  private fun wrap(angle: Float): Float {
    var value = angle
    while (value > PI) value -= (2 * PI).toFloat()
    while (value < -PI) value += (2 * PI).toFloat()
    return value
  }

  private companion object {
    const val CARE_STAND_RATE = 0.8f
    private fun avoidsWalkerSegment(ax: Float, az: Float, bx: Float, bz: Float, cameraX: Float, cameraZ: Float, clearance: Float): Boolean {
      val dx = bx - ax
      val dz = bz - az
      val lengthSquared = dx * dx + dz * dz
      val t = if (lengthSquared < 0.000001f) 0f else (((cameraX - ax) * dx + (cameraZ - az) * dz) / lengthSquared).coerceIn(0f, 1f)
      return hypot(ax + t * dx - cameraX, az + t * dz - cameraZ) >= min(clearance, hypot(ax - cameraX, az - cameraZ)) - 0.0001f
    }
    val ROUTE_OFFSETS = floatArrayOf(0f, -0.9f, 0.9f, -1.7f, 1.7f)
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
  val clipLoop: Boolean = true,
  val animationRevision: Int = 0,
  val moving: Boolean = false,
  val blocked: Boolean = false,
  val userMoving: Boolean = false,
  val ignoresObstacles: Boolean = false,
  val cautious: Boolean = false,
  val stopReason: String = "",
  val poopDeposited: Boolean = false,
  val spinning: Boolean = false,
  val scriptedFacing: Boolean = false,
  val animationPlaybackRate: Float = 1f,
  val motionPaused: Boolean = false,
  val turningToMove: Boolean = false,
)
