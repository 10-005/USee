package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class UserWalkRegionTest {
  @Test fun repeatedUndergroundPlaneDoesNotReplaceTheWalkingFloor() {
    val filter = GroundLevelFilter()
    filter.update(0f, 0f, 0L)
    repeat(50) { assertEquals(0f, filter.update(-3f, 0f, it + 1L), 0f) }
    assertEquals(0f, filter.update(Float.NaN, 0f, 51L), 0f)
  }

  @Test fun onlyActualFootprintIsTrustedNotEntireNearbyDisk() {
    val region = UserWalkRegion()
    region.update(0f, 0f, 0.2f)
    assertEquals(0.2f, region.heightAt(0.2f, 0f))
    assertEquals(0.2f, region.heightAt(0.5f, 0f))
    assertEquals(0.2f, region.heightAt(-0.5f, 0f))
    assertEquals(0.2f, region.heightAt(0f, 0.5f))
    assertEquals(0.2f, region.heightAt(0f, -0.5f))
    assertNull(region.heightAt(0.51f, 0f))
    assertNull(region.heightAt(0.4f, 0.4f))
    assertNull(region.heightAt(1.5f, 0f))
    assertNull(region.heightAt(0f, -1.4f))
    assertNull(region.heightAt(1.51f, 0f))
  }

  @Test fun groundLevelRequiresThreeDifferentRefreshesBeforeChanging() {
    val filter = GroundLevelFilter()
    assertEquals(-0.2f, filter.update(null, -0.2f, 0L), 0f)
    assertEquals(0f, filter.update(0f, -0.2f, 1L), 0f)
    repeat(20) { assertEquals(0f, filter.update(0.3f, 0f, 2L), 0f) }
    assertEquals(0f, filter.update(0.3f, 0f, 3L), 0f)
    assertEquals(0.3f, filter.update(0.3f, 0f, 4L), 0f)
    filter.clear()
    assertEquals(1f, filter.update(1f, 0f, 5L), 0f)
  }

  @Test fun traveledCorridorRemainsTrustedOutsideCurrentRadius() {
    val region = UserWalkRegion()
    for (i in 0..30) region.update(i * 0.1f, 0f, 0f)
    assertEquals(0f, region.heightAt(0.5f, 0.2f))
    assertEquals(0f, region.heightAt(0.5f, 0.49f))
    assertNull(region.heightAt(0.5f, 0.6f))
    assertNull(region.heightAt(5f, 0f))
    // A previously untrusted/red location becomes ground once the user walks there.
    for (i in 31..50) region.update(i * 0.1f, 0f, 0f)
    assertEquals(0f, region.heightAt(5f, 0f))
  }

  @Test fun relocalizationDoesNotCreateGroundAcrossUntraveledGap() {
    val region = UserWalkRegion()
    region.update(0f, 0f, 0f)
    region.update(5f, 0f, 0f)
    assertNull(region.heightAt(0f, 0f))
    assertNull(region.heightAt(2.5f, 0f))
    region.clear()
    assertNull(region.heightAt(5f, 0f))
  }
}
