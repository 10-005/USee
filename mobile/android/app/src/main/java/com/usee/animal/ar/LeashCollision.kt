package com.usee.animal.ar

/** Animated bone-space proxies, expressed in world metres. Includes rope thickness/skin margin. */
internal data class LeashCapsule(val a: LeashPoint, val b: LeashPoint, val radius: Float) {
  fun intersects(start: LeashPoint, end: LeashPoint): Boolean =
    segmentDistanceSquared(start, end, a, b) < radius * radius
}

internal data class LeashBody(val capsules: List<LeashCapsule>, val neck: LeashPoint)

private fun dot(a: LeashPoint, b: LeashPoint) = a.x*b.x + a.y*b.y + a.z*b.z

/** Closest points on two finite segments, including parallel and zero-length segments. */
internal fun segmentDistanceSquared(p: LeashPoint, q: LeashPoint, a: LeashPoint, b: LeashPoint): Float {
  val u = q - p; val v = b - a; val w = p - a
  val uu = dot(u,u); val vv = dot(v,v); val uv = dot(u,v)
  val uw = dot(u,w); val vw = dot(v,w)
  var s: Float
  var t: Float
  if (uu < 1e-10f && vv < 1e-10f) return dot(w,w)
  if (uu < 1e-10f) { s = 0f; t = (vw / vv).coerceIn(0f,1f) }
  else if (vv < 1e-10f) { t = 0f; s = (-uw / uu).coerceIn(0f,1f) }
  else {
    val denominator = uu*vv - uv*uv
    s = if (denominator > 1e-10f) ((uv*vw - uw*vv)/denominator).coerceIn(0f,1f) else 0f
    t = (uv*s + vw)/vv
    if (t < 0f) { t = 0f; s = (-uw/uu).coerceIn(0f,1f) }
    else if (t > 1f) { t = 1f; s = ((uv-uw)/uu).coerceIn(0f,1f) }
  }
  val delta = w + u*s - v*t
  return dot(delta,delta).coerceAtLeast(0f)
}

/** Collision-free routing with rounded bends validated against every body capsule. */
internal class LeashCollision {
  private var previousBends = emptyList<LeashPoint>()
  private var previousNeck: LeashPoint? = null
  private var clearFrames = 0
  fun reset() { previousBends = emptyList(); previousNeck = null; clearFrames = 0 }

  fun route(points: List<LeashPoint>, body: LeashBody, segments: Int = 24): List<LeashPoint> {
    require(segments > 0)
    if (points.size < 2 || body.capsules.isEmpty()) return points
    val start = points.first(); val hand = points.last()
    previousNeck?.let { old -> previousBends = previousBends.map { it + (body.neck - old) } }
    previousNeck = body.neck
    fun clear(a: LeashPoint, b: LeashPoint) = body.capsules.none { it.intersects(a,b) }
    val outward = start - body.neck
    val normal = if (outward.length() > 0.001f) outward * (1f/outward.length()) else LeashPoint(0f,1f,0f)
    val guide = start + normal * 0.045f
    // The endpoints must remain exact. Impossible configurations must not draw a penetrating rope.
    if (!clear(start,guide) || !clear(hand,hand)) { reset(); return emptyList() }
    val guided = listOf(start,guide) + if (points.size > 2) points.drop(2) else listOf(hand)
    val directClear = guided.zipWithNext().all { clear(it.first,it.second) }
    clearFrames = if (directClear) clearFrames + 1 else 0
    if (directClear && (previousBends.isEmpty() || clearFrames >= 8)) {
      previousBends = emptyList()
      return rounded(guided, body, segments)
    }
    // Keep a still-valid detour rather than flipping around the body every frame.
    if (previousBends.isNotEmpty()) {
      val previous = listOf(start,guide) + previousBends + hand
      if (previous.zipWithNext().all { clear(it.first,it.second) }) return rounded(previous,body,segments)
    }
    val nodes = arrayListOf(guide,hand)
    // Cube-corner offsets sit outside each capsule; a bounded visibility graph joins them.
    for (capsule in body.capsules) {
      val margin = capsule.radius + 0.025f
      for (center in listOf(capsule.a,capsule.b)) {
        for (x in listOf(-1f,1f)) for (y in listOf(1f,-1f)) for (z in listOf(-1f,1f)) {
          val candidate = center + LeashPoint(x*margin,y*margin,z*margin)
          if (clear(candidate,candidate)) nodes.add(candidate)
        }
      }
    }
    val costs = FloatArray(nodes.size) { Float.POSITIVE_INFINITY }
    val parent = IntArray(nodes.size) { -1 }
    val visited = BooleanArray(nodes.size)
    costs[0] = 0f
    for (iteration in nodes.indices) {
      val current = nodes.indices.filter { !visited[it] }.minByOrNull { costs[it] } ?: break
      if (!costs[current].isFinite() || current == 1) break
      visited[current] = true
      for (next in nodes.indices) {
        if (visited[next] || next == current) continue
        val cost = costs[current] + (nodes[next]-nodes[current]).length()
        if (cost < costs[next] && clear(nodes[current],nodes[next])) {
          costs[next] = cost; parent[next] = current
        }
      }
    }
    if (parent[1] < 0) { reset(); return emptyList() }
    val path = arrayListOf<LeashPoint>()
    var index = 1
    while (index >= 0) { path.add(nodes[index]); index = parent[index] }
    path.reverse()
    previousBends = path.drop(1).dropLast(1)
    return rounded(listOf(start) + path,body,segments)
  }

