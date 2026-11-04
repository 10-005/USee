package com.usee.animal.ar

import java.nio.FloatBuffer
import org.junit.Assert.*
import org.junit.Test

class SemanticFloorMaskTest {
  @Test fun worldProjectionUsesCapturePoseInsteadOfCurrentScreenPixels() {
    val captureInverse = identity.copyOf().apply { this[12] = -1f }
    val mask = SemanticFloorMask(10, 10, BooleanArray(100) { true }, captureInverse,
      100f, 100f, 50f, 50f, 100, 100, 0, 1_000L, IntArray(100) { 3 })
    assertEquals(1, mask.pathAtWorld(1f, 0f, -1f, 1_001L))
    assertEquals(-1, mask.pathAtWorld(0f, 0f, -1f, 1_001L))
    assertEquals(1, mask.pathAlongWorldDirection(0f, 0f, -1f, 1_001L))
  }

  @Test fun diagnosticDistinguishesLowConfidencePathFromObstaclesAndStaleResults() {
    val labels = listOf(3, 3, 0, 15)
    val logits = FloatArray(150 * labels.size) { -10f }
    labels.forEachIndexed { i, label -> logits[label * labels.size + i] = if (i == 1) -9.9f else 10f }
    val pixels = SemanticFloorMask.decodeDetails(FloatBuffer.wrap(logits), 150, 4, 1)
    assertArrayEquals(booleanArrayOf(true, false, false, false), pixels.walkable)
    val mask = SemanticFloorMask(4, 1, pixels.walkable, identity, 100f, 100f, 50f, 50f, 100, 100, 0, 1_000L, pixels.labels)
    assertEquals(1, mask.pathAtImage(0f, 0.5f, 1_001L))
    assertEquals(1, mask.pathAtImage(0.34f, 0.5f, 1_001L))
    assertEquals(0, mask.pathAtImage(0.67f, 0.5f, 1_001L))
    assertEquals(0, mask.pathAtImage(1f, 0.5f, 1_001L))
    assertEquals(-1, mask.pathAtImage(0f, 0.5f, 1_001L + SemanticFloorMask.MAX_AGE_NANOS))
    assertEquals(-1, mask.pathAtImage(-0.1f, 0.5f, 1_001L))
  }

  private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

  private fun mask(values: BooleanArray = BooleanArray(100) { true }, rotation: Int = 0) = SemanticFloorMask(
    10, 10, values, identity, 100f, 100f, 50f, 50f, 100, 100, rotation, 1_000L,
  )

  @Test fun projectsWorldPointAndRejectsUnknownOrStaleRegions() {
    val mask = mask()
    assertTrue(mask.allows(0f, 0f, -1f, 1_001L))
    assertFalse(mask.allows(0f, 0f, 1f, 1_001L))
    assertFalse(mask.allows(2f, 0f, -1f, 1_001L))
    assertFalse(mask.allows(0f, 0f, -1f, 1_001L + SemanticFloorMask.MAX_AGE_NANOS))
  }

  @Test fun isolatedBadPixelIsToleratedButCenterAndMajorityStillMatter() {
    val values = BooleanArray(100) { true }
    values[5 * 10 + 6] = false
    assertTrue(mask(values).allows(0f, 0f, -1f, 1_001L))
    values[4 * 10 + 4] = false
    values[4 * 10 + 5] = false
    values[4 * 10 + 6] = false
    assertFalse(mask(values).allows(0f, 0f, -1f, 1_001L))
    val missingCenter = BooleanArray(100) { true }.apply { this[55] = false }
    assertFalse(mask(missingCenter).allows(0f, 0f, -1f, 1_001L))
  }

  @Test fun rotationMatchesClockwiseImageRotation() {
    assertEquals(0.7f, SemanticFloorMask.rotate(0.2f, 0.3f, 90).first, 0.001f)
    assertEquals(0.2f, SemanticFloorMask.rotate(0.2f, 0.3f, 90).second, 0.001f)
    assertEquals(0.3f, SemanticFloorMask.rotate(0.2f, 0.3f, 270).first, 0.001f)
    assertEquals(0.8f, SemanticFloorMask.rotate(0.2f, 0.3f, 270).second, 0.001f)
    val values = BooleanArray(100)
    // Raw image (u=.7,v=.3) becomes upright (u=.7,v=.7).
    for (y in 5..7) for (x in 5..7) values[y * 10 + x] = true
    assertTrue(mask(values, 90).allows(0.2f, 0.2f, -1f, 1_001L))
    assertFalse(mask(values, 0).allows(0.2f, 0.2f, -1f, 1_001L))
  }

  @Test fun onlyConfidentWalkableAde20kClassesAreAllowed() {
    val labels = listOf(3, 28, 11, 52, 0, 15, 53, 6, 12, 3)
    val logits = FloatArray(150 * labels.size) { -10f }
    labels.forEachIndexed { pixel, label -> logits[label * labels.size + pixel] = if (pixel == 9) -9.9f else 10f }
    val mask = SemanticFloorMask.decode(FloatBuffer.wrap(logits), 150, labels.size, 1)
    assertArrayEquals(booleanArrayOf(true, true, true, true, false, false, false, false, false, false), mask)
  }
}
