package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class WalkCleanupStateTest {
  @Test fun originalPoopStaysHiddenBetweenCollectionAnimationAndInventoryConfirmation() {
    val state = WalkCleanupState()
    state.update(30f, true)
    assertTrue(state.shouldShowPoop(false, 0L, false))
    assertTrue(state.shouldShowPoop(true, 349L, false))
    assertFalse(state.shouldShowPoop(true, 350L, false))
    // The animation has finished and collecting is false, but saving is still pending.
    assertFalse(state.shouldShowPoop(false, 850L, true))
    assertFalse(state.shouldShowPoop(false, 5000L, true))
    state.collect()
    assertFalse(state.shouldShowPoop(false, 5000L, false))
  }

  @Test fun failedInventorySaveCanRestorePoopForAnotherAttempt() {
    val state = WalkCleanupState()
    state.update(30f, true)
    assertFalse(state.shouldShowPoop(false, 850L, true))
    // A rejected confirmation clears the request without marking the event collected.
    assertTrue(state.shouldShowPoop(false, 850L, false))
    assertTrue(state.canCollect(1f))
  }
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
