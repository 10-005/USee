package com.usee.animal.ar

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.ImageWriter
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Device-side regression check for native model loading and latency, without camera/UI access. */
class SegFormerDeviceCheck : Instrumentation() {
  override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

  override fun onStart() {
    val report = Bundle()
    try {
      val environment = OrtEnvironment.getEnvironment()
      val started = SystemClock.elapsedRealtime()
      OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(2)
        options.setInterOpNumThreads(1)
        val bytes = targetContext.assets.open("models/segformer/model.onnx").use { it.readBytes() }
        environment.createSession(bytes, options).use { session ->
          val timings = mutableListOf("load=${SystemClock.elapsedRealtime() - started}ms")
          // Exercise the installed app's exact YUV conversion path, including row/pixel strides.
          val analyzerClass = Class.forName("com.usee.animal.ar.SegFormerFloorAnalyzer")
          val analyzer = analyzerClass.getDeclaredConstructor(android.content.Context::class.java).newInstance(targetContext)
          val prepare = analyzerClass.getDeclaredMethod("prepare", Image::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
          try {
            ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).use { reader ->
              ImageWriter.newInstance(reader.surface, 2).use { writer ->
                writer.dequeueInputImage().use { image ->
                  for (plane in image.planes) {
                    val buffer = plane.buffer
                    val position = buffer.position()
                    buffer.put(ByteArray(buffer.remaining()) { 128.toByte() })
                    buffer.position(position)
                  }
                  val before = SystemClock.elapsedRealtime()
                  prepare.invoke(analyzer, image, 90)
                  timings += "YUV-preprocessing=${SystemClock.elapsedRealtime() - before}ms"
                }
              }
            }
          } finally { (analyzer as AutoCloseable).close() }
          for (size in listOf(512, 256, 256)) {
            val before = SystemClock.elapsedRealtime()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(3 * size * size)), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
              session.run(mapOf("pixel_values" to input)).use { result ->
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                check(shape.contentEquals(longArrayOf(1, 150, (size / 4).toLong(), (size / 4).toLong())))
                val companion = Class.forName("com.usee.animal.ar.SemanticFloorMask").getDeclaredField("Companion").get(null)
                val decode = companion.javaClass.getDeclaredMethod("decode", FloatBuffer::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                decode.invoke(companion, output.floatBuffer, 150, size / 4, size / 4)
              }
            }
            timings += "${size}px=${SystemClock.elapsedRealtime() - before}ms"
          }
          report.putString("stream", "PASS: ${timings.joinToString()}")
        }
      }
      finish(Activity.RESULT_OK, report)
    } catch (error: Throwable) {
      report.putString("stream", "FAIL: ${error.stackTraceToString()}")
      finish(Activity.RESULT_CANCELED, report)
    }
  }
}
