package com.usee.animal.ar

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class DogHeadingSmootherTest {
  @Test fun visibleHeadingTracksMovementExactlyAndAllowsOnlyIntentionalPreMovementTurns() {
    val smoother = DogHeadingSmoother()
    smoother.update(0f, 0f)
    val preparing = DogFrame(0f, 0f, 0f, 45f, true, "TurnLeft", 0f, turningToMove = true)
    assertEquals(45f, smoother.update(preparing, 0.02f), 0f)
    assertEquals("TurnLeft", renderTurn(DogAnimation("TurnLeft"), 0f, 45f, scripted = true).clip)
    val walking = preparing.copy(yawDegrees = 90f, clip = "Walk", speedMetersPerSecond = 0.5f, turningToMove = false)
    assertEquals(90f, smoother.update(walking, 0.02f), 0f)
    val standing = walking.copy(yawDegrees = 180f, clip = "Idle", speedMetersPerSecond = 0f)
    assertEquals(90f, smoother.update(standing, 0.02f), 0f)
  }
  @Test fun stationaryDogKeepsItsVisibleHeadingAndNeverPlaysTurnClips() {
    val smoother = DogHeadingSmoother()
    val dog = DogFrame(0f, 0f, 0f, 90f, true, "TurnLeft", 0f)
    val heading = smoother.update(0f, 0f)
    repeat(200) {
      val next = smoother.update(dog, 0.02f)
      assertEquals(heading, next, 0f)
      assertEquals("Idle", renderTurn(DogAnimation(dog.clip), heading, next).clip)
    }
    repeat(200) { smoother.update(dog.copy(speedMetersPerSecond = 0.5f), 0.02f) }
    assertEquals(90f, smoother.update(dog.copy(speedMetersPerSecond = 0.5f), 0.02f), 0.1f)
    assertEquals("Idle", renderTurn(DogAnimation(), heading, heading).clip)
  }

  @Test fun visualTurnsRespectWraparoundAndPreserveCarePoses() {
    assertEquals("Walk", renderTurn(DogAnimation("TurnLeft180"), 179f, -179f, speed = 0.5f).clip)
    assertEquals("Walk", renderTurn(DogAnimation("TurnRight"), -179f, 179f, speed = 0.5f).clip)
    for (clip in listOf("SitIdle", "SitEnd", "EatDrinkStart", "Drink", "Eat", "Walk")) {
      val pose = DogAnimation(clip)
      assertEquals(pose, renderTurn(pose, 0f, 2f, speed = 0.5f))
    }
  }

  @Test fun walkingTurnRequiresMeasuredTranslationAndStopsImmediatelyWhenBlockedOrPaused() {
    assertEquals("Walk", renderTurn(DogAnimation(), 0f, 2f, speed = 0.5f).clip)
    assertEquals("Walk", renderTurn(DogAnimation(), 0f, -2f, speed = 0.5f).clip)
    assertEquals("Idle", renderTurn(DogAnimation(), 0f, 2f, speed = 0.07f).clip)
    for (clip in listOf("Idle", "Walk", "Trot", "TurnLeft")) {
      assertEquals("Idle", renderTurn(DogAnimation(clip), 0f, 2f, speed = 0.5f, blocked = true).clip)
      assertEquals("Idle", renderTurn(DogAnimation(clip), 0f, 2f, speed = 0.5f, paused = true).clip)
    }
  }

  @Test fun stationaryMotionClipsAreSuppressedButExplicitSpinAndCareTurnsRemainVisible() {
    for (clip in listOf("Walk", "Trot", "TurnLeft", "TurnRight180")) {
      assertEquals("Idle", renderTurn(DogAnimation(clip), 0f, 2f).clip)
    }
    for (clip in listOf("TurnLeft", "TurnLeft180", "TurnRight")) {
      val animation = DogAnimation(clip)
      assertEquals(animation, renderTurn(animation, 0f, 2f, scripted = true))
    }
    val smoother = DogHeadingSmoother()
    smoother.update(0f, 0f)
    val spin = DogFrame(0f, 0f, 0f, 90f, true, "TurnLeft", 0f, spinning = true)
    assertEquals(90f, smoother.update(spin, 0.02f), 0f)
    val completed = spin.copy(yawDegrees = 0f, clip = "Idle", spinning = false, scriptedFacing = true)
    assertEquals(0f, smoother.update(completed, 0.02f), 0f)
    val care = spin.copy(yawDegrees = 180f, spinning = false, scriptedFacing = true)
    assertEquals(-180f, smoother.update(care, 0.02f), 0f)
  }

  @Test fun pauseFreezesVisualHeadingAndDiscardsPendingRotationUntilFreshIntent() {
    val smoother = DogHeadingSmoother()
    smoother.update(0f, 0f)
    var heading = 0f
    repeat(20) { heading = smoother.update(90f, 0.02f) }
    assertTrue(heading > 0f && heading < 90f)
    repeat(30) { assertEquals(heading, smoother.update(90f, 0.02f, paused = true), 0f) }
    repeat(8) { assertEquals(heading, smoother.update(90f, 0.02f), 0f) }
    repeat(200) { smoother.update(90f, 0.02f) }
    assertEquals(90f, smoother.update(90f, 0.02f), 0.1f)
  }

  private fun renderTurn(animation: DogAnimation, previous: Float, heading: Float,
    speed: Float = 0f, blocked: Boolean = false, paused: Boolean = false, scripted: Boolean = false) =
    renderTurnAnimation(animation, previous, heading, 0.02f, speed, blocked, paused, scripted)

  @Test fun scriptedSpinBypassesDebounceAndSeedsTheNextWalkingHeading() {
    val smoother = DogHeadingSmoother()
    smoother.update(0f, 0f)
    for (angle in 0..360 step 5) {
      val expected = ((angle + 180f) % 360f) - 180f
      assertEquals(expected, smoother.snap(angle.toFloat()), 0.001f)
    }
    assertEquals(0f, smoother.update(0f, 0.02f), 0.001f)
  }
  private fun delta(a: Float, b: Float) = ((a - b + 540f) % 360f) - 180f

  @Test fun smallNoiseAndShortAlternatingDirectionsKeepPreviousFacing() {
    val smoother = DogHeadingSmoother()
    smoother.update(0f, 0f)
    repeat(100) { i ->
      assertEquals(0f, smoother.update(if (i % 2 == 0) 6f else -6f, 0.02f), 0f)
    }
    repeat(100) { i ->
      assertEquals(0f, smoother.update(if (i % 2 == 0) 60f else -60f, 0.02f), 0f)
    }
  }

  @Test fun sustainedTurnWaitsThenAcceleratesSmoothlyAndReachesTarget() {
    val smoother = DogHeadingSmoother()
    var previous = smoother.update(0f, 0f)
    var previousSpeed = 0f
    repeat(200) { i ->
      val next = smoother.update(90f, 0.02f)
      val speed = delta(next, previous) / 0.02f
      if (i < 8) assertEquals(0f, next, 0f)
      assertTrue("Turn exceeded speed limit", abs(speed) <= 120.01f)
      assertTrue("Turn acceleration changed abruptly", abs(speed - previousSpeed) <= 9.7f)
      assertTrue(next >= previous - 0.001f)
      previous = next
      previousSpeed = speed
    }
    assertEquals(90f, previous, 0.1f)
  }

  @Test fun crossingAngleBoundaryUsesShortTurnAndRejectsOppositeNoise() {
    val smoother = DogHeadingSmoother()
    var previous = smoother.update(170f, 0f)
    var travel = 0f
    repeat(120) {
      val next = smoother.update(-170f, 0.02f)
      travel += delta(next, previous)
      previous = next
    }
    assertEquals(20f, travel, 0.1f)
    repeat(60) { i ->
      val next = smoother.update(if (i % 2 == 0) 170f else -150f, 0.02f)
      assertTrue(abs(delta(next, -170f)) < 0.1f)
    }
  }

  @Test fun resetInitializesNewSessionWithoutOldHeading() {
    val smoother = DogHeadingSmoother()
    smoother.update(90f, 0f)
    smoother.reset()
    assertEquals(-45f, smoother.update(-45f, 0f), 0f)
  }
}
