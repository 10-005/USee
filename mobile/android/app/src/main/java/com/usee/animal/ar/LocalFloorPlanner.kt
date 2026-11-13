package com.usee.animal.ar

import java.util.PriorityQueue
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class FloorWaypoint(val x: Float, val z: Float, val y: Float)

/** Bounded A* over verified ground, not guessed free pixels. Searches within 1.5m. */
internal object LocalFloorPlanner {
  private const val STEP = 0.12f
  private const val HALF = 12
  private const val WIDTH = HALF * 2 + 1

  fun plan(start: FloorWaypoint, targetX: Float, targetZ: Float,
    floorAt: (Float, Float) -> Float?, edgeAllowed: (FloorWaypoint, FloorWaypoint) -> Boolean): List<FloorWaypoint> {
    val dx = targetX - start.x
    val dz = targetZ - start.z
    val scale = (1.2f / hypot(dx, dz).coerceAtLeast(0.001f)).coerceAtMost(1f)
    val gx = (dx * scale / STEP).roundToInt().coerceIn(-HALF, HALF)
    val gz = (dz * scale / STEP).roundToInt().coerceIn(-HALF, HALF)
    if (gx == 0 && gz == 0) return emptyList()
    fun index(x: Int, z: Int) = (z + HALF) * WIDTH + x + HALF
    val seen = BooleanArray(WIDTH * WIDTH)
    val terrain = arrayOfNulls<FloorWaypoint>(WIDTH * WIDTH)
    fun point(x: Int, z: Int): FloorWaypoint? {
      if (x !in -HALF..HALF || z !in -HALF..HALF || hypot(x * STEP, z * STEP) > 1.5f) return null
      val id = index(x, z)
      if (!seen[id]) {
        seen[id] = true
        val wx = start.x + x * STEP
        val wz = start.z + z * STEP
        terrain[id] = floorAt(wx, wz)?.let { FloorWaypoint(wx, wz, it) }
      }
      return terrain[id]
    }
    val origin = index(0, 0)
    seen[origin] = true; terrain[origin] = start
    val targetKnown = point(gx, gz) != null
    val costs = FloatArray(WIDTH * WIDTH) { Float.POSITIVE_INFINITY }
    val parents = IntArray(WIDTH * WIDTH) { -1 }
    val closed = BooleanArray(WIDTH * WIDTH)
    data class QueueEntry(val x: Int, val z: Int, val score: Float)
    val queue = PriorityQueue<QueueEntry>(compareBy { it.score })
    costs[origin] = 0f
    queue.add(QueueEntry(0, 0, hypot(gx.toFloat(), gz.toFloat())))
    var expanded = 0
    var closest = origin
    var closestDistance = hypot(gx.toFloat(), gz.toFloat())
    fun pathTo(id: Int): List<FloorWaypoint> {
      val result = mutableListOf<FloorWaypoint>()
      var cursor = id
      while (cursor != origin && cursor >= 0) { result += terrain[cursor]!!; cursor = parents[cursor] }
      return result.asReversed()
    }
    while (queue.isNotEmpty() && expanded++ < 500) {
      val current = queue.remove()
      val id = index(current.x, current.z)
      if (closed[id]) continue
      closed[id] = true
      val remaining = hypot((gx - current.x).toFloat(), (gz - current.z).toFloat())
      if (remaining < closestDistance) { closest = id; closestDistance = remaining }
      if (current.x == gx && current.z == gz) {
        return pathTo(id)
      }
      val from = terrain[id]!!
      for (sx in -1..1) for (sz in -1..1) {
        if (sx == 0 && sz == 0) continue
        val nx = current.x + sx
        val nz = current.z + sz
        val to = point(nx, nz) ?: continue
        val next = index(nx, nz)
        if (closed[next] || !edgeAllowed(from, to)) continue
        if (sx != 0 && sz != 0) {
          val sideX = point(nx, current.z) ?: continue
          val sideZ = point(current.x, nz) ?: continue
          if (!edgeAllowed(from, sideX) || !edgeAllowed(from, sideZ) || !edgeAllowed(sideX, to) || !edgeAllowed(sideZ, to)) continue
        }
        val cost = costs[id] + hypot(sx.toFloat(), sz.toFloat())
        if (cost >= costs[next]) continue
        costs[next] = cost; parents[next] = id
        queue.add(QueueEntry(nx, nz, cost + hypot((gx - nx).toFloat(), (gz - nz).toFloat())))
      }
    }
    // Advance only along verified edges toward an unseen target. A known but
    // disconnected goal still means stop, rather than wandering along a barrier.
    return if (!targetKnown && closest != origin) pathTo(closest) else emptyList()
  }
}
