package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class WalkCleanupStateTest {
  @Test fun waitsForThirtySecondsAndConfirmedObstacle() {
    val state = WalkCleanupState()
    assertFalse(state.update(29f, true))
    assertFalse(state.update(1f, false))
    assertTrue(state.update(1f, true))
    assertFalse(state.update(500f, true))
  }

  @Test fun triggersAtThirtySecondsWhenObstacleIsPresent() {
    val state = WalkCleanupState()
    assertFalse(state.update(29f, true))
    assertTrue(state.update(1f, true))
    assertFalse(state.update(1f, true))
  }

  @Test fun bagReturnsWhenWalkingBackAndStaysGoneAfterCollection() {
    val state = WalkCleanupState()
    state.update(30f, true)
    assertTrue(state.canCollect(1.99f))
    assertFalse(state.canCollect(2f))
    assertFalse(state.canCollect(3f))
    assertTrue(state.canCollect(1f))
    state.collect()
    assertFalse(state.canCollect(1f))
    assertFalse(state.update(500f, true))
  }
}
