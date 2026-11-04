package com.usee.animal.ar

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

internal enum class FloorEvidence { CLEAR, OBSTACLE, UNKNOWN }
internal data class FloorDecision(val height: Float?, val cautious: Boolean, val reason: String = "")

/** World-space, capture-timestamped evidence. Render callbacks never count as new ML samples. */
internal class WalkabilityMemory {
  private data class Cell(val x: Int, val z: Int)
  private data class Entry(var y: Float? = null, var clearAt: Long = -1L,
    var blockedSince: Long = -1L, var lastBlockedSample: Long = -1L, var confirmed: Boolean = false)
  private val cells = LinkedHashMap<Cell, Entry>()
  private var lastClearX = 0f
  private var lastClearZ = 0f
  private var lastClearY: Float? = null
  private var lastClearAt = -1L
  private var uncertainSince = -1L
  val previousGroundY get() = lastClearY

  fun clear() { cells.clear(); lastClearY = null; lastClearAt = -1L; uncertainSince = -1L }

  fun resolve(x: Float, z: Float, ground: Float?, evidence: FloorEvidence,
    sampleAt: Long, now: Long, allowGrace: Boolean): FloorDecision {
    val key = Cell(floor(x / 0.15f).toInt(), floor(z / 0.15f).toInt())
    val entry = cells.getOrPut(key) { Entry() }
    if (cells.size > 2048) cells.remove(cells.keys.first())
    if (evidence == FloorEvidence.CLEAR && ground != null) {
      entry.y = ground
      entry.clearAt = sampleAt
      entry.blockedSince = -1L
      entry.lastBlockedSample = -1L
      entry.confirmed = false
      lastClearX = x; lastClearZ = z; lastClearY = ground; lastClearAt = sampleAt
      uncertainSince = -1L
      return FloorDecision(ground, false)
    }
    if (evidence == FloorEvidence.OBSTACLE && sampleAt != entry.lastBlockedSample) {
      if (entry.lastBlockedSample < 0L || sampleAt - entry.lastBlockedSample > 600_000_000L) entry.blockedSince = sampleAt
      entry.lastBlockedSample = sampleAt
      entry.confirmed = sampleAt - entry.blockedSince >= 300_000_000L
      if (entry.confirmed) entry.clearAt = -1L
    }
    if (entry.confirmed && now - entry.lastBlockedSample > 1_500_000_000L) entry.confirmed = false
    if (entry.confirmed) return FloorDecision(null, false, "장애물 확인")
    if (!allowGrace) return FloorDecision(null, true, "바닥 미확인")
    if (uncertainSince < 0L) uncertainSince = now
    val cachedY = entry.y
    if (cachedY != null && entry.clearAt >= 0L && now - entry.clearAt in 0L..3_000_000_000L &&
      (ground == null || abs(ground - cachedY) <= 0.08f)) {
      return FloorDecision(cachedY, true)
    }
    val previous = lastClearY
    // A short, distance-bounded creep bridges a single bad observation, never a new unbounded path.
    if (previous != null && now - lastClearAt in 0L..3_000_000_000L && now - uncertainSince <= 300_000_000L &&
      hypot(x - lastClearX, z - lastClearZ) <= 0.15f && (ground == null || abs(ground - previous) <= 0.08f)) {
      return FloorDecision(previous, true)
    }
    return FloorDecision(null, true, if (evidence == FloorEvidence.OBSTACLE) "장애물 재확인" else "바닥 미확인")
  }
}

internal class WalkStopStats {
  private data class Stat(var count: Int = 0, var seconds: Double = 0.0)
  private val entries = linkedMapOf<String, Stat>()
  private var active = ""
  private var lastAt = 0L
  fun record(reason: String, now: Long) {
    if (lastAt != 0L && active.isNotEmpty()) entries.getValue(active).seconds += (now - lastAt).coerceAtLeast(0L) / 1e9
    if (reason.isNotEmpty() && reason != active) entries.getOrPut(reason) { Stat() }.count++
    active = reason; lastAt = now
  }
  fun summary(): String {
    val total = entries.values.sumOf { it.count }
    val seconds = entries.values.sumOf { it.seconds }
    val top = entries.maxByOrNull { it.value.seconds }
    return "정지 ${total}회 · %.1f초".format(seconds) + (top?.let { " · 최다 ${it.key} ${it.value.count}회" } ?: "")
  }
  fun report(): String = entries.entries.joinToString(", ") { (reason, stat) ->
    "$reason:${stat.count}회/%.1f초".format(stat.seconds)
  }
}