  private fun rounded(path: List<LeashPoint>, body: LeashBody, budget: Int): List<LeashPoint> {
    fun clear(a: LeashPoint, b: LeashPoint) = body.capsules.none { it.intersects(a,b) }
    val result = arrayListOf(path.first())
    for (i in 1 until path.lastIndex) {
      val corner = path[i]
      val incoming = corner - path[i - 1]
      val outgoing = path[i + 1] - corner
      val before = incoming.length()
      val after = outgoing.length()
      if (before < 0.0001f || after < 0.0001f || dot(incoming,outgoing) / (before * after) > 0.98f) {
        result.add(corner)
        continue
      }
      var trim = minOf(0.12f, before * 0.35f, after * 0.35f)
      var accepted = false
      // A rounded corner cuts inside the old route. Shrink the radius until every
      // rendered chord is clear; retain the original corner if no safe arc fits.
      for (attempt in 0..5) {
        val entry = corner - incoming * (trim / before)
        val exit = corner + outgoing * (trim / after)
        val arc = List(7) { index ->
          val t = index / 6f
          entry * ((1f - t) * (1f - t)) + corner * (2f * t * (1f - t)) + exit * (t * t)
        }
        if (clear(result.last(),entry) && clear(exit,path[i + 1]) &&
          arc.zipWithNext().all { clear(it.first,it.second) }) {
          result.addAll(arc)
          accepted = true
          break
        }
        trim *= 0.5f
      }
      if (!accepted) result.add(corner)
    }
    result.add(path.last())
    // Never drop an unrounded corner or an arc sample to satisfy the mesh budget.
    return subdivide(if (result.size - 1 <= budget) result else path, budget)
  }

  /** Preserve every corner. Uniform arc resampling would cut corners through colliders. */
  private fun subdivide(path: List<LeashPoint>, budget: Int): List<LeashPoint> {
    if (path.size - 1 > budget) return emptyList()
    val edges = path.zipWithNext()
    val counts = IntArray(edges.size) { 1 }
    repeat(budget - edges.size) {
      val longest = edges.indices.maxByOrNull { (edges[it].second-edges[it].first).length()/counts[it] }!!
      counts[longest]++
    }
    val result = arrayListOf(path.first())
    for (i in edges.indices) for (j in 1..counts[i]) {
      result.add(if (j == counts[i]) edges[i].second else
        edges[i].first + (edges[i].second-edges[i].first)*(j.toFloat()/counts[i]))
    }
    return result
  }
}
