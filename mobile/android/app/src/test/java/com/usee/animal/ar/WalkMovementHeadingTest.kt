package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class WalkMovementHeadingTest {
  @Test fun aSustainedWalkingTurnIsConfirmedButShortNoiseAndRelocalizationAreNot() {
    val heading = WalkMovementHeading()
    repeat(5) { assertFalse(heading.observe(1f, 0f, 0.02f, 0f, -1f)) }
    assertTrue(heading.observe(1f, 0f, 0.02f, 0f, -1f))
    assertEquals(1f, heading.x, 0f)
    assertEquals(0f, heading.z, 0f)
    repeat(20) { i ->
      assertFalse(heading.observe(if (i % 2 == 0) -1f else 1f, 0f, 0.02f, 0f, -1f))
    }
    assertFalse(heading.observe(10f, 0f, 0.02f, 0f, -1f))
    assertFalse(heading.observe(0.1f, 0f, 0.02f, 0f, -1f))
    heading.clear()
    repeat(6) { assertFalse(heading.observe(0f, -1f, 0.02f, 0f, -1f)) }
  }
}
