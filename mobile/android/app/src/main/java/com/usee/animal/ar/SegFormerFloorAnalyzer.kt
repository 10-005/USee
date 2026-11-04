package com.usee.animal.ar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.usee.animal.BuildConfig
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One in-flight CPU inference; camera images are never sent off-device. */
internal class SegFormerFloorAnalyzer(context: Context) : AutoCloseable {
  private val assets = context.applicationContext.assets
  private val worker = Executors.newSingleThreadExecutor()
  private val busy = AtomicBoolean(false)
  @Volatile private var closed = false
  @Volatile private var session: OrtSession? = null
  @Volatile var latest: SemanticFloorMask? = null
    private set
  @Volatile var failed = false
    private set
  private var lastSubmission = 0L
  private var lastFrameTimestamp = 0L
  private var lastDiagnostic = 0L

  init {
    worker.execute {
      try {
        OrtSession.SessionOptions().use { options ->
          options.setIntraOpNumThreads(2)
          options.setInterOpNumThreads(1)
          session = OrtEnvironment.getEnvironment().createSession(
            assets.open("models/segformer/model.onnx").use { it.readBytes() }, options,
          )
          Log.i(TAG, "SegFormer initialized")
        }
      } catch (error: Exception) {
        failed = true
        Log.e(TAG, "Cannot initialize SegFormer", error)
      }
    }
  }

  fun submit(frame: Frame, rotation: Int) {
    val diagnosticNow = SystemClock.elapsedRealtimeNanos()
    if (BuildConfig.DEBUG && diagnosticNow - lastDiagnostic > 3_000_000_000L) {
      lastDiagnostic = diagnosticNow
      Log.d(TAG, "ready=${session != null}, busy=${busy.get()}, tracking=${frame.camera.trackingState}, resultAgeMs=${latest?.let { (diagnosticNow - it.capturedAtNanos) / 1_000_000 }}, failed=$failed")
    }
    if (closed || failed || session == null) return
    if (frame.camera.trackingState != TrackingState.TRACKING) {
      invalidate()
      return
    }
    val now = SystemClock.elapsedRealtimeNanos()
    if (frame.timestamp == lastFrameTimestamp || now - lastSubmission < 250_000_000L || !busy.compareAndSet(false, true)) return
    val image = try { frame.acquireCameraImage() } catch (_: NotYetAvailableException) {
      busy.set(false)
      return
    } catch (_: ResourceExhaustedException) {
      busy.set(false)
      return
    }
    lastSubmission = now
    lastFrameTimestamp = frame.timestamp
    val intrinsics = frame.camera.imageIntrinsics
    val focal = intrinsics.focalLength
    val center = intrinsics.principalPoint
    val inverse = FloatArray(16)
    frame.camera.pose.inverse().toMatrix(inverse, 0)
    worker.execute {
      try {
        val width = image.width
        val height = image.height
        val input = try { prepare(image, rotation) } finally { image.close() }
        val environment = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { tensor ->
          session!!.run(mapOf("pixel_values" to tensor)).use { result ->
            val output = result[0] as OnnxTensor
            val shape = output.info.shape
            require(shape.size == 4 && shape[0] == 1L && shape[1] == 150L) { "Unexpected SegFormer output" }
            val maskWidth = shape[3].toInt()
            val maskHeight = shape[2].toInt()
            val walkable = SemanticFloorMask.decode(output.floatBuffer, 150, maskWidth, maskHeight)
            val elapsed = SystemClock.elapsedRealtimeNanos() - now
            if (BuildConfig.DEBUG) Log.d(TAG, "analysisMs=${elapsed / 1_000_000}, accepted=${now > invalidatedAt}, rotation=$rotation")
            if (elapsed > SemanticFloorMask.MAX_AGE_NANOS) {
              Log.w(TAG, "SegFormer result expired before publication: ${elapsed / 1_000_000}ms")
            }
            if (!closed && now - invalidatedAt > 0L) latest = SemanticFloorMask(
              maskWidth, maskHeight, walkable, inverse, focal[0], focal[1], center[0], center[1],
              width, height, rotation, now,
            )
          }
        }
      } catch (error: Exception) {
        failed = true
        latest = null
        Log.e(TAG, "SegFormer inference failed", error)
      } finally {
        busy.set(false)
      }
    }
  }

  @Volatile private var invalidatedAt = 0L

  fun invalidate() {
    invalidatedAt = SystemClock.elapsedRealtimeNanos()
    latest = null
  }

  override fun close() {
    if (closed) return
    closed = true
    latest = null
    // Queued after any inference, so native resources cannot be closed while in use.
    worker.execute { session?.close(); session = null }
    worker.shutdown()
  }

  private fun prepare(image: Image, rotation: Int): FloatArray {
    val w = image.width
    val h = image.height
    val planes = image.planes
    // Image.Plane.buffer crosses JNI on real camera images. Cache it once per plane,
    // rather than making almost a million JNI calls for every 640x480 frame.
    val buffers = planes.map { it.buffer.duplicate() }
    val offsets = buffers.map { it.position() }
    val rowStrides = planes.map { it.rowStride }
    val pixelStrides = planes.map { it.pixelStride }
    val pixels = IntArray(w * h)
    fun sample(plane: Int, x: Int, y: Int): Int =
      buffers[plane].get(offsets[plane] + y * rowStrides[plane] + x * pixelStrides[plane]).toInt() and 255
    for (y in 0 until h) for (x in 0 until w) {
      val luma = (sample(0, x, y) - 16).coerceAtLeast(0)
      val u = sample(1, x / 2, y / 2) - 128
      val v = sample(2, x / 2, y / 2) - 128
      val r = ((298 * luma + 409 * v + 128) shr 8).coerceIn(0, 255)
      val g = ((298 * luma - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
      val b = ((298 * luma + 516 * u + 128) shr 8).coerceIn(0, 255)
      pixels[y * w + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
    val upright = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
    val scaled = Bitmap.createScaledBitmap(upright, INPUT_SIZE, INPUT_SIZE, true)
    val rgb = IntArray(INPUT_SIZE * INPUT_SIZE)
    scaled.getPixels(rgb, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
    val tensor = FloatArray(rgb.size * 3)
    for (i in rgb.indices) {
      tensor[i] = (((rgb[i] shr 16) and 255) / 255f - 0.485f) / 0.229f
      tensor[rgb.size + i] = (((rgb[i] shr 8) and 255) / 255f - 0.456f) / 0.224f
      tensor[2 * rgb.size + i] = ((rgb[i] and 255) / 255f - 0.406f) / 0.225f
    }
    if (scaled !== upright && scaled !== bitmap) scaled.recycle()
    if (upright !== bitmap) upright.recycle()
    bitmap.recycle()
    return tensor
  }

  companion object {
    private const val TAG = "SegFormerFloor"
    private const val INPUT_SIZE = 256
  }
}
