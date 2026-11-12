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
  private val labels: IntArray? = null,
  private val confident: BooleanArray? = null,
  val analysisNanos: Long? = null,
  val inferenceNanos: Long? = null,
) {
  fun isFresh(now: Long) = now - capturedAtNanos in 0L..MAX_AGE_NANOS

  fun pathAtWorld(x: Float, y: Float, z: Float, now: Long): Int {
    val m = worldToCamera
    val px = m[0] * x + m[4] * y + m[8] * z + m[12]
    val py = m[1] * x + m[5] * y + m[9] * z + m[13]
    val pz = m[2] * x + m[6] * y + m[10] * z + m[14]
    if (pz >= -0.1f) return -1
    return pathAtImage((fx * px / -pz + cx) / imageWidth, (fy * -py / -pz + cy) / imageHeight, now)
  }

  fun pathAlongWorldDirection(x: Float, y: Float, z: Float, now: Long): Int {
    val m = worldToCamera
    val px = m[0] * x + m[4] * y + m[8] * z
    val py = m[1] * x + m[5] * y + m[9] * z
    val pz = m[2] * x + m[6] * y + m[10] * z
    if (pz >= -0.0001f) return -1
    return pathAtImage((fx * px / -pz + cx) / imageWidth, (fy * -py / -pz + cy) / imageHeight, now)
  }

  /** -1 unknown/stale, 0 non-walking class, 1 path (confidence may still be insufficient). */
  fun pathAtImage(u: Float, v: Float, now: Long): Int {
    if (!isFresh(now) || u !in 0f..1f || v !in 0f..1f) return -1
    val (ru, rv) = rotate(u, v, rotation)
    val index = (rv * (height - 1)).roundToInt() * width + (ru * (width - 1)).roundToInt()
    val label = labels?.get(index)
    return if (label == null) { if (walkable[index]) 1 else 0 }
      else if (label < 0) -1 else if (label in WALKABLE) 1 else if (confident?.get(index) == false) -1 else 0
  }

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
    if (ix !in 1 until width - 1 || iy !in 1 until height - 1) return false
    if (!walkable[iy * width + ix]) return false
    var accepted = 0
    for (dy in -1..1) for (dx in -1..1) {
      if (walkable[(iy + dy) * width + ix + dx]) accepted++
    }
    return accepted >= 6
  }

  fun evidenceAt(x: Float, y: Float, z: Float, now: Long): FloorEvidence {
    if (!isFresh(now)) return FloorEvidence.UNKNOWN
    val m = worldToCamera
    val px = m[0] * x + m[4] * y + m[8] * z + m[12]
    val py = m[1] * x + m[5] * y + m[9] * z + m[13]
    val pz = m[2] * x + m[6] * y + m[10] * z + m[14]
    if (pz >= -0.1f) return FloorEvidence.UNKNOWN
    val u = (fx * px / -pz + cx) / imageWidth
    val v = (fy * -py / -pz + cy) / imageHeight
    if (u !in 0f..1f || v !in 0f..1f) return FloorEvidence.UNKNOWN
    val (ru, rv) = rotate(u, v, rotation)
    val index = (rv * (height - 1)).roundToInt() * width + (ru * (width - 1)).roundToInt()
    val label = labels?.get(index)
    if (label != null && label >= 0 && label !in WALKABLE && confident?.get(index) == true) return FloorEvidence.OBSTACLE
    return if (allows(x, y, z, now)) FloorEvidence.CLEAR else FloorEvidence.UNKNOWN
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
      return decodeDetails(logits, classes, width, height).walkable
    }

    fun decodeDetails(logits: FloatBuffer, classes: Int, width: Int, height: Int): SemanticPixels {
      require(classes == 150 && width > 0 && height > 0)
      val pixels = width * height
      require(logits.remaining() == classes * pixels)
      val values = FloatArray(logits.remaining())
      logits.get(values)
      val labels = IntArray(pixels) { -1 }
      val confident = BooleanArray(pixels)
      val walkable = BooleanArray(pixels) { pixel ->
        var best = 0
        var maximum = Float.NEGATIVE_INFINITY
        for (label in 0 until classes) {
          val value = values[label * pixels + pixel]
          if (value > maximum) { maximum = value; best = label }
        }
        if (maximum.isFinite()) labels[pixel] = best
        if (!maximum.isFinite()) false else {
          var total = 0.0
          for (label in 0 until classes) total += exp((values[label * pixels + pixel] - maximum).toDouble())
          confident[pixel] = 1.0 / total >= 0.55
          best in WALKABLE && confident[pixel]
        }
      }
      return SemanticPixels(walkable, labels, confident)
    }
  }
}

internal data class SemanticPixels(val walkable: BooleanArray, val labels: IntArray, val confident: BooleanArray)
