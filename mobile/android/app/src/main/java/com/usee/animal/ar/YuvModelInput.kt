package com.usee.animal.ar

import java.nio.ByteBuffer

/** Reusable CHW input with bilinear RGB sampling, without full-frame bitmaps. */
internal class YuvModelInput(private val size: Int = 256) {
  private val input = FloatArray(size * size * 3)
  fun prepare(width: Int, height: Int, rotation: Int, buffers: List<ByteBuffer>,
    rowStrides: IntArray, pixelStrides: IntArray): FloatArray {
    require(rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270)
    val uprightWidth = if (rotation == 90 || rotation == 270) height else width
    val uprightHeight = if (rotation == 90 || rotation == 270) width else height
    val count = size * size
    fun sample(plane: Int, x: Int, y: Int) = buffers[plane].get(
      buffers[plane].position() + y * rowStrides[plane] + x * pixelStrides[plane]).toInt() and 255
    fun rgb(ux: Int, uy: Int): Int {
      val x = when (rotation) { 90 -> uy; 180 -> width - 1 - ux; 270 -> width - 1 - uy; else -> ux }
      val y = when (rotation) { 90 -> height - 1 - ux; 180 -> height - 1 - uy; 270 -> ux; else -> uy }
      val luma = (sample(0, x, y) - 16).coerceAtLeast(0)
      val cb = sample(1, x / 2, y / 2) - 128
      val cr = sample(2, x / 2, y / 2) - 128
      val r = ((298 * luma + 409 * cr + 128) shr 8).coerceIn(0, 255)
      val g = ((298 * luma - 100 * cb - 208 * cr + 128) shr 8).coerceIn(0, 255)
      val b = ((298 * luma + 516 * cb + 128) shr 8).coerceIn(0, 255)
      return (r shl 16) or (g shl 8) or b
    }
    for (v in 0 until size) for (u in 0 until size) {
      val px = ((u + 0.5f) * uprightWidth / size - 0.5f).coerceIn(0f, (uprightWidth - 1).toFloat())
      val py = ((v + 0.5f) * uprightHeight / size - 0.5f).coerceIn(0f, (uprightHeight - 1).toFloat())
      val x0 = px.toInt(); val y0 = py.toInt()
      val x1 = (x0 + 1).coerceAtMost(uprightWidth - 1)
      val y1 = (y0 + 1).coerceAtMost(uprightHeight - 1)
      val tx = px - x0; val ty = py - y0
      val a = rgb(x0, y0); val b = rgb(x1, y0)
      val c = rgb(x0, y1); val d = rgb(x1, y1)
      fun channel(shift: Int): Float {
        val top = ((a shr shift) and 255) * (1f - tx) + ((b shr shift) and 255) * tx
        val bottom = ((c shr shift) and 255) * (1f - tx) + ((d shr shift) and 255) * tx
        return (top * (1f - ty) + bottom * ty) / 255f
      }
      val i = v * size + u
      input[i] = (channel(16) - 0.485f) / 0.229f
      input[count + i] = (channel(8) - 0.456f) / 0.224f
      input[2 * count + i] = (channel(0) - 0.406f) / 0.225f
    }
    return input
  }
}
