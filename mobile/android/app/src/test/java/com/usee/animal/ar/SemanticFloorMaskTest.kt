package com.usee.animal.ar

import java.nio.FloatBuffer
import org.junit.Assert.*
import org.junit.Test

class SemanticFloorMaskTest {
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

  @Test fun boundaryTouchingFurnitureIsRejected() {
    val values = BooleanArray(100) { true }
    values[5 * 10 + 6] = false
    assertFalse(mask(values).allows(0f, 0f, -1f, 1_001L))
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
