package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DogLeaderTest {
  @Test fun everyNormalStepMovesHeadFirstIncludingRightLeftAndReverseCatchUp() {
    for (direction in listOf(1f to 0f, -1f to 0f, 0f to 1f)) {
      val leader = DogLeader()
      var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
      var cameraX = 0f; var cameraZ = 0f
      repeat(100) { cameraZ -= 0.02f; dog = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor) }
      var sawTurnBeforeMoving = false
      var travelled = 0f
      repeat(400) {
        cameraX += direction.first * 0.02f
        cameraZ += direction.second * 0.02f
        val next = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor)
        val dx = next.x - dog.x; val dz = next.z - dog.z
        val length = hypot(dx, dz)
        if (next.turningToMove) {
          sawTurnBeforeMoving = true
          assertEquals(0f, length, 0f)
          assertFalse(next.blocked)
        }
        if (length > 0.00001f) {
          val yaw = Math.toRadians(next.yawDegrees.toDouble())
          val headAlignment = (dx * sin(yaw) + dz * cos(yaw)) / length
          assertTrue("Movement must follow the head, alignment=$headAlignment: $dog -> $next", headAlignment > 0.999)
          assertFalse(next.clip.contains("Back"))
          assertFalse(next.clip.endsWith("Left") || next.clip.endsWith("Right"))
          travelled += length
        }
        dog = next
      }
      assertTrue(sawTurnBeforeMoving)
      assertTrue(travelled > 5f)
    }
  }

  @Test fun suddenWalkingTurnsCatchUpToTheNewFrontEvenWithoutTurningThePhone() {
    for (newDirection in listOf(1f to 0f, -1f to 0f, 0f to 1f)) {
      val leader = DogLeader()
      leader.update(0f, 0f, 0f, 0f, -1f, floor)
      var cameraX = 0f
      var cameraZ = 0f
      var dog = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor)
      repeat(150) { cameraZ -= 0.02f; dog = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor) }
      val before = dog
      var maximumSpeed = 0f
      repeat(400) {
        cameraX += newDirection.first * 0.02f
        cameraZ += newDirection.second * 0.02f
        val next = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor)
        assertTrue("Must walk continuously instead of teleporting", hypot(next.x - dog.x, next.z - dog.z) <= 3.61f * 0.02f)
        assertTrue("Must keep clear of the user", hypot(next.x - cameraX, next.z - cameraZ) >= 0.44f)
        assertFalse("Catch-up must retain terrain validation", next.ignoresObstacles)
        maximumSpeed = max(maximumSpeed, next.speedMetersPerSecond)
        dog = next
      }
      val ahead = (dog.x - cameraX) * newDirection.first + (dog.z - cameraZ) * newDirection.second
      val lateral = abs((dog.x - cameraX) * newDirection.second - (dog.z - cameraZ) * newDirection.first)
      assertTrue("Must catch up ahead of the new movement: $dog", ahead > 1.2f)
      assertTrue("Must settle onto the walking direction: $dog", lateral < 0.4f)
      assertTrue("A sudden turn should trigger running catch-up, max=$maximumSpeed from $before", maximumSpeed > 2.3f)
      repeat(200) { dog = leader.update(0.02f, cameraX, cameraZ, 0f, -1f, floor) }
      val stoppedAhead = (dog.x - cameraX) * newDirection.first + (dog.z - cameraZ) * newDirection.second
      assertEquals("Keep the new front after the user stops", DOG_LEAD_METERS, stoppedAhead, 0.2f)
    }
  }

  @Test fun directionChangeCannotOverrideSitOrStopAndCannotCrossUnknownGround() {
    for (hold in listOf("sit", "stop")) {
      val leader = DogLeader()
      val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
      leader.requestAction(hold)
      repeat(200) { i ->
        val held = leader.update(0.02f, i * 0.02f, 0f, 0f, -1f, floor)
        assertEquals(initial.x, held.x, 0f)
        assertEquals(initial.z, held.z, 0f)
      }
    }
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var dog = leader.update(0.02f, 0f, -0.02f, 0f, -1f, floor)
    repeat(50) { i -> dog = leader.update(0.02f, 0f, -(i + 1) * 0.02f, 0f, -1f, floor) }
    val start = dog
    repeat(200) { i ->
      dog = leader.update(0.02f, i * 0.02f, -1f, 0f, -1f) { _, _ -> null }
      assertFalse(dog.ignoresObstacles)
      assertEquals(start.x, dog.x, 0f)
      assertEquals(start.z, dog.z, 0f)
    }
    assertTrue(dog.blocked)
  }
  @Test fun sitImmediatelyStartsInTheVisibleHeadingAndKeepsItsPositionWhileTheCameraMoves() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var before = leader.update(0.02f, 0f, -0.5f, 1f, 0f, floor)
    repeat(10) { before = leader.update(0.02f, 0f, -0.5f, 1f, 0f, floor) }
    val visibleHeading = (before.yawDegrees - 30f + 540f) % 360f - 180f
    leader.requestAction("sit", visibleHeading)
    val immediate = leader.update(0f, 0f, -0.5f, 1f, 0f, floor)
    assertEquals("SitStart", immediate.clip)
    assertEquals(visibleHeading, immediate.yawDegrees, 0.001f)
    assertEquals(before.x, immediate.x, 0f)
    assertEquals(before.z, immediate.z, 0f)
    assertFalse(immediate.moving)
    var seated = immediate
    repeat(200) { i ->
      seated = leader.update(0.02f, i * 0.01f, -i * 0.02f, -1f, 0f, floor)
      assertEquals(visibleHeading, seated.yawDegrees, 0.001f)
      assertEquals(before.x, seated.x, 0f)
      assertEquals(before.z, seated.z, 0f)
    }
    assertEquals("SitIdle", seated.clip)
  }

  @Test fun sitInterruptsSpinAndDoesNotResumeTheOldTurnOrWalk() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAction("spin")
    repeat(30) { leader.update(0.02f, 0f, 0f, 0f, -1f, floor) }
    leader.requestAction("sit", -45f)
    val seated = leader.update(0.02f, 0f, -1f, 1f, 0f, floor)
    assertEquals("SitStart", seated.clip)
    assertFalse(seated.spinning)
    repeat(200) {
      val held = leader.update(0.02f, 0f, -1f, 1f, 0f, floor)
      assertEquals(-45f, held.yawDegrees, 0.001f)
      assertEquals(seated.x, held.x, 0f)
      assertEquals(seated.z, held.z, 0f)
      assertFalse(held.spinning)
    }
  }

  @Test fun slowValidCameraFramesAdvanceTheSameElapsedWalkWithoutHalfSpeed() {
    fun walk(interval: Float): DogFrame {
      val leader = DogLeader()
      leader.update(0f, 0f, 0f, 0f, -1f, floor)
      var dog = leader.update(interval, 0f, -0.5f * interval, 0f, -1f, floor)
      for (i in 2..(10f / interval).toInt()) {
        dog = leader.update(interval, 0f, -0.5f * i * interval, 0f, -1f, floor)
      }
      return dog
    }
    val regular = walk(0.02f)
    for (interval in listOf(0.1f, 0.2f, 0.25f)) {
      val slow = walk(interval)
      assertFalse(slow.motionPaused)
      assertEquals(regular.z, slow.z, 0.2f)
      assertTrue(slow.speedMetersPerSecond > 0.3f)
    }
  }
  @Test fun corruptedTrustedFloorCannotPullTheDogUnderground() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.setTrustedGround { _, _ -> -3f }
    repeat(150) {
      val dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
      assertEquals(initial.y, dog.y, 0.001f)
    }
  }

  @Test fun seatedDogReceivesWaterAndTreatInItsFacingDirection() {
    val leader = DogLeader()
    fun frame() = leader.update(0.05f, 0f, 0f, 0f, -1f, floor)
    val start = frame()
    val camera = LeashPoint(0f, 1f, 0f)
    assertFalse(leader.giveCare("drink", camera))
    leader.requestAction("sit", 90f)
    repeat(25) { frame() }
    assertTrue(leader.canGiveCare)
    assertTrue(leader.giveCare("drink", camera))
    assertFalse(leader.giveCare("eat", camera))
    assertEquals(camera, leader.careItem!!.point)
    repeat(5) { frame() }
    val bowlInFlight = leader.careItem!!.point
    repeat(8) { frame() }
    val bowl = leader.careItem!!.point
    assertEquals(0.4f, hypot(bowl.x - start.x, bowl.z - start.z), 0.001f)
    assertTrue(bowlInFlight.z in bowl.z..camera.z)
    assertEquals(start.x + 0.4f, bowl.x, 0.001f)
    assertEquals(start.z, bowl.z, 0.001f)
    val waterFrames = List(190) { frame() }
    assertTrue(waterFrames.all { it.x == start.x && it.z == start.z })
    assertEquals("SitEnd", waterFrames.first().clip)
    assertEquals(0.8f, waterFrames.first().animationPlaybackRate, 0.001f)
    assertTrue(waterFrames.all { abs(it.yawDegrees - 90f) < 0.001f && !it.clip.startsWith("Turn") })
    assertTrue(waterFrames.any { it.clip == "EatDrinkStart" })
    assertTrue(waterFrames.any { it.clip == "Drink" })
    assertTrue(leader.canGiveCare)

    assertTrue(leader.giveCare("eat", camera))
    val launch = leader.careItem!!.point
    repeat(6) { frame() }
    val inFlight = leader.careItem!!.point
    assertTrue(inFlight.z < launch.z)
    repeat(7) { frame() }
    val treat = leader.careItem!!.point
    assertEquals(start.x + 0.4f, treat.x, 0.001f)
    assertEquals(start.z, treat.z, 0.001f)
    val treatFrames = List(190) { frame() }
    assertTrue(treatFrames.all { it.x == start.x && it.z == start.z })
    assertTrue(treatFrames.all { abs(it.yawDegrees - 90f) < 0.001f && !it.clip.startsWith("Turn") })
    assertEquals(90f, treatFrames.first { it.clip == "EatDrinkStart" }.yawDegrees, 0.001f)
    assertTrue(treatFrames.any { it.clip == "Eat" || it.clip == "EatTear" })
    assertNull(leader.careItem)
    assertTrue(leader.canGiveCare)
  }

  @Test fun spinMakesExactlyOneFullTurnWithoutTranslatingThenAutomaticallyResumesWalking() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAction("spin")
    var previousYaw = initial.yawDegrees
    var turned = 0f
    var dog = initial
    for (i in 1..160) {
      dog = leader.update(0.02f, i * 0.01f, -i * 0.02f, 1f, 0f, floor)
      val delta = Math.toRadians((dog.yawDegrees - previousYaw).toDouble())
      turned += Math.toDegrees(atan2(sin(delta), cos(delta))).toFloat()
      previousYaw = dog.yawDegrees
      assertEquals(initial.x, dog.x, 0.0001f)
      assertEquals(initial.z, dog.z, 0.0001f)
      if (i == 75) assertEquals("TurnLeft", dog.clip)
      if (!dog.spinning) break
    }
    assertEquals(360f, turned, 0.02f)
    assertFalse(dog.spinning)
    assertTrue(dog.scriptedFacing)
    val completed = dog
    dog = leader.update(0.02f, 3f, -5f, 1f, 0f, floor)
    assertTrue(dog.turningToMove || hypot(dog.x - completed.x, dog.z - completed.z) > 0f)
    for (i in 1..100) dog = leader.update(0.02f, 3f, -5f, 1f, 0f, floor)
    assertTrue(hypot(dog.x - initial.x, dog.z - initial.z) > 0.1f)
  }

  @Test fun spinResumesAfterAnEarlierSitOrStopAndTrackingInterruption() {
    for (hold in listOf("sit", "stop")) {
      val leader = DogLeader()
      val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
      leader.requestAction(hold)
      leader.update(0.02f, 0f, 0f, 0f, -1f, floor)
      leader.requestAction("spin")
      repeat(160) { leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor) }
      val resumed = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
      assertTrue(resumed.z < initial.z)
      leader.requestAction("spin")
      leader.update(0.02f, 0f, -1f, 0f, -1f, floor)
      leader.pauseTracking()
      leader.update(0f, 0f, -1f, 0f, -1f, floor)
      val recovered = leader.update(0.02f, 0f, -1f, 0f, -1f, floor)
      assertFalse(recovered.spinning)
      assertTrue(recovered.speedMetersPerSecond > 0f)
    }
  }

  @Test fun stationaryDogDoesNotPlayOrdinaryTurnClipsWhenTheCameraHeadingChanges() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    // A small view change leaves the target inside the 18cm arrival radius.
    for (i in 1..100) {
      val dog = leader.update(0.02f, 0f, 0f, 0.1f, -0.995f, floor)
      assertEquals(initial.x, dog.x, 0f)
      assertEquals(initial.z, dog.z, 0f)
      assertEquals(0f, dog.speedMetersPerSecond, 0f)
      assertFalse(dog.moving)
      assertFalse(dog.clip.startsWith("Turn"))
    }
  }

  @Test fun repeatedSpinDoesNotRestartAndExplicitStopOrTrackingPauseCancelsIt() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAction("spin")
    for (i in 1..100) {
      leader.requestAction("spin")
      assertFalse(leader.requestAttention())
      leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
    }
    val beforeStop = leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
    val expectedYaw = ((initial.yawDegrees + 242.4f + 180f) % 360f + 360f) % 360f - 180f
    assertEquals(expectedYaw, beforeStop.yawDegrees, 0.02f)
    leader.requestAction("stop")
    val stopped = leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
    assertFalse(stopped.spinning)
    assertEquals(beforeStop.yawDegrees, stopped.yawDegrees, 0.001f)
    leader.requestAction("spin")
    leader.pauseTracking()
    val paused = leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
    assertFalse(paused.spinning)
    assertEquals(initial.z, paused.z, 0.0001f)
  }
  @Test fun attentionTimerUsesElapsedTimeEvenWithSlowFrames() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAttention()
    var dog = initial
    for (i in 1..16) dog = leader.update(0.2f, 0f, -3f, 0f, -1f, floor)
    assertTrue(dog.z < initial.z)
  }
  @Test fun nameCallStopsPositionAndBodyHeadingForThreeSecondsThenResumes() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    assertTrue(leader.requestAttention())
    var dog = initial
    for (i in 1..149) {
      leader.requestRecall()
      leader.requestPlacement()
      leader.beginPoop(1f, 0f)
      leader.updateLeashLength(5f)
      dog = leader.update(0.02f, i * 0.01f, -i * 0.02f, 1f, 0f, floor)
      assertEquals(initial.x, dog.x, 0.0001f)
      assertEquals(initial.z, dog.z, 0.0001f)
      assertEquals(initial.yawDegrees, dog.yawDegrees, 0.0001f)
      assertFalse(dog.moving)
    }
    for (i in 1..150) dog = leader.update(0.02f, 1.5f, -3f, 1f, 0f, floor)
    assertTrue(kotlin.math.hypot(dog.x - initial.x, dog.z - initial.z) > 0.1f)
  }

  @Test fun nameCallPreservesSitAndStopButExplicitGoOverridesAttention() {
    for (hold in listOf("sit", "stop")) {
      val leader = DogLeader()
      val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
      leader.requestAction(hold)
      assertTrue(leader.requestAttention())
      var dog = initial
      for (i in 1..250) dog = leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
      assertEquals(initial.z, dog.z, 0.0001f)
      assertEquals(if (hold == "sit") "SitIdle" else "Idle", dog.clip)
      assertTrue(leader.requestAttention())
      leader.requestAction("go")
      for (i in 1..50) dog = leader.update(0.02f, 0f, -3f, 0f, -1f, floor)
      assertTrue(dog.z < initial.z - 0.1f)
    }
  }

  @Test fun sitCommandHoldsUntilGoEvenWhenWalkerMovesAndLeashIsStretched() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var dog = leader.update(0.02f, 0f, -0.6f, 0f, -1f, floor)
    val x = dog.x
    val z = dog.z
    leader.requestAction("sit")
    dog = leader.update(0.02f, 0f, -0.8f, 0f, -1f, floor)
    assertEquals("SitStart", dog.clip)
    for (i in 1..800) {
      leader.requestAction("sit")
      leader.requestRecall()
      leader.requestPlacement()
      leader.beginPoop(1f, 0f)
      leader.updateLeashLength(5f)
      dog = leader.update(0.02f, 0f, -0.8f - i * 0.005f, 0f, -1f, floor)
      assertEquals(x, dog.x, 0.0001f)
      assertEquals(z, dog.z, 0.0001f)
      assertFalse(dog.moving)
    }
    assertEquals("SitIdle", dog.clip)
    leader.requestAction("go")
    for (i in 1..150) dog = leader.update(0.02f, 0f, -5f, 0f, -1f, floor)
    assertTrue("Must follow the walker only after go", dog.z < z - 0.1f)
  }

  @Test fun trackingPausePreservesSitAndCommandsDoNotInterruptPoop() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAction("sit")
    leader.pauseTracking()
    leader.update(0.02f, 0f, 0f, 0f, -1f, floor)
    val resumed = leader.update(0.02f, 0f, -1f, 0f, -1f, floor)
    assertTrue(resumed.clip.startsWith("Sit"))
    leader.requestAction("go")
    leader.beginPoop(1f, 0f)
    leader.requestAction("sit")
    val poop = leader.update(0.02f, 0f, -1f, 0f, -1f, floor)
    assertFalse(poop.clip.startsWith("Sit"))
  }

  @Test fun stopHoldsStandingPositionAndSitStopGoCanSwitch() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestAction("stop")
    var dog = initial
    for (i in 1..800) {
      dog = leader.update(0.02f, i * 0.01f, -i * 0.01f, 1f, 0f, floor)
      assertEquals(initial.x, dog.x, 0.0001f)
      assertEquals(initial.z, dog.z, 0.0001f)
      assertEquals("Idle", dog.clip)
      assertFalse(dog.moving)
    }
    leader.requestAction("sit")
    for (i in 1..100) dog = leader.update(0.02f, 0f, -2f, 0f, -1f, floor)
    assertEquals("SitIdle", dog.clip)
    leader.requestAction("stop")
    dog = leader.update(0.02f, 0f, -2f, 0f, -1f, floor)
    assertEquals("Idle", dog.clip)
    assertEquals(initial.z, dog.z, 0.0001f)
    leader.pauseTracking()
    for (i in 1..100) dog = leader.update(0.02f, 0f, -2f, 0f, -1f, floor)
    assertEquals(initial.z, dog.z, 0.0001f)
    leader.requestAction("go")
    for (i in 1..150) dog = leader.update(0.02f, 0f, -2f, 0f, -1f, floor)
    assertTrue(dog.z < initial.z - 0.1f)
  }

  @Test fun poopWalksRightStaysFixedAndReturnsBeforeFollowingUser() {
    val leader = DogLeader(animations = DogAnimationController(mapOf("Poop" to 2f)))
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.beginPoop(1f, 0f)
    var dog = initial
    // Move and turn the user far away; recall must not interrupt the fixed route.
    for (i in 1..25) {
      leader.requestRecall()
      dog = leader.update(0.02f, 3f, -3f, 1f, 0f, floor)
    }
    assertEquals(initial.x + 0.20f, dog.x, 0.001f)
    assertEquals(initial.z, dog.z, 0.001f)
    assertFalse(dog.poopDeposited)
    assertTrue(dog.moving)
    for (i in 1..30) dog = leader.update(0.02f, 3f, -3f, 1f, 0f, floor)
    assertEquals(initial.x + 0.40f, dog.x, 0.001f)
    assertEquals(initial.z, dog.z, 0.001f)
    assertEquals("Poop", dog.clip)
    assertTrue(dog.poopDeposited)
    assertFalse(dog.moving)
    for (i in 1..60) dog = leader.update(0.02f, -3f, 3f, -1f, 0f, floor)
    assertEquals("Poop", dog.clip)
    assertEquals(initial.x + 0.40f, dog.x, 0.001f)
    // Wait for the actual two-second clip, then the return to the saved origin.
    var returned = false
    for (i in 1..150) {
      dog = leader.update(0.02f, 3f, -3f, 1f, 0f, floor)
      if (abs(dog.x - initial.x) < 0.001f && abs(dog.z - initial.z) < 0.001f) {
        returned = true
        break
      }
      assertTrue(dog.x >= initial.x - 0.001f && dog.x <= initial.x + 0.401f)
      assertEquals(initial.z, dog.z, 0.001f)
    }
    assertTrue("Must return to the saved world position", returned)
    assertEquals(initial.yawDegrees, dog.yawDegrees, 0.001f)
    for (i in 1..100) dog = leader.update(0.02f, 3f, -3f, 1f, 0f, floor)
    assertTrue(hypot(dog.x - initial.x, dog.z - initial.z) > 0.1f)
  }

  @Test fun poopUsesCapturedScreenRightAndTrackingPauseDoesNotSkipSequence() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.beginPoop(0f, 2f)
    var dog = leader.update(0.05f, 0f, 0f, 0f, -1f, floor)
    val beforePause = dog.z
    leader.pauseTracking()
    dog = leader.update(0f, 2f, 2f, -1f, 0f, floor)
    assertEquals(beforePause, dog.z, 0.001f)
    for (i in 1..25) dog = leader.update(0.02f, 2f, 2f, -1f, 0f, floor)
    assertEquals(initial.x, dog.x, 0.001f)
    assertEquals(initial.z + 0.22f, dog.z, 0.001f)
    assertFalse(dog.poopDeposited)
  }

  @Test fun trustedUserGroundStillRespectsDetectedWalls() {
    val leader = DogLeader()
    val region = UserWalkRegion()
    region.update(0f, 0f, 0f)
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateWalls(listOf(WallSegment(-10f, -1.8f, 10f, -1.8f)))
    var dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
    for (i in 1..60) dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
    assertTrue(dog.blocked)
    for (i in 1..24) region.update(0f, -i * 0.1f, 0f)
    for (i in 24 downTo 5) region.update(0f, -i * 0.1f, 0f)
    leader.setTrustedGround(region::heightAt)
    for (i in 1..200) dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f) { _, _ -> null }
    assertTrue("Detected wall must block even inside the user region", dog.z > -1.8f)
    assertFalse(dog.ignoresObstacles)
    assertTrue(dog.blocked)
    leader.updateWalls(emptyList())
    for (i in 1..200) dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f) { _, _ -> null }
    assertTrue("Unknown floor is traversable after the wall disappears", dog.z < -1.8f)
    assertFalse(dog.blocked)
  }

  @Test fun uncertainKnownFloorSlowsMovementAndFreshFloorRecovers() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
    for (i in 1..40) {
      leader.beginNavigationFrame()
      dog = leader.update(0.02f, 0f, -0.5f, 0f, -1f) { _, _ ->
        leader.observeNavigation(true, "")
        0f
      }
      assertFalse(dog.blocked)
      assertTrue(dog.speedMetersPerSecond <= 0.351f)
    }
    assertTrue(dog.cautious)
    leader.beginNavigationFrame()
    val recovered = leader.update(0.02f, 0f, -0.5f, 0f, -1f, floor)
    assertFalse(recovered.cautious)
    assertTrue(recovered.speedMetersPerSecond > 0.35f)
  }

  @Test fun ropeOverLimitTriggersReturnEvenInsideHorizontalLimit() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateLeashLength(2.7f)
    var dog = initial
    for (i in 1..150) dog = leader.update(0.02f, 0f, 0.5f, 0f, -1f, floor)
    assertTrue("Long rope should pull the dog closer", hypot(dog.x, dog.z - 0.5f) < 1.7f)
  }

  @Test fun longLeashRecallUsesInitialPlacementDistanceAndForwardDirection() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestRecall()
    leader.updateLeashLength(2.8f)
    var dog = leader.update(0.02f, 0f, 2f, 0f, 1f, floor)
    for (i in 1..600) dog = leader.update(0.02f, 0f, 2f, 0f, 1f, floor)
    assertEquals(0f, dog.x, 0.1f)
    assertEquals(3.6f, dog.z, 0.1f)
  }

  @Test fun recallReturnsAtLeadDistanceAndFollowsChangedViewingDirection() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestRecall()
    var dog = leader.update(0.02f, 0f, 0f, 1f, 0f, floor)
    for (i in 1..400) dog = leader.update(0.02f, 0f, 0f, 1f, 0f, floor)
    assertEquals(1.6f, dog.x, 0.1f)
    assertEquals(0f, dog.z, 0.1f)
    val arrivedYaw = Math.toRadians(dog.yawDegrees.toDouble())
    assertTrue("Called dog should arrive head-first along its diagonal path", (sin(arrivedYaw) + cos(arrivedYaw)) / sqrt(2.0) > 0.99)
    for (i in 1..200) dog = leader.update(0.02f, 0f, 0f, 0f, 1f, floor)
    assertEquals(0f, dog.x, 0.1f)
    assertEquals(1.6f, dog.z, 0.1f)
  }

  @Test fun recallRestoresInitialSpacingEvenWhenDogWasTooClose() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestRecall()
    var dog = initial
    for (i in 1..200) dog = leader.update(0.02f, 0f, -0.6f, 0f, -1f, floor)
    assertEquals(-2.2f, dog.z, 0.1f)
  }

  @Test fun recallCrossesWallsAndUnknownFloorWithoutBlocking() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestRecall()
    leader.updateWalls(listOf(WallSegment(-10f, -0.75f, 10f, -0.75f)))
    var dog = initial
    for (i in 1..400) {
      dog = leader.update(0.02f, 0f, 1f, 0f, -1f) { _, _ -> null }
      assertFalse(dog.blocked)
    }
    assertEquals(-0.6f, dog.z, 0.1f)
    assertEquals(1.6f, hypot(dog.x, dog.z - 1f), 0.1f)
    assertEquals(initial.y, dog.y, 0f)
  }

  @Test fun automaticReturnIgnoresWallAndLedgeBeforeResumingNormalWalk() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateWalls(listOf(WallSegment(-10f, 0f, 10f, 0f)))
    assertTrue(leader.needsUnrestrictedReturn(0f, 2f))
    var dog = initial
    for (i in 1..500) dog = leader.update(0.02f, 0f, 2f, 0f, -1f) { _, _ -> 5f }
    assertTrue(hypot(dog.x, dog.z - 2f) < 1.7f)
    assertEquals(initial.y, dog.y, 0f)
    assertFalse(leader.needsUnrestrictedReturn(0f, 2f))
  }

  @Test fun recallRemainsRequestedAcrossTrackingPause() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.requestRecall()
    leader.pauseTracking()
    assertTrue(leader.needsUnrestrictedReturn(0f, 0f))
    leader.update(0f, 0f, 0f, 0f, -1f) { _, _ -> null }
    var dog = leader.update(0.02f, 0f, 0f, 0f, -1f) { _, _ -> null }
    for (i in 1..400) dog = leader.update(0.02f, 0f, 0f, 0f, -1f) { _, _ -> null }
    assertEquals(1.6f, hypot(dog.x, dog.z), 0.1f)
  }

  @Test fun waitingForSemanticRecognitionStopsAnimationAndMotion() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    for (i in 1..30) leader.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
    val paused = leader.pausedFrame()
    assertTrue(paused.visible)
    assertFalse(paused.moving)
    assertFalse(paused.blocked)
    assertEquals("Idle", paused.clip)
    assertEquals(0f, paused.speedMetersPerSecond, 0f)
  }

  @Test fun automaticPlacementRetriesUntilFloorIsKnown() {
    val leader = DogLeader()
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f) { _, _ -> null }.visible)
    assertTrue(leader.update(0.02f, 0f, 0f, 0f, -1f, floor).visible)
  }

  @Test fun distantDogReturnsTowardUserEvenWhenUserLooksAway() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var dog = initial
    for (i in 1..100) dog = leader.update(0.02f, 0f, 2f, 0f, -1f, floor)
    assertTrue(dog.z > initial.z + 0.2f)
  }

  @Test fun blockedFloorIsReportedButNormalRestIsNot() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    assertFalse(leader.update(0.02f, 0f, 0f, 0f, -1f, floor).blocked)
    val stopped = leader.update(0.02f, 0f, -0.5f, 0f, -1f) { _, _ -> null }
    assertTrue(stopped.blocked)
    assertEquals(0f, stopped.speedMetersPerSecond, 0f)
  }

  private val floor: (Float, Float) -> Float? = { _, _ -> 0f }

  @Test fun manualPlacementWaitsForPressAndUsesCurrentPosition() {
    val leader = DogLeader(requiresPlacement = true)
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f, floor).visible)
    assertFalse(leader.update(0.02f, 2f, 3f, 1f, 0f, floor).visible)
    leader.requestPlacement()
    val dog = leader.update(0.02f, 2f, 3f, 1f, 0f, floor)
    assertTrue(dog.visible)
    assertEquals(3.6f, dog.x, 0.001f)
    assertEquals(3f, dog.z, 0.001f)
    leader.requestPlacement()
    val next = leader.update(0.02f, 4f, 3f, 1f, 0f, floor)
    assertEquals(5.6f, next.x, 0.001f)
    assertEquals(3f, next.z, 0.001f)
  }

  @Test fun invalidPlacementDoesNotSpawnLaterWithoutAnotherPress() {
    val leader = DogLeader(requiresPlacement = true)
    leader.requestPlacement()
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f) { _, _ -> null }.visible)
    assertFalse(leader.update(0.02f, 0f, 0f, 0f, -1f, floor).visible)
    leader.requestPlacement()
    assertTrue(leader.update(0.02f, 0f, 0f, 0f, -1f, floor).visible)
  }

  @Test fun trackingLossCancelsPendingPlacement() {
    val leader = DogLeader(requiresPlacement = true)
    leader.requestPlacement()
    leader.cancelPlacement()
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f, floor).visible)
  }

  @Test fun lookingAroundWhileStationaryDoesNotMoveOrTurnTheDog() {
    val leader = DogLeader()
    val initial = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    for (i in 1..400) {
      val next = leader.update(0.02f, 0f, 0f, 0f, 1f, floor)
      assertEquals(initial.x, next.x, 0f)
      assertEquals(initial.z, next.z, 0f)
      assertEquals(initial.yawDegrees, next.yawDegrees, 0f)
      assertEquals(0f, next.speedMetersPerSecond, 0f)
    }
  }

  @Test fun steadyWalkIgnoresPhoneDirectionAndSettlesWithoutRestartingFromSmallSway() {
    val leader = DogLeader()
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var stoppedFrames = 0
    for (i in 1..400) {
      dog = leader.update(0.02f, 0f, -i * 0.02f, sin(i * 0.03f), cos(i * 0.03f), floor)
      if (i > 100 && dog.speedMetersPerSecond < 0.1f) stoppedFrames++
      assertEquals(0f, dog.x, 0.01f)
    }
    assertEquals("Steady walking should not repeatedly stop", 0, stoppedFrames)
    repeat(200) { dog = leader.update(0.02f, 0f, -8f, 1f, 0f, floor) }
    val resting = dog
    repeat(300) { i ->
      dog = leader.update(0.02f, 0.015f * sin(i * 0.1f), -8f, 1f, 0f, floor)
      assertEquals(resting.x, dog.x, 0f)
      assertEquals(resting.z, dog.z, 0f)
      assertEquals(0f, dog.speedMetersPerSecond, 0f)
    }
  }

  @Test fun backingUpMakesDogFaceWalkerAndFollow() {
    val leader = DogLeader()
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    for (i in 1..300) dog = leader.update(0.02f, 0f, i * 0.015f, 0f, -1f, floor)
    assertTrue(dog.z > 2.5f)
    assertTrue("Dog should face camera (+Z): $dog", cos(Math.toRadians(dog.yawDegrees.toDouble())) > 0.8)
  }

  @Test fun trackingPauseNeverRepositionsDog() {
    val leader = DogLeader()
    val before = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    val after = leader.update(2f, 0f, 5f, 0f, 1f, floor)
    assertEquals(before.x, after.x, 0f)
    assertEquals(before.z, after.z, 0f)
  }

  @Test fun delayedFramesStopGaitAndMotionUntilContinuousTrackingResumes() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var before = leader.update(0.02f, 0f, -0.02f, 0f, -1f, floor)
    for (i in 2..30) before = leader.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
    assertTrue(DogAnimationController.isLocomotion(before.clip))
    assertTrue(before.moving)
    repeat(3) {
      val paused = leader.update(0.3f, 0f, -0.6f, 0f, -1f, floor)
      assertEquals(before.x, paused.x, 0f)
      assertEquals(before.z, paused.z, 0f)
      assertEquals(before.yawDegrees, paused.yawDegrees, 0f)
      assertEquals(0f, paused.speedMetersPerSecond, 0f)
      assertEquals("Idle", paused.clip)
      assertFalse(paused.moving)
      assertFalse(paused.userMoving)
      assertFalse(paused.blocked)
      assertTrue(paused.motionPaused)
      assertEquals("프레임 지연", paused.stopReason)
      val duplicate = leader.update(0f, 0f, -0.6f, 0f, -1f, floor)
      assertEquals(paused, duplicate)
    }
    var resumed = leader.update(0.02f, 0f, -0.62f, 0f, -1f, floor)
    for (i in 32..60) resumed = leader.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
    assertFalse(resumed.motionPaused)
    assertTrue(resumed.moving)
    assertTrue(resumed.z < before.z)
    assertTrue(DogAnimationController.isLocomotion(resumed.clip))
  }

  @Test fun rejectedStepStopsPreviousGaitAndDoesNotRotateTowardAnUnreachableTarget() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f, floor)
    var before = leader.update(0.02f, 0f, -0.02f, 0f, -1f, floor)
    for (i in 2..30) before = leader.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
    assertTrue(before.moving)
    repeat(200) {
      val blocked = leader.update(0.02f, 0f, -0.6f, 0.5f, -0.8660254f) { _, _ -> null }
      assertTrue(blocked.blocked)
      assertFalse(blocked.motionPaused)
      assertFalse(blocked.moving)
      assertEquals("Idle", blocked.clip)
      assertEquals(0f, blocked.speedMetersPerSecond, 0f)
      assertEquals(before.x, blocked.x, 0f)
      assertEquals(before.z, blocked.z, 0f)
      assertEquals(before.yawDegrees, blocked.yawDegrees, 0f)
    }
    var recovered = leader.update(0.02f, 0f, -0.6f, 0.5f, -0.8660254f, floor)
    repeat(100) { recovered = leader.update(0.02f, 0f, -0.6f, 0.5f, -0.8660254f, floor) }
    assertFalse(recovered.blocked)
    assertTrue(hypot(recovered.x - before.x, recovered.z - before.z) > 0.1f)
  }

  @Test fun steadyWalkingUsesBoundedFloorQueriesAndStableAnimation() {
    val leader = DogLeader()
    var queries = 0
    val countedFloor: (Float, Float) -> Float? = { _, _ -> queries++; 0f }
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, countedFloor)
    var clipChanges = 0
    for (i in 1..600) {
      val next = leader.update(0.02f, 0f, -i * 0.015f, 0f, -1f, countedFloor)
      if (next.clip != dog.clip) clipChanges++
      dog = next
    }
    assertTrue("Too many floor queries: $queries", queries < 800)
    assertTrue("Animation keeps restarting: $clipChanges", clipChanges <= 2)
    assertTrue(dog.z < -8f)
  }

  @Test fun repeatedCameraFramesDoNotResetWalkingSpeed() {
    val regular = DogLeader()
    val repeated = DogLeader()
    regular.update(0f, 0f, 0f, 0f, -1f, floor)
    repeated.update(0f, 0f, 0f, 0f, -1f, floor)
    var expected = regular.update(0.02f, 0f, -0.02f, 0f, -1f, floor)
    var actual = repeated.update(0.02f, 0f, -0.02f, 0f, -1f, floor)
    for (i in 2..200) {
      repeated.update(0f, 0f, -(i - 1) * 0.02f, 0f, -1f, floor)
      expected = regular.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
      actual = repeated.update(0.02f, 0f, -i * 0.02f, 0f, -1f, floor)
    }
    assertEquals(expected.z, actual.z, 0.02f)
    assertEquals(expected.clip, actual.clip)
  }

  @Test fun shortKnownFloorAllowsStepsWithoutLongLookahead() {
    val leader = DogLeader()
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    for (i in 1..100) {
      val currentZ = dog.z
      // The visible floor grows as the walker scans it. The next small step is
      // known, even though a full 24 cm route is not yet mapped.
      dog = leader.update(0.02f, 0f, -i * 0.02f, 0f, -1f) { _, z ->
        if (z >= currentZ - 0.12f) 0f else null
      }
    }
    assertTrue("Known short steps should advance: $dog", dog.z < -2.4f)
  }

  @Test fun unknownFloorAndLedgesStopMovement() {
    for (ledge in listOf(false, true)) {
      val leader = DogLeader()
      var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
      val bounded: (Float, Float) -> Float? = { _, z ->
        if (z >= -1.6f) 0f else if (ledge) -1f else null
      }
      for (i in 1..300) dog = leader.update(0.02f, 0f, -i * 0.01f, 0f, -1f, bounded)
      assertTrue(dog.z >= -1.6f)
      assertEquals(0f, dog.y, 0f)
    }
  }

  @Test fun finiteWallIsBypassedHeadFirstWithoutSideStepsOrCrossing() {
    val leader = DogLeader()
    val wall = WallSegment(-0.5f, -2.4f, 0.5f, -2.4f)
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateWalls(listOf(wall))
    for (i in 1..500) {
      val cameraZ = -min(i * 0.01f, 1.5f)
      val next = leader.update(0.02f, 0f, cameraZ, 0f, -1f, floor)
      assertFalse("Crossed wall: $dog -> $next", wall.blocks(dog.x, dog.z, next.x, next.z, dog.y, DOG_BODY_RADIUS, DOG_MODEL_UNITS))
      val dx = next.x - dog.x; val dz = next.z - dog.z
      val distance = hypot(dx, dz)
      if (distance > 0.00001f) {
        val yaw = Math.toRadians(next.yawDegrees.toDouble())
        assertTrue((dx * sin(yaw) + dz * cos(yaw)) / distance > 0.999)
        assertFalse(next.clip.contains("Back") || next.clip.endsWith("Left") || next.clip.endsWith("Right"))
      }
      dog = next
    }
    assertTrue("Dog should get around wall: $dog", dog.z < -2.1f)
  }

  @Test fun placementCannotPutDogBehindRecognizedWall() {
    val leader = DogLeader(requiresPlacement = true)
    leader.updateWalls(listOf(WallSegment(-1f, -0.8f, 1f, -0.8f)))
    leader.requestPlacement()
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f, floor).visible)
  }

  @Test fun wallDetourNeverLeavesKnownFloor() {
    val leader = DogLeader()
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateWalls(listOf(WallSegment(-2f, -1.8f, 2f, -1.8f)))
    for (i in 1..200) {
      dog = leader.update(0.02f, 0f, -min(i * 0.01f, 1f), 0f, -1f) { x, _ ->
        if (abs(x) <= 0.2f) 0f else null
      }
      assertTrue(abs(dog.x) <= 0.2f)
      assertTrue(dog.z >= -1.8f)
    }
  }
}
