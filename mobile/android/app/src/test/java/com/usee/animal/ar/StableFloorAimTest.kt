package com.usee.animal.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StableFloorAimTest {
  @Test fun stableAimUsesTheCurrentCenterAfterFiveSeconds() {
    val aim = StableFloorAim()
    val target = FloorWaypoint(0f, -1.6f, 0f)
    for (step in 0..19) {
      assertNull(aim.update(step * 250_000_000L, target))
    }
    val slightlyChanged = target.copy(x = 0.1f)
    assertNull(aim.update(4_999_000_000L, slightlyChanged))
    assertEquals(slightlyChanged, aim.update(5_000_000_000L, slightlyChanged))
  }

  @Test fun movingTheAimOrLosingFramesRestartsTheWait() {
    val aim = StableFloorAim()
    val before = FloorWaypoint(0f, -1.6f, 0f)
    val after = before.copy(x = 0.31f)
    repeat(20) { aim.update(it * 250_000_000L, before) }
    assertNull(aim.update(5_000_000_000L, after))
    repeat(19) { aim.update(5_250_000_000L + it * 250_000_000L, after) }
    assertNull(aim.update(10_500_000_000L, after))
    assertNull(aim.update(10_750_000_000L, null))
    assertNull(aim.update(11_000_000_000L, after))
  }

  @Test fun centerRayMustPointToNearbyGround() {
    val target = centerFloorTarget(1f, 1.5f, 2f, floatArrayOf(0f, -0.75f, -1f), 0f)
    assertEquals(FloorWaypoint(1f, 0f, 0f), target)
    assertNull(centerFloorTarget(0f, 1.5f, 0f, floatArrayOf(0f, 0.1f, -1f), 0f))
    assertNull(centerFloorTarget(0f, 1.5f, 0f, floatArrayOf(0f, -0.1f, -1f), 0f))
  }

  @Test fun centerPlacementUsesTheTargetWhenNormalRecognitionHasNoFloor() {
    val leader = DogLeader()
    leader.update(0f, 0f, 0f, 0f, -1f) { _, _ -> null }
    val target = FloorWaypoint(0.2f, -1.8f, -0.05f)
    leader.updateWalls(listOf(WallSegment(-1f, -1f, 1f, -1f)))
    assertNull(leader.placeFromCenter(0f, 0f, target))
    leader.updateWalls(emptyList())
    val dog = leader.placeFromCenter(0f, 0f, target)
    assertNotNull(dog)
    assertTrue(dog!!.visible)
    assertEquals(target, FloorWaypoint(dog.x, dog.z, dog.y))
  }
}
