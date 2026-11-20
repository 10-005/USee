package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class LeashInteractionTest {
  @Test fun sideSocketIsUsedOnlyWhenFacingViewerWithStableBoundary() {
    val forward = LeashPoint(0f, 0f, 1f)
    assertTrue(LeashInteraction.facingViewer(forward, LeashPoint(0f, 0f, 1f), false))
    assertFalse(LeashInteraction.facingViewer(forward, LeashPoint(0f, 0f, -1f), true))
    assertFalse(LeashInteraction.facingViewer(forward, LeashPoint(1f, 0f, 0f), false))
    assertFalse(LeashInteraction.facingViewer(forward, LeashPoint(1f, 0f, 0.2f), false))
    assertTrue(LeashInteraction.facingViewer(forward, LeashPoint(1f, 0f, 0.2f), true))
  }

  @Test fun leashTouchIncludesFingerToleranceButDoesNotCatchUnrelatedScreenTaps() {
    assertTrue(LeashInteraction.nearSegment(50f, 60f, 0f, 0f, 100f, 100f, 24f))
    assertTrue(LeashInteraction.nearSegment(100f, 110f, 0f, 0f, 100f, 100f, 24f))
    assertFalse(LeashInteraction.nearSegment(90f, 10f, 0f, 0f, 100f, 100f, 24f))
    assertTrue(LeashInteraction.nearSegment(2f, 2f, 0f, 0f, 0f, 0f, 24f))
  }
}
