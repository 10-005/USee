package com.usee.animal.ar

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class DogHeadingSmootherTest {
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
