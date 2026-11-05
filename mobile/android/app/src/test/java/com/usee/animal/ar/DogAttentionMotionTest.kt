package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class DogAttentionMotionTest {
  @Test fun cameraCrossingBehindDogDoesNotFlipNeckAcrossTheAngleWrap() {
    val next = smoothAttentionYaw(179f, -179f, 0.05f)
    assertTrue(next > 179f && next < 181f)
    assertEquals(179f, smoothAttentionYaw(179f, -179f, 0f), 0.001f)
  }
  @Test fun gazeBlendsInAndOutWithinThreeSecondsAndTailStopsAtTwo() {
    assertEquals(0f, attentionWeight(0f, 3f), 0.001f)
    assertEquals(1f, attentionWeight(1f, 3f), 0.001f)
    assertEquals(0.5f, attentionWeight(2.875f, 3f), 0.001f)
    assertEquals(0f, attentionWeight(3f, 3f), 0.001f)
    assertTrue(kotlin.math.abs(attentionTailDegrees(0.3125f)) > 19f)
    assertEquals(0f, attentionTailDegrees(2f), 0.001f)
    assertEquals(0f, attentionTailDegrees(2.5f), 0.001f)
  }

  @Test fun gazeUsesActualNoseDirectionAndShortestTurnToCamera() {
    assertEquals(0f, attentionYaw(0f, 1f, 0f, 1f), 0.001f)
    assertEquals(PI.toFloat() / 2f, attentionYaw(0f, 1f, 1f, 0f), 0.001f)
    assertEquals(-PI.toFloat() / 2f, attentionYaw(0f, 1f, -1f, 0f), 0.001f)
    assertEquals(PI.toFloat(), kotlin.math.abs(attentionYaw(0f, -1f, 0f, 1f)), 0.001f)
  }
}
