package com.usee.animal.ar

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class LocalFloorPlannerTest {
  @Test fun findsDetourAroundMissingOrRaisedGroundWithoutCrossingCorners() {
    for (raised in listOf(false, true)) {
      val floor: (Float, Float) -> Float? = { x, z ->
        if (abs(x) < 0.2f && z < -0.12f && z > -0.6f) { if (raised) 0.4f else null } else 0f
      }
      val route = LocalFloorPlanner.plan(FloorWaypoint(0f, 0f, 0f), 0f, -0.84f, floor) { a, b -> abs(a.y - b.y) <= 0.08f }
      assertTrue(route.isNotEmpty())
      assertTrue(route.any { abs(it.x) >= 0.2f })
      assertTrue(route.all { it.y == 0f })
      assertEquals(-0.84f, route.last().z, 0.01f)
    }
  }

  @Test fun noCompleteRouteMeansStopInsteadOfWanderingAlongBarrier() {
    val route = LocalFloorPlanner.plan(FloorWaypoint(0f, 0f, 0f), 0f, -0.84f,
      { _, z -> if (z < -0.12f && z > -0.6f) null else 0f }) { _, _ -> true }
    assertTrue(route.isEmpty())
  }
}
