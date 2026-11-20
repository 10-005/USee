package com.usee.animal.ar

/** One retry at a time is enforced by the analyzer's worker gate. */
internal class AnalysisRetryPolicy {
  @Volatile var failed = false
    private set
  @Volatile private var nextAttempt = 0L
  private var failures = 0

  fun canAttempt(now: Long) = now >= nextAttempt
  fun initialized() { failed = false }
  fun fail(now: Long) {
    failures = (failures + 1).coerceAtMost(6)
    nextAttempt = now + minOf(30L, 1L shl (failures - 1)) * 1_000_000_000L
    failed = true
  }
  fun recover() { failures = 0; nextAttempt = 0L; failed = false }
}
