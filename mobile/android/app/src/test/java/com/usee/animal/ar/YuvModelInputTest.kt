package com.usee.animal.ar

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class YuvModelInputTest {
  @Test fun downsamplingInterpolatesRgbInsteadOfDroppingThreeOfFourPixels() {
    val result = YuvModelInput(1).prepare(2, 2, 0,
      listOf(ByteBuffer.wrap(byteArrayOf(16, 80, 160.toByte(), 235.toByte())),
        ByteBuffer.wrap(byteArrayOf(128.toByte())), ByteBuffer.wrap(byteArrayOf(128.toByte()))),
      intArrayOf(2, 1, 1), intArrayOf(1, 1, 1))
    val gray = listOf(16, 80, 160, 235).map { ((298 * (it - 16) + 128) shr 8).coerceIn(0, 255) }.average().toFloat() / 255f
    assertEquals((gray - 0.485f) / 0.229f, result[0], 0.0001f)
    assertEquals((gray - 0.456f) / 0.224f, result[1], 0.0001f)
    assertEquals((gray - 0.406f) / 0.225f, result[2], 0.0001f)
  }
  @Test fun rotationsRespectPaddedRowsPlaneOffsetsAndInterleavedChroma() {
    val luma = ByteBuffer.wrap(byteArrayOf(99, 16, 80, 0, 0, 160.toByte(), 235.toByte(), 0, 0)).apply { position(1) }
    val uv = ByteBuffer.wrap(byteArrayOf(99, 128.toByte(), 0)).apply { position(1) }
    val buffers = listOf(luma, uv, uv)
    val converter = YuvModelInput(2)
    val original = converter.prepare(2, 2, 0, buffers, intArrayOf(4, 2, 2), intArrayOf(1, 2, 2)).copyOf()
    for ((rotation, indices) in listOf(90 to listOf(2, 0, 3, 1), 180 to listOf(3, 2, 1, 0), 270 to listOf(1, 3, 0, 2))) {
      val actual = converter.prepare(2, 2, rotation, buffers, intArrayOf(4, 2, 2), intArrayOf(1, 2, 2))
      for (channel in 0..2) for (pixel in 0..3) {
        assertEquals(original[channel * 4 + indices[pixel]], actual[channel * 4 + pixel], 0.0001f)
      }
    }
    assertEquals(1, luma.position())
    assertEquals(1, uv.position())
    assertEquals((0f - 0.485f) / 0.229f, original[0], 0.0001f)
    assertEquals((1f - 0.485f) / 0.229f, original[3], 0.0001f)
  }

  @Test fun inputStorageIsReusedAndEveryChannelIsOverwritten() {
    val converter = YuvModelInput(2)
    fun prepare(value: Byte) = converter.prepare(2, 2, 0,
      listOf(ByteBuffer.wrap(ByteArray(4) { value }), ByteBuffer.wrap(byteArrayOf(128.toByte())), ByteBuffer.wrap(byteArrayOf(128.toByte()))),
      intArrayOf(2, 1, 1), intArrayOf(1, 1, 1))
    val first = prepare(16)
    val second = prepare(235.toByte())
    assertSame(first, second)
    assertTrue(second.all { it.isFinite() && it > 2f })
  }
}
