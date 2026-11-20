package com.usee.animal.ar

import kotlin.math.sqrt

internal object LeashInteraction {
  fun facingViewer(forward: LeashPoint, towardViewer: LeashPoint, previous: Boolean): Boolean {
    val denominator = forward.length() * towardViewer.length()
    if (denominator < 0.0001f) return previous
    val dot = (forward.x * towardViewer.x + forward.y * towardViewer.y + forward.z * towardViewer.z) / denominator
    return dot > if (previous) 0.1f else 0.35f
  }

  fun nearSegment(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float, tolerance: Float): Boolean {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared < 0.0001f) 0f else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    val ex = x - ax - t * dx
    val ey = y - ay - t * dy
    return sqrt(ex * ex + ey * ey) <= tolerance
  }
}
