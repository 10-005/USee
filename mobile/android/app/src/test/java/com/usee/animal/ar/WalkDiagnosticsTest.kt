package com.usee.animal.ar

import org.junit.Assert.*
import org.junit.Test

class WalkDiagnosticsTest {
  private val diagnostics = WalkDiagnostics()
  private val stops = WalkStopStats()

  private fun report(reason: String, now: Long, frameInterval: Long? = 16_000_000L,
    semantic: SemanticFloorMask? = null, failed: Boolean = false,
    depth: DepthGrid? = null, depthSupported: Boolean = true, trackingFailure: String? = null): String? {
    stops.record(reason, now)
    return diagnostics.report(reason, now, frameInterval, semantic, failed,
      depth, depthSupported, trackingFailure, stops)
  }

  @Test fun firstStopCorrelatesFrameTimingAndThePublishedAnalysisSample() {
    val now = 2_000_000_000L
    val mask = mask(1_000_000_000L)
    val line = report("바닥 미확인", now, 300_000_000L, mask, depth = DepthGrid(1_800_000_000L))!!
    assertTrue(line.contains("state=stopped reason=\"바닥 미확인\""))
    assertTrue(line.contains("frameIntervalMs=300 frameMaxIntervalMs=300"))
    assertTrue(line.contains("semantic=fresh semanticAgeMs=1000 analysisMs=180 inferenceMs=120"))
    assertTrue(line.contains("depth=fresh depthAgeMs=200"))
    assertTrue(line.contains("totals=[바닥 미확인:1회/"))
  }

  @Test fun unknownUnsupportedAndExpiredInputsRemainDistinguishable() {
    val missing = report("추적 끊김", 1_000_000_000L, frameInterval = null,
      depthSupported = false, trackingFailure = "INSUFFICIENT_FEATURES")!!
    assertTrue(missing.contains("frameIntervalMs=unknown"))
    assertTrue(missing.contains("semantic=missing semanticAgeMs=unknown analysisMs=unknown inferenceMs=unknown"))
    assertTrue(missing.contains("depth=unsupported depthAgeMs=unknown"))
    assertTrue(missing.contains("trackingFailure=INSUFFICIENT_FEATURES"))
    val expired = report("추적 끊김", 6_000_000_000L, semantic = mask(1_000_000_000L),
      depth = DepthGrid(1_000_000_000L))!!
    assertTrue(expired.contains("semantic=stale semanticAgeMs=5000"))
    assertTrue(expired.contains("depth=stale depthAgeMs=5000"))
    val failed = report("분석 실패", 7_000_000_000L, failed = true)!!
    assertTrue(failed.contains("semantic=failed"))
    assertTrue(failed.contains("depth=missing depthAgeMs=unknown"))
  }

  @Test fun repeatedStopReportsEveryFiveSecondsAndRecoveryReportsAfterOneSecond() {
    assertNull(report("", 1_000_000_000L))
    assertNotNull(report("벽", 2_000_000_000L))
    assertNull(report("벽", 3_000_000_000L))
    assertNull(report("벽", 6_999_999_999L))
    assertNotNull(report("벽", 7_000_000_000L))
    assertNull(report("", 7_100_000_000L))
    val recovered = report("", 8_000_000_000L)!!
    assertTrue(recovered.contains("state=recovered reason=\"\""))
    assertTrue(recovered.contains("totals=[벽:1회/"))
    assertNull(report("", 9_000_000_000L))
    assertTrue(report("", 13_000_000_000L)!!.contains("state=walking"))
  }

  @Test fun rapidTransitionsStayRateLimitedButCountsAndFrameSpikesAreRetained() {
    assertNotNull(report("벽", 1_000_000_000L))
    assertNull(report("", 1_100_000_000L))
    assertNull(report("프레임 지연", 1_200_000_000L, frameInterval = 350_000_000L))
    assertNull(report("", 1_300_000_000L))
    val line = report("바닥 미확인", 2_000_000_000L)!!
    assertTrue(line.contains("frameIntervalMs=16 frameMaxIntervalMs=350"))
    assertTrue(line.contains("벽:1회/"))
    assertTrue(line.contains("프레임 지연:1회/"))
    assertTrue(line.contains("바닥 미확인:1회/"))
    assertNull(report("", 2_100_000_000L))
    assertNull(report("바닥 미확인", 2_200_000_000L))
    val next = report("바닥 미확인", 7_000_000_000L)!!
    assertTrue(next.contains("바닥 미확인:2회/"))
    assertTrue(next.contains("frameMaxIntervalMs=16"))
  }

  @Test fun timingsCannotLeakFromAPreviousMaskOrBecomeNegative() {
    report("벽", 1_000_000_000L, semantic = mask(500_000_000L))
    val cleared = report("분석 실패", 2_000_000_000L, failed = true)!!
    assertTrue(cleared.contains("analysisMs=unknown inferenceMs=unknown"))
    val future = report("높이 차", 3_000_000_000L, frameInterval = -1L,
      semantic = mask(4_000_000_000L), depth = DepthGrid(4_000_000_000L))!!
    assertTrue(future.contains("frameIntervalMs=unknown frameMaxIntervalMs=unknown"))
    assertTrue(future.contains("semantic=stale semanticAgeMs=unknown"))
    assertTrue(future.contains("depth=stale depthAgeMs=unknown"))
  }

  private fun mask(capturedAt: Long) = SemanticFloorMask(
    1, 1, booleanArrayOf(true), FloatArray(16), 1f, 1f, 0f, 0f, 1, 1, 0, capturedAt,
    analysisNanos = 180_000_000L, inferenceNanos = 120_000_000L,
  )
}
