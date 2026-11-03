package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class AnimationSamplingTest {
  @Test fun earlyFrameTimestampsNeverSampleBeforeTheClip() {
    assertEquals(0f, sampleAnimationTime(-0.016f, 2f, true), 0f)
    assertEquals(0f, sampleAnimationTime(-0.016f, 2f, false), 0f)
  }

  @Test fun loopingClipsStayInsideTheirDurationAfterManyCycles() {
    assertEquals(0.5f, sampleAnimationTime(10.5f, 2f, true), 0.0001f)
    assertEquals(0f, sampleAnimationTime(10f, 2f, true), 0.0001f)
  }

  @Test fun finishedOneShotsHoldTheFinalPoseInsteadOfWrappingToStanding() {
    val finalTime = sampleAnimationTime(2f, 2f, false)
    assertTrue(finalTime < 2f)
    assertTrue(finalTime > 1.999f)
    assertEquals(finalTime, sampleAnimationTime(10f, 2f, false), 0f)
  }
}
