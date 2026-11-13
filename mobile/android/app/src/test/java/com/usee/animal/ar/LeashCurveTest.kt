package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class LeashCurveTest {
  @Test fun transformStorageCanBeReusedAcrossSegments() {
    val storage = FloatArray(16) { Float.NaN }
    val start = LeashPoint(1f, 2f, 3f)
    val end = LeashPoint(2f, 4f, 3f)
    assertSame(storage, LeashCurve.segmentTransform(start, end, storage))
    assertArrayEquals(LeashCurve.segmentTransform(start, end), storage, 0f)
  }
  @Test fun handProjectsToBottomCenterForDifferentCameraFieldsOfView() {
    for (focal in listOf(1.3f, 1.8f, 2.4f)) {
      val inverseProjection = FloatArray(16)
      inverseProjection[0] = 0.5f / focal
      inverseProjection[5] = 1f / focal
      inverseProjection[14] = -1f
      val hand = LeashCurve.handInCamera(inverseProjection)
      assertEquals(0f, hand.x * (focal / 0.5f) / -hand.z, 0.0001f)
      assertEquals(-1f, hand.y * focal / -hand.z, 0.0001f)
      assertEquals(-0.35f, hand.z, 0f)
    }
  }

  @Test fun handStaysFixedWhileCollarMovesAndChangesDirection() {
    val curve = LeashCurve()
    val hand = LeashPoint(0f, 1f, 0f)
    for (collar in listOf(LeashPoint(0f, 0.6f, -1f), LeashPoint(1f, 0.7f, -1f), LeashPoint(-1f, 0.5f, 1f))) {
      val points = curve.points(collar, hand, 0.016f)
      assertEquals(collar, points.first())
      assertEquals(hand, points.last())
      assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    }
  }

  @Test fun leashHasMoreSlackNearbyThanAtLongDistance() {
    fun sag(distance: Float): Float {
      val points = LeashCurve().points(LeashPoint(0f, 1f, -distance), LeashPoint(0f, 1f, 0f), 0f)
      return 1f - points[points.size / 2].y
    }
    assertTrue(sag(1f) > sag(2.5f) + 0.1f)
  }

  @Test fun cylinderEndsMatchCurveWithoutInvalidVerticalTransforms() {
    val start = LeashPoint(1f, 2f, 3f)
    for (end in listOf(LeashPoint(1f, 4f, 3f), LeashPoint(1f, 2f, 5f), LeashPoint(3f, 1f, 4f))) {
      val matrix = LeashCurve.segmentTransform(start, end)
      assertTrue(matrix.all { it.isFinite() })
      assertEquals(start.x, matrix[12] - matrix[4] / 2f, 0.0001f)
      assertEquals(start.y, matrix[13] - matrix[5] / 2f, 0.0001f)
      assertEquals(start.z, matrix[14] - matrix[6] / 2f, 0.0001f)
      assertEquals(end.x, matrix[12] + matrix[4] / 2f, 0.0001f)
      assertEquals(end.y, matrix[13] + matrix[5] / 2f, 0.0001f)
      assertEquals(end.z, matrix[14] + matrix[6] / 2f, 0.0001f)
    }
  }
}
