package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class AnalysisRetryPolicyTest {
  @Test fun thermalStatusAndHeadroomReduceWorkUntilTheDeviceHasCooled() {
    val policy = ThermalWorkloadPolicy()
    policy.update(0, 0.9f)
    assertTrue(policy.reduced)
    policy.update(0, 0.8f)
    assertTrue(policy.reduced)
    policy.update(0, 0.65f)
    assertFalse(policy.reduced)
    policy.update(2)
    assertTrue(policy.reduced)
    policy.update(1)
    assertTrue(policy.reduced)
    policy.update(0)
    assertFalse(policy.reduced)
  }
  @Test fun failuresBackOffToThirtySecondsAndRecoveryResetsTheDelay() {
    val policy = AnalysisRetryPolicy()
    var now = 1_000_000_000L
    assertTrue(policy.canAttempt(now))
    for (seconds in listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L)) {
      policy.fail(now)
      assertTrue(policy.failed)
      val due = now + seconds * 1_000_000_000L
      assertFalse(policy.canAttempt(due - 1L))
      assertTrue(policy.canAttempt(due))
      policy.initialized()
      assertFalse(policy.failed)
      now = due
    }
    policy.recover()
    assertFalse(policy.failed)
    policy.fail(now)
    assertTrue(policy.canAttempt(now + 1_000_000_000L))
  }

  @Test fun workloadIntervalsReactToMotionAndStayBelowMaskExpiry() {
    assertEquals(250_000_000L, analysisIntervalNanos(true, false))
    assertEquals(750_000_000L, analysisIntervalNanos(false, false))
    assertEquals(500_000_000L, analysisIntervalNanos(true, true))
    assertEquals(1_000_000_000L, analysisIntervalNanos(false, true))
    for (active in listOf(true, false)) for (reduced in listOf(true, false)) {
      assertTrue(analysisIntervalNanos(active, reduced) < SemanticFloorMask.MAX_AGE_NANOS)
    }
  }
}
