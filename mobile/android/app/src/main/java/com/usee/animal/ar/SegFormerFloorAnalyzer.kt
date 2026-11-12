package com.usee.animal.ar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
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
  private val retry = AnalysisRetryPolicy()
  val failed get() = retry.failed
  private val modelInput = YuvModelInput()
  private var outputValues = FloatArray(0)
  private val cameraRotation = FloatArray(4)
  private val submittedRotation = FloatArray(4)
  private var submittedX = 0f
  private var submittedY = 0f
  private var submittedZ = 0f
  private var lastSubmission = 0L
  private var lastFrameTimestamp = 0L
  private var lastDiagnostic = 0L

  init { initialize() }

  private fun initialize() {
    if (closed || !busy.compareAndSet(false, true)) return
    worker.execute {
      try {
        session?.close()
        session = null
        OrtSession.SessionOptions().use { options ->
          options.setIntraOpNumThreads(2)
          options.setInterOpNumThreads(1)
          session = OrtEnvironment.getEnvironment().createSession(
            assets.open("models/segformer/model.onnx").use { it.readBytes() }, options,
          )
        }
        retry.initialized()
        Log.i(TAG, "SegFormer initialized")
      } catch (error: Exception) {
        retry.fail(SystemClock.elapsedRealtimeNanos())
        Log.e(TAG, "Cannot initialize SegFormer; retry scheduled", error)
      } finally {
        busy.set(false)
      }
    }
  }

  fun submit(frame: Frame, rotation: Int, active: Boolean = true, reducedWorkload: Boolean = false) {
    val diagnosticNow = SystemClock.elapsedRealtimeNanos()
    if (BuildConfig.DEBUG && diagnosticNow - lastDiagnostic > 3_000_000_000L) {
      lastDiagnostic = diagnosticNow
      Log.d(TAG, "ready=${session != null}, busy=${busy.get()}, tracking=${frame.camera.trackingState}, resultAgeMs=${latest?.let { (diagnosticNow - it.capturedAtNanos) / 1_000_000 }}, failed=$failed")
    }
    if (closed) return
    if (failed || session == null) {
      if (retry.canAttempt(diagnosticNow)) initialize()
      return
    }
    if (frame.camera.trackingState != TrackingState.TRACKING) {
      invalidate()
      return
    }
    val now = SystemClock.elapsedRealtimeNanos()
    val pose = frame.camera.pose
    pose.getRotationQuaternion(cameraRotation, 0)
    val moved = kotlin.math.abs(pose.tx() - submittedX) + kotlin.math.abs(pose.ty() - submittedY) +
      kotlin.math.abs(pose.tz() - submittedZ) > 0.05f
    var rotationDot = 0f
    for (i in 0..3) rotationDot += cameraRotation[i] * submittedRotation[i]
    val turned = kotlin.math.abs(rotationDot) < 0.9975f
    val interval = analysisIntervalNanos(active || moved || turned, reducedWorkload)
    if (frame.timestamp == lastFrameTimestamp || now - lastSubmission < interval || !busy.compareAndSet(false, true)) return
    val image = try { frame.acquireCameraImage() } catch (_: NotYetAvailableException) {
      busy.set(false)
      return
    } catch (_: ResourceExhaustedException) {
      busy.set(false)
      return
    }
    lastSubmission = now
    lastFrameTimestamp = frame.timestamp
    submittedX = pose.tx(); submittedY = pose.ty(); submittedZ = pose.tz()
    cameraRotation.copyInto(submittedRotation)
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
          val inferenceStartedAt = SystemClock.elapsedRealtimeNanos()
          session!!.run(mapOf("pixel_values" to tensor)).use { result ->
            val inferenceNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartedAt
            val output = result[0] as OnnxTensor
            val shape = output.info.shape
            require(shape.size == 4 && shape[0] == 1L && shape[1] == 150L) { "Unexpected SegFormer output" }
            val maskWidth = shape[3].toInt()
            val maskHeight = shape[2].toInt()
            val outputSize = 150 * maskWidth * maskHeight
            if (outputValues.size != outputSize) outputValues = FloatArray(outputSize)
            val pixels = SemanticFloorMask.decodeDetails(output.floatBuffer, 150, maskWidth, maskHeight, outputValues)
            retry.recover()
            val elapsed = SystemClock.elapsedRealtimeNanos() - now
            if (BuildConfig.DEBUG) Log.d(TAG, "analysisMs=${elapsed / 1_000_000}, accepted=${now > invalidatedAt}, rotation=$rotation")
            if (elapsed > SemanticFloorMask.MAX_AGE_NANOS) {
              Log.w(TAG, "SegFormer result expired before publication: ${elapsed / 1_000_000}ms")
            }
            if (!closed && now - invalidatedAt > 0L) latest = SemanticFloorMask(
              maskWidth, maskHeight, pixels.walkable, inverse, focal[0], focal[1], center[0], center[1],
              width, height, rotation, now, pixels.labels, pixels.confident,
              analysisNanos = elapsed, inferenceNanos = inferenceNanos,
            )
          }
        }
      } catch (error: Exception) {
        retry.fail(SystemClock.elapsedRealtimeNanos())
        latest = null
        Log.e(TAG, "SegFormer inference failed; retry scheduled", error)
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
    val planes = image.planes
    return modelInput.prepare(image.width, image.height, rotation,
      planes.map { it.buffer.duplicate() },
      planes.map { it.rowStride }.toIntArray(), planes.map { it.pixelStride }.toIntArray())
  }

  companion object {
    private const val TAG = "SegFormerFloor"
    private const val INPUT_SIZE = 256
  }
}
