package com.usee.animal.ar

import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.roundToInt

/** Immutable result tied to the physical camera pose at exposure, not the latest render pose. */
internal class SemanticFloorMask(
  val width: Int,
  val height: Int,
  private val walkable: BooleanArray,
  private val worldToCamera: FloatArray,
  private val fx: Float,
  private val fy: Float,
  private val cx: Float,
  private val cy: Float,
  private val imageWidth: Int,
  private val imageHeight: Int,
  private val rotation: Int,
  val capturedAtNanos: Long,
) {
  fun allows(x: Float, y: Float, z: Float, now: Long): Boolean {
    if (now - capturedAtNanos !in 0L..MAX_AGE_NANOS) return false
    val m = worldToCamera
    val px = m[0] * x + m[4] * y + m[8] * z + m[12]
    val py = m[1] * x + m[5] * y + m[9] * z + m[13]
    val pz = m[2] * x + m[6] * y + m[10] * z + m[14]
    if (pz >= -0.1f) return false
    val u = (fx * px / -pz + cx) / imageWidth
    val v = (fy * -py / -pz + cy) / imageHeight
    if (u !in 0f..1f || v !in 0f..1f) return false
    val (ru, rv) = rotate(u, v, rotation)
    val ix = (ru * (width - 1)).roundToInt()
    val iy = (rv * (height - 1)).roundToInt()
    // Erode one output pixel at boundaries to avoid stepping onto adjacent furniture/walls.
    if (ix !in 1 until width - 1 || iy !in 1 until height - 1) return false
    for (dy in -1..1) for (dx in -1..1) {
      if (!walkable[(iy + dy) * width + ix + dx]) return false
    }
    return true
  }

  companion object {
    const val MAX_AGE_NANOS = 1_500_000_000L
    // ADE20K output indices are already zero-based; never subtract 1 again.
    private val WALKABLE = setOf(3, 9, 11, 13, 28, 52) // floor, grass, sidewalk, earth, rug, path

    fun rotate(u: Float, v: Float, degrees: Int): Pair<Float, Float> = when (degrees) {
      90 -> 1f - v to u
      180 -> 1f - u to 1f - v
      270 -> v to 1f - u
      else -> u to v
    }

    fun decode(logits: FloatBuffer, classes: Int, width: Int, height: Int): BooleanArray {
      require(classes == 150 && width > 0 && height > 0)
      val pixels = width * height
      require(logits.remaining() == classes * pixels)
      val values = FloatArray(logits.remaining())
      logits.get(values)
      return BooleanArray(pixels) { pixel ->
        var best = 0
        var maximum = Float.NEGATIVE_INFINITY
        for (label in 0 until classes) {
          val value = values[label * pixels + pixel]
          if (value > maximum) { maximum = value; best = label }
        }
        if (best !in WALKABLE || !maximum.isFinite()) false else {
          var total = 0.0
          for (label in 0 until classes) total += exp((values[label * pixels + pixel] - maximum).toDouble())
          1.0 / total >= 0.55
        }
      }
    }
  }
}
