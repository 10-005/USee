package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class WalkabilityMemoryTest {
  private val start = 1_000_000_000L

  @Test fun cachedFloorSurvivesMissingObservationsWithoutRefreshingItsExpiry() {
    val memory = WalkabilityMemory()
    assertEquals(0f, memory.resolve(0f, 0f, 0f, FloorEvidence.CLEAR, start, start, false).height)
    val cached = memory.resolve(0f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 2_000_000_000L, true)
    assertEquals(0f, cached.height)
    assertTrue(cached.cautious)
    assertNull(memory.resolve(0f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 3_100_000_000L, true).height)
  }

  @Test fun oneFrozenObstacleSampleCannotBecomeConfirmedFromRenderCallbacks() {
    val memory = WalkabilityMemory()
    memory.resolve(0f, 0f, 0f, FloorEvidence.CLEAR, start, start, false)
    val sample = start + 100_000_000L
    repeat(60) {
      assertNotNull(memory.resolve(0f, 0f, 0f, FloorEvidence.OBSTACLE, sample, sample + it * 10_000_000L, true).height)
    }
    assertNull(memory.resolve(0f, 0f, 0f, FloorEvidence.OBSTACLE, sample + 350_000_000L, sample + 350_000_000L, true).height)
    assertEquals(0f, memory.resolve(0f, 0f, 0f, FloorEvidence.CLEAR, sample + 500_000_000L, sample + 500_000_000L, true).height)
  }

  @Test fun unknownNewFloorHasBoundedTimeAndDistanceGraceAndCannotSpawnDog() {
    val memory = WalkabilityMemory()
    assertNull(memory.resolve(0f, 0f, 0f, FloorEvidence.UNKNOWN, -1L, start, false).height)
    memory.resolve(0.14f, 0f, 0f, FloorEvidence.CLEAR, start, start, false)
    assertNotNull(memory.resolve(0.16f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 100_000_000L, true).height)
    assertNull(memory.resolve(0.4f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 150_000_000L, true).height)
    assertNull(memory.resolve(0.16f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 500_000_000L, true).height)
  }

  @Test fun trackingResetAndHeightChangeCannotReuseOldGround() {
    val memory = WalkabilityMemory()
    memory.resolve(0f, 0f, 0f, FloorEvidence.CLEAR, start, start, false)
    assertNull(memory.resolve(0f, 0f, 1f, FloorEvidence.UNKNOWN, -1L, start + 100_000_000L, true).height)
    memory.clear()
    assertNull(memory.resolve(0f, 0f, null, FloorEvidence.UNKNOWN, -1L, start + 200_000_000L, true).height)
  }

  @Test fun stopStatsCountEpisodesInsteadOfEveryFrame() {
    val stats = WalkStopStats()
    stats.record("벽", start)
    stats.record("벽", start + 100_000_000L)
    stats.record("", start + 500_000_000L)
    stats.record("벽", start + 600_000_000L)
    stats.record("", start + 900_000_000L)
    assertTrue(stats.summary().contains("2회"))
    assertTrue(stats.summary().contains("0.8초"))
  }
}
