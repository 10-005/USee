package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class WallAvoidanceTest {
  private val front = WallSegment(-1f, 0f, 1f, 0f)

  @Test fun sweptBodyCannotCrossWallOrClipItsEdge() {
    assertTrue(front.blocks(0f, -0.5f, 0f, 0.5f, 0f))
    assertTrue(front.blocks(0f, -0.5f, 0f, -0.2f, 0f))
    assertTrue(front.blocks(1.1f, -0.5f, 1.1f, 0.5f, 0f))
    assertFalse(front.blocks(1.4f, -0.5f, 1.4f, 0.5f, 0f))
    assertFalse(front.blocks(0f, -0.5f, 0.5f, -0.5f, 0f))
  }

  @Test fun wallAboveDogOrOnDifferentFloorDoesNotBlock() {
    val high = WallSegment(-1f, 0f, 1f, 0f, 1f, 2f)
    assertFalse(high.blocks(0f, -0.5f, 0f, 0.5f, 0f))
    assertTrue(high.blocks(0f, -0.5f, 0f, 0.5f, 1f))
    assertFalse(high.blocks(0f, -0.5f, 0f, 0.5f, 2.5f))
  }

  @Test fun newlyDetectedOverlapAllowsEscapeButNotWallCrossing() {
    assertFalse(front.blocks(0f, -0.15f, 0f, -0.35f, 0f))
    assertFalse(front.blocks(0f, -0.15f, 0.2f, -0.15f, 0f))
    assertTrue(front.blocks(0f, -0.15f, 0f, 0.35f, 0f))
    assertTrue(front.blocks(0f, -0.15f, 0f, -0.1f, 0f))
    assertTrue(front.blocks(0f, -0.15f, 0f, -0.15f, 0f))
  }

  @Test fun frontWallSelectsSafeSideAndKeepsItStable() {
    val avoidance = WallAvoidance().apply { walls = listOf(front) }
    val first = avoidance.steer(0f, -0.5f, 0f, 0f, 0.45f, 0f, 0.02f)
    assertTrue(first.detouring)
    assertFalse(first.blocked)
    assertTrue(abs(sin(first.yaw)) > 0.9f)
    repeat(30) {
      val next = avoidance.steer(0f, -0.5f, 0f, 0.03f, 0.45f, 0f, 0.02f)
      assertTrue(cos(first.yaw - next.yaw) > 0.99f)
    }
  }

  @Test fun shallowApproachUsesSmallestSafeDirectionChange() {
    val avoidance = WallAvoidance().apply { walls = listOf(WallSegment(0.5f, -3f, 0.5f, 3f)) }
    val route = avoidance.steer(0f, 0f, 0f, 0.7f, 0.45f, 0.7f, 0.02f)
    assertTrue(route.detouring)
    assertTrue(cos(route.yaw - 0.7f) > 0.9f)
    assertTrue(avoidance.isClear(0f, 0f, sin(route.yaw) * 0.45f, cos(route.yaw) * 0.45f, 0f))
  }

  @Test fun blockedLeftSideUsesRightAndRejectedFloorCanSwitchSides() {
    val avoidance = WallAvoidance().apply {
      walls = listOf(front, WallSegment(-0.45f, -1f, -0.45f, 1f))
    }
    val route = avoidance.steer(0f, -0.5f, 0f, 0f, 0.45f, 0f, 0.02f)
    assertTrue(sin(route.yaw) > 0.9f)
    avoidance.walls = listOf(front)
    avoidance.rejectRoute(route.yaw)
    val other = avoidance.steer(0f, -0.5f, 0f, 0f, 0.45f, 0f, 0.02f)
    assertTrue(sin(other.yaw) < -0.9f)
  }

  @Test fun enclosedSpaceStopsAndRemovedWallRestoresDirectPath() {
    val avoidance = WallAvoidance().apply { walls = listOf(
      WallSegment(-1f, 0.3f, 1f, 0.3f), WallSegment(-1f, -0.3f, 1f, -0.3f),
      WallSegment(-0.3f, -1f, -0.3f, 1f), WallSegment(0.3f, -1f, 0.3f, 1f),
    ) }
    assertTrue(avoidance.steer(0f, 0f, 0f, 0f, 0.45f, 0f, 0.02f).blocked)
    avoidance.walls = emptyList()
    val direct = avoidance.steer(0f, 0f, 0f, 0f, 0.45f, 0f, 0.02f)
    assertFalse(direct.blocked)
    assertFalse(direct.detouring)
    assertEquals(0f, direct.yaw, 0f)
  }
}
