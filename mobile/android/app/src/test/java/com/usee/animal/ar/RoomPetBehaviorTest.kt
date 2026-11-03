package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class RoomPetBehaviorTest {
  private val durations = mapOf("SitEnd" to 1f, "LieStart" to 1f, "LieEnd" to 1f, "ScratchEar" to 2f)

  @Test fun sitsThenRestsAfterTenVisibleSeconds() {
    val state = RoomPetBehavior(durations) { 20f }
    assertEquals("SitIdle", state.update(9f).clip)
    assertEquals("SitEnd", state.update(1f).clip)
    assertEquals("LieStart", state.update(1f).clip)
    assertEquals("LieIdle", state.update(1f).clip)
    assertTrue(state.animation.loop)
  }

  @Test fun scratchesOnceAndReturnsToRest() {
    val state = RoomPetBehavior(durations) { 20f }
    state.update(10f)
    state.update(1f)
    state.update(1f)
    assertEquals("LieEnd", state.update(8f).clip)
    assertEquals("ScratchEar", state.update(1f).clip)
    assertFalse(state.animation.loop)
    assertEquals("LieStart", state.update(2f).clip)
    assertEquals("LieIdle", state.update(1f).clip)
    assertTrue(state.animation.loop)
  }

  @Test fun walkReturnSitsBeforeStartingTheRestTimer() {
    val state = RoomPetBehavior(durations + ("SitStart" to 1.25f), returningFromWalk = true) { 20f }
    assertEquals("Walk", state.animation.clip)
    assertTrue(state.walking)
    assertEquals("Walk", state.update(2.9f).clip)
    assertEquals("SitStart", state.update(0.2f).clip)
    assertFalse(state.walking)
    assertFalse(state.resting)
    assertEquals("SitIdle", state.update(1.25f).clip)
    assertEquals("SitIdle", state.update(9f).clip)
    assertEquals("SitEnd", state.update(1f).clip)
  }
}
