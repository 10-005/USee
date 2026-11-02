package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DogLeaderTest {
  private val floor: (Float, Float) -> Float? = { _, _ -> 0f }

  @Test fun manualPlacementWaitsForPressAndUsesCurrentPosition() {
    val leader = DogLeader(requiresPlacement = true)
    assertFalse(leader.update(0f, 0f, 0f, 0f, -1f, floor).visible)
    assertFalse(leader.update(0.02f, 2f, 3f, 1f, 0f, floor).visible)
    leader.requestPlacement()
    val dog = leader.update(0.02f, 2f, 3f, 1f, 0f, floor)
    assertTrue(dog.visible)
    assertEquals(3f, dog.x, 0.001f)
    assertEquals(3f, dog.z, 0.001f)
    leader.requestPlacement()
    val next = leader.update(0.02f, 4f, 3f, 1f, 0f, floor)
    assertTrue(hypot(next.x - dog.x, next.z - dog.z) < 0.08f)
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

  @Test fun turnAroundTravelsContinuouslyAndPassesWalker() {
    val leader = DogLeader()
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    for (i in 1..400) {
      val next = leader.update(0.02f, 0f, 0f, 0f, 1f, floor)
      assertTrue(hypot(next.x - dog.x, next.z - dog.z) <= 3.6f * 0.02f + 0.001f)
      assertTrue(hypot(next.x, next.z) >= 0.44f)
      dog = next
    }
    assertTrue("Dog should reach the new forward side: $dog", dog.z > 0.7f)
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

  @Test fun finiteWallIsBypassedWithSideStepsWithoutCrossing() {
    val leader = DogLeader()
    val wall = WallSegment(-0.5f, -1.8f, 0.5f, -1.8f)
    var dog = leader.update(0f, 0f, 0f, 0f, -1f, floor)
    leader.updateWalls(listOf(wall))
    var sawSideStep = false
    for (i in 1..500) {
      val cameraZ = -min(i * 0.01f, 1.5f)
      val next = leader.update(0.02f, 0f, cameraZ, 0f, -1f, floor)
      assertFalse("Crossed wall: $dog -> $next", wall.blocks(dog.x, dog.z, next.x, next.z, dog.y))
      if (next.clip == "WalkLeft" || next.clip == "WalkRight") sawSideStep = true
      dog = next
    }
    assertTrue("Dog should get around wall: $dog", dog.z < -2.1f)
    assertTrue("Wall bypass should use side-step animation", sawSideStep)
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
