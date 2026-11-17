package com.usee.animal.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkLowLightTest {
  @Test fun detectsConsecutiveDarkSamplesAndStaysOnThroughItsOwnIllumination() {
    val light = WalkLowLight()
    light.observeLight(40, false)
    light.observeLight(40, false)
    light.observeLight(80, false)
    light.observeLight(40, false)
    light.observeLight(40, false)
    assertFalse(light.detected)

    light.observeLight(40, false)
    light.observeLight(120, false)
    assertTrue(light.detected)
    assertFalse(WalkLowLight().detected)
  }

  @Test fun onlyRepeatedRecentObstacleObservationsInLowLightEnableTheNearbyFloor() {
    val light = WalkLowLight()
    light.observeObstacle(100_000_000L)
    light.observeObstacle(300_000_000L)
    light.observeObstacle(500_000_000L)
    assertFalse(light.shortRange)

    light.observeLight(null, true)
    light.observeObstacle(600_000_000L)
    light.observeObstacle(3_000_000_000L)
    light.observeObstacle(3_000_000_000L)
    light.observeObstacle(3_200_000_000L)
    assertNull(light.nearbyFloor(0f, 0f, 0f, 0f, -1f))

    light.observeObstacle(3_400_000_000L)
    assertEquals(-1f, light.nearbyFloor(1.4f, 1f, 1f, 1f, -1f))
    assertNull(light.nearbyFloor(1.41f, 1f, 1f, 1f, -1f))
  }

  @Test fun dogRepositionsFromTheNormalLeadAndFollowsTheWalkerAtFortyCentimeters() {
    val leader = DogLeader()
    val original = leader.update(0f, 0f, 0f, 0f, -1f) { _, _ -> 0f }
    assertEquals(-1.6f, original.z, 0.001f)
    val light = WalkLowLight().apply {
      observeLight(null, true)
      repeat(3) { observeObstacle(it * 200_000_000L) }
    }
    leader.setShortRange(light.shortRange)

    var walkerZ = 0f
    fun update() = leader.update(0.02f, 0f, walkerZ, 0f, -1f) { x, z ->
      light.nearbyFloor(x, z, 0f, walkerZ, 0f)
    }
    val close = update()
    assertEquals(-0.4f, close.z, 0.001f)
    repeat(100) {
      walkerZ -= 0.01f
      assertFalse(update().blocked)
    }
    assertEquals(0.4f, walkerZ - update().z, 0.04f)
  }
}
