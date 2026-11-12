package com.usee.animal.ar

/** Session-local diagnostics. Count every observation, but format/log at most once per second. */
internal class WalkDiagnostics {
  private var lastReportAt: Long? = null
  private var lastReportedReason = ""
  private var maxFrameIntervalNanos: Long? = null

  fun report(reason: String, now: Long, frameIntervalNanos: Long?,
    semantic: SemanticFloorMask?, semanticFailed: Boolean, depth: DepthGrid?, depthSupported: Boolean,
    trackingFailure: String?, stops: WalkStopStats): String? {
    if (frameIntervalNanos != null && frameIntervalNanos >= 0L) {
      maxFrameIntervalNanos = maxOf(maxFrameIntervalNanos ?: 0L, frameIntervalNanos)
    }
    val previous = lastReportAt
    if (previous == null && reason.isEmpty()) return null
    if (previous != null) {
      val elapsed = now - previous
      if (elapsed < 1_000_000_000L ||
        reason == lastReportedReason && elapsed < 5_000_000_000L) return null
    }
    val state = when {
      reason.isNotEmpty() -> "stopped"
      lastReportedReason.isNotEmpty() -> "recovered"
      else -> "walking"
    }
    lastReportAt = now
    lastReportedReason = reason
    val semanticState = when {
      semanticFailed -> "failed"
      semantic == null -> "missing"
      semantic.isFresh(now) -> "fresh"
      else -> "stale"
    }
    val depthState = when {
      !depthSupported -> "unsupported"
      depth == null -> "missing"
      depth.fresh(now) -> "fresh"
      else -> "stale"
    }
    val maximumInterval = millis(maxFrameIntervalNanos)
    maxFrameIntervalNanos = null
    return "state=$state reason=\"$reason\" frameIntervalMs=${millis(frameIntervalNanos)}" +
      " frameMaxIntervalMs=$maximumInterval" +
      " semantic=$semanticState semanticAgeMs=${age(now, semantic?.capturedAtNanos)}" +
      " analysisMs=${millis(semantic?.analysisNanos)} inferenceMs=${millis(semantic?.inferenceNanos)}" +
      " depth=$depthState depthAgeMs=${age(now, depth?.capturedAt)}" +
      " trackingFailure=${trackingFailure ?: "none"}" +
      " summary=\"${stops.summary()}\" totals=[${stops.report()}]"
  }

  private fun age(now: Long, capturedAt: Long?) = millis(capturedAt?.let { now - it })
  private fun millis(nanos: Long?): String =
    if (nanos == null || nanos < 0L) "unknown" else (nanos / 1_000_000L).toString()
}
